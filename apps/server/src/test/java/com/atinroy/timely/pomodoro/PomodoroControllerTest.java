package com.atinroy.timely.pomodoro;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against the compose Postgres. Time passes by moving all of the user's sessions into the past.
// Every user it creates has a @cycles.test email and is deleted afterwards.
@SpringBootTest
@AutoConfigureMockMvc
class PomodoroControllerTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private String token;
    private Integer userId;
    private int coding;

    @BeforeEach
    void signUp() throws Exception {
        token = signup();
        userId = JsonPath.read(mvc.perform(asMe(get("/users/me"))).andReturn().getResponse().getContentAsString(), "$.id");
        coding = createActivity("Coding");
    }

    @AfterEach
    void deleteTestUsers() {
        jdbc.update("delete from users where email like '%@cycles.test'");
    }

    @Test
    void startRunsTheFirstFocusSessionWithTheUsersSettings() throws Exception {
        settings(50, 10, 30, 2);

        String body = mvc.perform(asMe(post("/pomodoro/cycles")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "Asia/Kolkata"}
                        """.formatted(coding)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("running"))
                .andExpect(jsonPath("$.completedPomodoros").value(0))
                .andExpect(jsonPath("$.settings.focusMinutes").value(50))
                .andExpect(jsonPath("$.settings.shortBreakMinutes").value(10))
                .andExpect(jsonPath("$.settings.longBreakMinutes").value(30))
                .andExpect(jsonPath("$.settings.pomodorosPerLongBreak").value(2))
                .andExpect(jsonPath("$.currentSession.kind").value("focus"))
                .andExpect(jsonPath("$.currentSession.activityId").value(coding))
                .andExpect(jsonPath("$.currentSession.mode").value("countdown"))
                .andExpect(jsonPath("$.currentSession.plannedSeconds").value(50 * 60))
                .andExpect(jsonPath("$.currentSession.timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Number>read(body, "$.currentSession.pomodoroCycleId").longValue())
                .isEqualTo(JsonPath.<Number>read(body, "$.id").longValue());
    }

    @Test
    void laterSettingsChangesLeaveARunningCycleAlone() throws Exception {
        long cycle = startCycle();

        settings(10, 1, 2, 1);

        mvc.perform(asMe(get("/pomodoro/cycles/{id}", cycle)))
                .andExpect(jsonPath("$.settings.focusMinutes").value(25))
                .andExpect(jsonPath("$.settings.pomodorosPerLongBreak").value(4));
    }

    @Test
    void startRejectsWhenASessionIsRunning() throws Exception {
        mvc.perform(asMe(post("/sessions/stopwatch/start")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC"}
                        """.formatted(coding)))
                .andExpect(status().isCreated());

        mvc.perform(asMe(post("/pomodoro/cycles")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC"}
                        """.formatted(coding)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session-already-running"));
        assertThat(jdbc.queryForObject("select count(*) from pomodoro_cycles where user_id = ?", Integer.class, userId))
                .isZero();
    }

    @Test
    void startRejectsAnArchivedOrUnknownActivity() throws Exception {
        mvc.perform(asMe(post("/pomodoro/cycles")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC"}
                        """.formatted(Integer.MAX_VALUE)))
                .andExpect(status().isNotFound());
        mvc.perform(asMe(patch("/activities/{id}", coding)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"archived": true}
                        """))
                .andExpect(status().isOk());

        mvc.perform(asMe(post("/pomodoro/cycles")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC"}
                        """.formatted(coding)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("activity-archived"));
        mvc.perform(asMe(post("/pomodoro/cycles")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void currentReturnsTheCycleInProgressOrNothing() throws Exception {
        mvc.perform(asMe(get("/pomodoro/cycles/current"))).andExpect(status().isNoContent());

        long cycle = startCycle();

        mvc.perform(asMe(get("/pomodoro/cycles/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cycle))
                .andExpect(jsonPath("$.status").value("running"));
    }

    @Test
    void aFinishedFocusSessionStartsAShortBreakAtItsEnd() throws Exception {
        long cycle = startCycle();
        Instant start = rewind(Duration.ofMinutes(26));

        String body = mvc.perform(asMe(get("/pomodoro/cycles/{id}", cycle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("running"))
                .andExpect(jsonPath("$.completedPomodoros").value(1))
                .andExpect(jsonPath("$.currentSession.kind").value("break"))
                .andExpect(jsonPath("$.currentSession.activityId").value(nullValue()))
                .andExpect(jsonPath("$.currentSession.plannedSeconds").value(5 * 60))
                .andExpect(jsonPath("$.currentSession.pomodoroCycleId").value(cycle))
                .andReturn().getResponse().getContentAsString();

        assertThat(OffsetDateTime.parse(JsonPath.read(body, "$.currentSession.startedAt")).toInstant())
                .isEqualTo(start.plus(Duration.ofMinutes(25)));
    }

    @Test
    void everyNthPomodoroIsFollowedByALongBreak() throws Exception {
        settings(25, 5, 15, 2);
        long cycle = startCycle();
        rewind(Duration.ofMinutes(31));
        nextFocus(cycle, null);

        rewind(Duration.ofMinutes(26));

        mvc.perform(asMe(get("/pomodoro/cycles/{id}", cycle)))
                .andExpect(jsonPath("$.completedPomodoros").value(2))
                .andExpect(jsonPath("$.currentSession.kind").value("break"))
                .andExpect(jsonPath("$.currentSession.plannedSeconds").value(15 * 60));
    }

    @Test
    void aFinishedBreakWaitsForTheNextFocus() throws Exception {
        long cycle = startCycle();
        rewind(Duration.ofMinutes(31));

        mvc.perform(asMe(get("/pomodoro/cycles/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cycle))
                .andExpect(jsonPath("$.status").value("waiting"))
                .andExpect(jsonPath("$.currentSession").value(nullValue()));
        mvc.perform(asMe(get("/sessions/current"))).andExpect(status().isNoContent());
    }

    @Test
    void theNextFocusDefaultsToThePreviousActivity() throws Exception {
        long cycle = startCycle();
        rewind(Duration.ofMinutes(31));

        mvc.perform(asMe(post("/pomodoro/cycles/{id}/focus", cycle)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"timezone": "UTC"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("focus"))
                .andExpect(jsonPath("$.activityId").value(coding))
                .andExpect(jsonPath("$.plannedSeconds").value(25 * 60))
                .andExpect(jsonPath("$.pomodoroCycleId").value(cycle));
        mvc.perform(asMe(get("/pomodoro/cycles/{id}", cycle))).andExpect(jsonPath("$.status").value("running"));
    }

    @Test
    void theNextFocusCanSwitchActivity() throws Exception {
        int guitar = createActivity("Guitar");
        long cycle = startCycle();
        rewind(Duration.ofMinutes(31));

        mvc.perform(asMe(post("/pomodoro/cycles/{id}/focus", cycle)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC"}
                        """.formatted(guitar)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activityId").value(guitar));
    }

    @Test
    void theNextFocusCannotStartWhileASessionRuns() throws Exception {
        long cycle = startCycle();

        mvc.perform(asMe(post("/pomodoro/cycles/{id}/focus", cycle)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"timezone": "UTC"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("cycle-not-waiting"));

        rewind(Duration.ofMinutes(26));

        mvc.perform(asMe(post("/pomodoro/cycles/{id}/focus", cycle)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"timezone": "UTC"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("cycle-not-waiting"));
    }

    @Test
    void aCycleLeftWaitingForALongBreakEnds() throws Exception {
        long cycle = startCycle();
        rewind(Duration.ofMinutes(25 + 5 + 15 + 1));

        mvc.perform(asMe(get("/pomodoro/cycles/current"))).andExpect(status().isNoContent());
        mvc.perform(asMe(get("/pomodoro/cycles/{id}", cycle)))
                .andExpect(jsonPath("$.status").value("ended"))
                .andExpect(jsonPath("$.completedPomodoros").value(1));
        mvc.perform(asMe(post("/pomodoro/cycles/{id}/focus", cycle)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"timezone": "UTC"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("cycle-ended"));
    }

    @Test
    void stopEndsTheCycleAndItsRunningSession() throws Exception {
        long cycle = startCycle();

        mvc.perform(asMe(post("/pomodoro/cycles/{id}/stop", cycle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ended"))
                .andExpect(jsonPath("$.currentSession").value(nullValue()));

        mvc.perform(asMe(get("/sessions/current"))).andExpect(status().isNoContent());
        mvc.perform(asMe(get("/pomodoro/cycles/current"))).andExpect(status().isNoContent());
        mvc.perform(asMe(get("/sessions").param("pomodoroCycleId", String.valueOf(cycle))))
                .andExpect(jsonPath("$.items[0].endedAt").isString())
                .andExpect(jsonPath("$.items[0].completed").value(false));
    }

    @Test
    void stopEndsAWaitingCycle() throws Exception {
        long cycle = startCycle();
        rewind(Duration.ofMinutes(31));

        mvc.perform(asMe(post("/pomodoro/cycles/{id}/stop", cycle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ended"));
        mvc.perform(asMe(get("/pomodoro/cycles/current"))).andExpect(status().isNoContent());
    }

    @Test
    void stoppingAnEndedCycleIsAConflict() throws Exception {
        long cycle = startCycle();
        mvc.perform(asMe(post("/pomodoro/cycles/{id}/stop", cycle))).andExpect(status().isOk());

        mvc.perform(asMe(post("/pomodoro/cycles/{id}/stop", cycle)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("cycle-ended"));
    }

    @Test
    void stoppingTheCyclesSessionEndsTheCycle() throws Exception {
        long cycle = startCycle();

        mvc.perform(asMe(post("/sessions/current/stop"))).andExpect(status().isOk());

        mvc.perform(asMe(get("/pomodoro/cycles/{id}", cycle))).andExpect(jsonPath("$.status").value("ended"));
    }

    @Test
    void startingANewCycleEndsAWaitingOne() throws Exception {
        long old = startCycle();
        rewind(Duration.ofMinutes(31));

        long cycle = startCycle();

        mvc.perform(asMe(get("/pomodoro/cycles/{id}", old))).andExpect(jsonPath("$.status").value("ended"));
        mvc.perform(asMe(get("/pomodoro/cycles/current"))).andExpect(jsonPath("$.id").value(cycle));
    }

    @Test
    void aCyclesSessionsAreListedByCycle() throws Exception {
        long cycle = startCycle();
        rewind(Duration.ofMinutes(26));

        mvc.perform(asMe(get("/sessions").param("pomodoroCycleId", String.valueOf(cycle))))
                .andExpect(jsonPath("$.items[*].kind").value(contains("break", "focus")));
    }

    @Test
    void deletingACyclesLastSessionDeletesTheCycle() throws Exception {
        long cycle = startCycle();
        long session = JsonPath.<Number>read(mvc.perform(asMe(get("/sessions/current")))
                .andReturn().getResponse().getContentAsString(), "$.id").longValue();

        mvc.perform(asMe(delete("/sessions/{id}", session))).andExpect(status().isNoContent());

        mvc.perform(asMe(get("/pomodoro/cycles/{id}", cycle)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not-found"));
    }

    @Test
    void usersCannotSeeOrTouchEachOthersCycles() throws Exception {
        long mine = startCycle();
        String otherToken = signup();
        MockHttpServletRequestBuilder[] requests = {
                get("/pomodoro/cycles/{id}", mine),
                post("/pomodoro/cycles/{id}/focus", mine).contentType(MediaType.APPLICATION_JSON).content("""
                        {"timezone": "UTC"}
                        """),
                post("/pomodoro/cycles/{id}/stop", mine),
        };

        for (MockHttpServletRequestBuilder request : requests) {
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/pomodoro/cycles/current").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNoContent());
        mvc.perform(asMe(get("/pomodoro/cycles/{id}", mine))).andExpect(jsonPath("$.status").value("running"));
    }

    @Test
    void endpointsNeedAToken() throws Exception {
        mvc.perform(post("/pomodoro/cycles").contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC"}
                        """.formatted(coding)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/pomodoro/cycles/current")).andExpect(status().isUnauthorized());
        mvc.perform(get("/pomodoro/cycles/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/pomodoro/cycles/1/stop")).andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder asMe(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private long startCycle() throws Exception {
        String body = mvc.perform(asMe(post("/pomodoro/cycles")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC"}
                        """.formatted(coding)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }

    private void nextFocus(long cycle, Integer activityId) throws Exception {
        String activity = activityId == null ? "" : "\"activityId\": %d,".formatted(activityId);
        mvc.perform(asMe(post("/pomodoro/cycles/{id}/focus", cycle)).contentType(MediaType.APPLICATION_JSON).content("""
                        {%s "timezone": "UTC"}
                        """.formatted(activity)))
                .andExpect(status().isCreated());
    }

    private void settings(int focus, int shortBreak, int longBreak, int perLongBreak) throws Exception {
        mvc.perform(asMe(patch("/users/me")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"pomodoroSettings": {"focusMinutes": %d, "shortBreakMinutes": %d,
                                              "longBreakMinutes": %d, "pomodorosPerLongBreak": %d}}
                        """.formatted(focus, shortBreak, longBreak, perLongBreak)))
                .andExpect(status().isOk());
    }

    // Moves every session back, oldest first so no two overlap on the way. Returns the running session's new start.
    private Instant rewind(Duration ago) {
        List<Long> ids = jdbc.queryForList(
                "select id from sessions where user_id = ? order by started_at", Long.class, userId);
        Instant running = null;
        for (Long id : ids) {
            OffsetDateTime start = jdbc.queryForObject("""
                            update sessions set started_at = started_at - ?::interval, ended_at = ended_at - ?::interval
                            where id = ? returning case when ended_at is null then started_at end
                            """,
                    OffsetDateTime.class, ago.toSeconds() + " seconds", ago.toSeconds() + " seconds", id);
            if (start != null) {
                running = start.toInstant();
            }
        }
        return running;
    }

    private int createActivity(String name) throws Exception {
        String body = mvc.perform(asMe(post("/activities")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "%s", "color": "#4f46e5"}
                        """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String signup() throws Exception {
        String body = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "atin", "email": "%s@cycles.test", "password": "correct horse battery"}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
