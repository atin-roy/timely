package com.atinroy.timely.session;

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
import java.util.ArrayList;
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

// Runs against the compose Postgres. Time passes by moving a running session's start into the past.
// Every user it creates has a @sessions.test email and is deleted afterwards.
@SpringBootTest
@AutoConfigureMockMvc
class SessionControllerTest {
    private static final Instant NINE = Instant.parse("2026-03-10T09:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private String token;
    private int coding;

    @BeforeEach
    void signUp() throws Exception {
        token = signup();
        coding = createActivity("Coding");
    }

    @AfterEach
    void deleteTestUsers() {
        jdbc.update("delete from users where email like '%@sessions.test'");
    }

    @Test
    void stopwatchStartsARunningFocusSession() throws Exception {
        mvc.perform(asMe(post("/sessions/stopwatch/start")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "Asia/Kolkata"}
                        """.formatted(coding)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.kind").value("focus"))
                .andExpect(jsonPath("$.activityId").value(coding))
                .andExpect(jsonPath("$.source").value("timed"))
                .andExpect(jsonPath("$.mode").value("stopwatch"))
                .andExpect(jsonPath("$.plannedSeconds").value(nullValue()))
                .andExpect(jsonPath("$.pomodoroCycleId").value(nullValue()))
                .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.endedAt").value(nullValue()))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.needsTrim").value(false));
    }

    @Test
    void countdownStartsWithItsPlannedLength() throws Exception {
        mvc.perform(asMe(post("/sessions/countdown/start")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC", "plannedSeconds": 1500}
                        """.formatted(coding)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("countdown"))
                .andExpect(jsonPath("$.plannedSeconds").value(1500))
                .andExpect(jsonPath("$.endedAt").value(nullValue()));
    }

    @Test
    void breakIsACountdownOnlyWhenGivenALength() throws Exception {
        mvc.perform(asMe(post("/sessions/break/start")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"timezone": "UTC"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("break"))
                .andExpect(jsonPath("$.activityId").value(nullValue()))
                .andExpect(jsonPath("$.mode").value("stopwatch"));
        mvc.perform(asMe(post("/sessions/current/stop"))).andExpect(status().isOk());

        mvc.perform(asMe(post("/sessions/break/start")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"timezone": "UTC", "plannedSeconds": 300}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("break"))
                .andExpect(jsonPath("$.mode").value("countdown"))
                .andExpect(jsonPath("$.plannedSeconds").value(300));
    }

    @Test
    void onlyOneSessionRunsAtATime() throws Exception {
        startStopwatch();

        mvc.perform(asMe(post("/sessions/stopwatch/start")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC"}
                        """.formatted(coding)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session-already-running"));
        mvc.perform(asMe(post("/sessions/countdown/start")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC", "plannedSeconds": 60}
                        """.formatted(coding)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session-already-running"));
        mvc.perform(asMe(post("/sessions/break/start")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"timezone": "UTC"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session-already-running"));
    }

    @Test
    void startRejectsAnArchivedOrUnknownActivity() throws Exception {
        mvc.perform(asMe(patch("/activities/{id}", coding)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"archived": true}
                        """))
                .andExpect(status().isOk());

        mvc.perform(asMe(post("/sessions/stopwatch/start")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC"}
                        """.formatted(coding)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("activity-archived"));
        mvc.perform(asMe(post("/sessions/stopwatch/start")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC"}
                        """.formatted(Integer.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not-found"));
    }

    @Test
    void startRejectsBadRequests() throws Exception {
        for (String timezone : new String[]{"Mars/Olympus", "+05:30"}) {
            mvc.perform(asMe(post("/sessions/stopwatch/start")).contentType(MediaType.APPLICATION_JSON).content("""
                            {"activityId": %d, "timezone": "%s"}
                            """.formatted(coding, timezone)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("bad-request"));
        }
        mvc.perform(asMe(post("/sessions/countdown/start")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC", "plannedSeconds": 0}
                        """.formatted(coding)))
                .andExpect(status().isBadRequest());
        mvc.perform(asMe(post("/sessions/countdown/start")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC", "plannedSeconds": 86401}
                        """.formatted(coding)))
                .andExpect(status().isBadRequest());
        mvc.perform(asMe(post("/sessions/stopwatch/start")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(asMe(get("/sessions/current"))).andExpect(status().isNoContent());
    }

    @Test
    void currentReturnsTheRunningSessionOrNothing() throws Exception {
        mvc.perform(asMe(get("/sessions/current"))).andExpect(status().isNoContent());

        long id = startStopwatch();

        mvc.perform(asMe(get("/sessions/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void aCountdownPastItsPlannedEndIsEndedAtThatEnd() throws Exception {
        long id = startCountdown(60);
        Instant start = rewind(id, Duration.ofMinutes(2));

        mvc.perform(asMe(get("/sessions/current"))).andExpect(status().isNoContent());

        String body = mvc.perform(asMe(get("/sessions/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(instant(body, "$.endedAt")).isEqualTo(start.plusSeconds(60));
    }

    @Test
    void aFinishedCountdownDoesNotBlockStartingTheNextSession() throws Exception {
        long id = startCountdown(60);
        rewind(id, Duration.ofMinutes(2));

        startStopwatch();
    }

    @Test
    void stopEndsTheRunningSession() throws Exception {
        long id = startStopwatch();
        rewind(id, Duration.ofMinutes(10));

        String body = mvc.perform(asMe(post("/sessions/current/stop")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.endedAt").isString())
                .andExpect(jsonPath("$.completed").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(Duration.between(instant(body, "$.startedAt"), instant(body, "$.endedAt")))
                .isBetween(Duration.ofMinutes(10), Duration.ofMinutes(11));
        mvc.perform(asMe(get("/sessions/current"))).andExpect(status().isNoContent());
    }

    @Test
    void aCountdownStoppedEarlyIsIncomplete() throws Exception {
        long id = startCountdown(600);
        rewind(id, Duration.ofMinutes(5));

        mvc.perform(asMe(post("/sessions/current/stop")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void stopWithNothingRunningIsNotFound() throws Exception {
        mvc.perform(asMe(post("/sessions/current/stop")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("no-running-session"));

        long id = startCountdown(60);
        rewind(id, Duration.ofMinutes(2));

        mvc.perform(asMe(post("/sessions/current/stop")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("no-running-session"));
    }

    @Test
    void aStopwatchLeftRunningTooLongNeedsTrimButKeepsRunning() throws Exception {
        long id = startStopwatch();
        rewind(id, Duration.ofHours(13));

        mvc.perform(asMe(get("/sessions/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsTrim").value(true))
                .andExpect(jsonPath("$.endedAt").value(nullValue()));
        mvc.perform(asMe(post("/sessions/current/stop")))
                .andExpect(jsonPath("$.needsTrim").value(false));
    }

    @Test
    void logAddsASessionAfterTheFact() throws Exception {
        String body = mvc.perform(asMe(post("/sessions")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind": "focus", "activityId": %d, "timezone": "Asia/Kolkata",
                         "startedAt": "2026-03-10T09:00:00Z", "endedAt": "2026-03-10T15:30:00+05:30"}
                        """.formatted(coding)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("focus"))
                .andExpect(jsonPath("$.activityId").value(coding))
                .andExpect(jsonPath("$.source").value("logged"))
                .andExpect(jsonPath("$.mode").value(nullValue()))
                .andExpect(jsonPath("$.plannedSeconds").value(nullValue()))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.needsTrim").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(instant(body, "$.startedAt")).isEqualTo(NINE);
        assertThat(instant(body, "$.endedAt")).isEqualTo(NINE.plus(Duration.ofHours(1)));
    }

    @Test
    void logRejectsAMismatchedKindAndActivity() throws Exception {
        mvc.perform(asMe(post("/sessions")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind": "focus", "timezone": "UTC", "startedAt": "%s", "endedAt": "%s"}
                        """.formatted(NINE, NINE.plusSeconds(60))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad-request"));
        mvc.perform(asMe(post("/sessions")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind": "break", "activityId": %d, "timezone": "UTC", "startedAt": "%s", "endedAt": "%s"}
                        """.formatted(coding, NINE, NINE.plusSeconds(60))))
                .andExpect(status().isBadRequest());
        log("break", null, NINE, NINE.plusSeconds(60));
    }

    @Test
    void logRejectsAnEndNotAfterTheStart() throws Exception {
        mvc.perform(asMe(post("/sessions")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind": "focus", "activityId": %d, "timezone": "UTC", "startedAt": "%s", "endedAt": "%s"}
                        """.formatted(coding, NINE, NINE)))
                .andExpect(status().isBadRequest());
        mvc.perform(asMe(post("/sessions")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind": "focus", "activityId": %d, "timezone": "UTC", "startedAt": "%s"}
                        """.formatted(coding, NINE)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logRejectsAnOverlapButAllowsTouchingSessions() throws Exception {
        log("focus", coding, NINE, NINE.plus(Duration.ofHours(1)));

        mvc.perform(asMe(post("/sessions")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind": "break", "timezone": "UTC", "startedAt": "%s", "endedAt": "%s"}
                        """.formatted(NINE.plus(Duration.ofMinutes(59)), NINE.plus(Duration.ofHours(2)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session-overlap"));
        mvc.perform(asMe(post("/sessions")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind": "break", "timezone": "UTC", "startedAt": "%s", "endedAt": "%s"}
                        """.formatted(NINE.minus(Duration.ofMinutes(10)), NINE.plus(Duration.ofHours(2)))))
                .andExpect(status().isConflict());

        log("break", null, NINE.plus(Duration.ofHours(1)), NINE.plus(Duration.ofHours(2)));
        log("break", null, NINE.minus(Duration.ofHours(1)), NINE);
    }

    @Test
    void aRunningSessionBlocksLoggingAnythingAfterItsStart() throws Exception {
        long id = startStopwatch();
        Instant start = rewind(id, Duration.ofHours(1));

        mvc.perform(asMe(post("/sessions")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind": "break", "timezone": "UTC", "startedAt": "%s", "endedAt": "%s"}
                        """.formatted(start.plus(Duration.ofHours(2)), start.plus(Duration.ofHours(3)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session-overlap"));

        log("break", null, start.minus(Duration.ofHours(1)), start);
    }

    @Test
    void getReturnsTheSession() throws Exception {
        long id = log("focus", coding, NINE, NINE.plusSeconds(60));

        mvc.perform(asMe(get("/sessions/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.activityId").value(coding));
        mvc.perform(asMe(get("/sessions/{id}", Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not-found"));
    }

    @Test
    void updateCorrectsTheStartAndEnd() throws Exception {
        long id = log("focus", coding, NINE, NINE.plus(Duration.ofHours(1)));

        String body = mvc.perform(asMe(patch("/sessions/{id}", id)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"startedAt": "%s"}
                        """.formatted(NINE.plus(Duration.ofMinutes(15)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("logged"))
                .andReturn().getResponse().getContentAsString();
        assertThat(instant(body, "$.startedAt")).isEqualTo(NINE.plus(Duration.ofMinutes(15)));
        assertThat(instant(body, "$.endedAt")).isEqualTo(NINE.plus(Duration.ofHours(1)));

        body = mvc.perform(asMe(patch("/sessions/{id}", id)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"endedAt": "%s"}
                        """.formatted(NINE.plus(Duration.ofHours(2)))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(instant(body, "$.startedAt")).isEqualTo(NINE.plus(Duration.ofMinutes(15)));
        assertThat(instant(body, "$.endedAt")).isEqualTo(NINE.plus(Duration.ofHours(2)));
    }

    @Test
    void updateRejectsAnEndNotAfterTheStart() throws Exception {
        long id = log("focus", coding, NINE, NINE.plus(Duration.ofHours(1)));

        mvc.perform(asMe(patch("/sessions/{id}", id)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"startedAt": "%s"}
                        """.formatted(NINE.plus(Duration.ofHours(1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad-request"));
    }

    @Test
    void updateRejectsAnOverlapWithAnotherSessionButNotItself() throws Exception {
        log("focus", coding, NINE, NINE.plus(Duration.ofHours(1)));
        long later = log("focus", coding, NINE.plus(Duration.ofHours(2)), NINE.plus(Duration.ofHours(3)));

        mvc.perform(asMe(patch("/sessions/{id}", later)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"startedAt": "%s"}
                        """.formatted(NINE.plus(Duration.ofMinutes(30)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session-overlap"));
        mvc.perform(asMe(patch("/sessions/{id}", later)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"startedAt": "%s", "endedAt": "%s"}
                        """.formatted(NINE.plus(Duration.ofHours(1)), NINE.plus(Duration.ofMinutes(150)))))
                .andExpect(status().isOk());
    }

    @Test
    void updateKeepsATimedSessionTimedAndRecomputesCompleted() throws Exception {
        long id = startCountdown(60);
        Instant start = rewind(id, Duration.ofMinutes(5));
        mvc.perform(asMe(get("/sessions/{id}", id))).andExpect(jsonPath("$.completed").value(true));

        mvc.perform(asMe(patch("/sessions/{id}", id)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"endedAt": "%s"}
                        """.formatted(start.plusSeconds(30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("timed"))
                .andExpect(jsonPath("$.mode").value("countdown"))
                .andExpect(jsonPath("$.plannedSeconds").value(60))
                .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void aRunningSessionsStartCanBeCorrectedButNotItsEnd() throws Exception {
        long id = startStopwatch();
        Instant start = rewind(id, Duration.ofMinutes(10));

        mvc.perform(asMe(patch("/sessions/{id}", id)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"endedAt": "%s"}
                        """.formatted(start.plusSeconds(60))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session-running"));

        String body = mvc.perform(asMe(patch("/sessions/{id}", id)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"startedAt": "%s"}
                        """.formatted(start.minus(Duration.ofMinutes(20)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endedAt").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        assertThat(instant(body, "$.startedAt")).isEqualTo(start.minus(Duration.ofMinutes(20)));
    }

    @Test
    void updateMovesAFocusSessionToAnotherActivity() throws Exception {
        int guitar = createActivity("Guitar");
        long focus = log("focus", coding, NINE, NINE.plus(Duration.ofHours(1)));
        long rest = log("break", null, NINE.plus(Duration.ofHours(1)), NINE.plus(Duration.ofHours(2)));

        mvc.perform(asMe(patch("/sessions/{id}", focus)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d}
                        """.formatted(guitar)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityId").value(guitar));
        mvc.perform(asMe(patch("/sessions/{id}", rest)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d}
                        """.formatted(guitar)))
                .andExpect(status().isBadRequest());

        mvc.perform(asMe(patch("/activities/{id}", coding)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"archived": true}
                        """))
                .andExpect(status().isOk());
        mvc.perform(asMe(patch("/sessions/{id}", focus)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d}
                        """.formatted(coding)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("activity-archived"));
    }

    @Test
    void deleteRemovesTheSession() throws Exception {
        long id = log("focus", coding, NINE, NINE.plus(Duration.ofHours(1)));

        mvc.perform(asMe(delete("/sessions/{id}", id))).andExpect(status().isNoContent());

        mvc.perform(asMe(get("/sessions/{id}", id))).andExpect(status().isNotFound());
        mvc.perform(asMe(delete("/sessions/{id}", id))).andExpect(status().isNotFound());
    }

    @Test
    void deletingTheRunningSessionStopsTheTimer() throws Exception {
        long id = startStopwatch();

        mvc.perform(asMe(delete("/sessions/{id}", id))).andExpect(status().isNoContent());

        mvc.perform(asMe(get("/sessions/current"))).andExpect(status().isNoContent());
        startStopwatch();
    }

    @Test
    void listPagesThroughSessionsNewestFirst() throws Exception {
        List<Long> logged = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Instant start = NINE.plus(Duration.ofHours(i));
            logged.add(log("focus", coding, start, start.plus(Duration.ofMinutes(30))));
        }

        List<Long> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            MockHttpServletRequestBuilder request = asMe(get("/sessions")).param("limit", "2");
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            String body = mvc.perform(request)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            List<Number> ids = JsonPath.read(body, "$.items[*].id");
            ids.forEach(n -> seen.add(n.longValue()));
            cursor = JsonPath.read(body, "$.nextCursor");
            pages++;
        } while (cursor != null);

        assertThat(pages).isEqualTo(3);
        assertThat(seen).containsExactlyElementsOf(logged.reversed());
    }

    @Test
    void listFiltersByKindActivityAndStartTime() throws Exception {
        int guitar = createActivity("Guitar");
        long first = log("focus", coding, NINE, NINE.plus(Duration.ofHours(1)));
        long rest = log("break", null, NINE.plus(Duration.ofHours(1)), NINE.plus(Duration.ofHours(2)));
        long last = log("focus", guitar, NINE.plus(Duration.ofHours(2)), NINE.plus(Duration.ofHours(3)));

        mvc.perform(asMe(get("/sessions").param("kind", "break")))
                .andExpect(jsonPath("$.items[*].id").value(contains((int) rest)));
        mvc.perform(asMe(get("/sessions").param("activityId", String.valueOf(guitar))))
                .andExpect(jsonPath("$.items[*].id").value(contains((int) last)));
        mvc.perform(asMe(get("/sessions").param("kind", "focus")))
                .andExpect(jsonPath("$.items[*].id").value(contains((int) last, (int) first)));
        mvc.perform(asMe(get("/sessions")
                        .param("from", NINE.plus(Duration.ofHours(1)).toString())
                        .param("to", NINE.plus(Duration.ofHours(2)).toString())))
                .andExpect(jsonPath("$.items[*].id").value(contains((int) rest)))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void listSettlesAFinishedCountdownFirst() throws Exception {
        long id = startCountdown(60);
        rewind(id, Duration.ofMinutes(2));

        mvc.perform(asMe(get("/sessions")))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].endedAt").isString())
                .andExpect(jsonPath("$.items[0].completed").value(true));
    }

    @Test
    void listRejectsABadLimitOrCursor() throws Exception {
        mvc.perform(asMe(get("/sessions").param("limit", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad-request"))
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
        mvc.perform(asMe(get("/sessions").param("limit", "201"))).andExpect(status().isBadRequest());
        mvc.perform(asMe(get("/sessions").param("cursor", "not a cursor"))).andExpect(status().isBadRequest());
        mvc.perform(asMe(get("/sessions").param("cursor", "bm90LWEtdGltZXwx"))).andExpect(status().isBadRequest());
    }

    @Test
    void usersCannotSeeOrTouchEachOthersSessions() throws Exception {
        long mine = log("focus", coding, NINE, NINE.plus(Duration.ofHours(1)));
        String otherToken = signup();
        MockHttpServletRequestBuilder[] requests = {
                get("/sessions/{id}", mine),
                patch("/sessions/{id}", mine).contentType(MediaType.APPLICATION_JSON).content("""
                        {"startedAt": "%s"}
                        """.formatted(NINE.minusSeconds(60))),
                delete("/sessions/{id}", mine),
        };

        for (MockHttpServletRequestBuilder request : requests) {
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/sessions").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(post("/sessions/stopwatch/start").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"activityId": %d, "timezone": "UTC"}
                                """.formatted(coding)))
                .andExpect(status().isNotFound());
        mvc.perform(asMe(get("/sessions/{id}", mine))).andExpect(status().isOk());
    }

    @Test
    void endpointsNeedAToken() throws Exception {
        mvc.perform(get("/sessions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/sessions/current")).andExpect(status().isUnauthorized());
        mvc.perform(post("/sessions/current/stop")).andExpect(status().isUnauthorized());
        mvc.perform(post("/sessions/stopwatch/start").contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC"}
                        """.formatted(coding)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/sessions/1")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/sessions/1")).andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder asMe(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private long startStopwatch() throws Exception {
        return id(mvc.perform(asMe(post("/sessions/stopwatch/start")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC"}
                        """.formatted(coding)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private long startCountdown(int plannedSeconds) throws Exception {
        return id(mvc.perform(asMe(post("/sessions/countdown/start")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC", "plannedSeconds": %d}
                        """.formatted(coding, plannedSeconds)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private long log(String kind, Integer activityId, Instant startedAt, Instant endedAt) throws Exception {
        String activity = activityId == null ? "" : "\"activityId\": %d,".formatted(activityId);
        return id(mvc.perform(asMe(post("/sessions")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind": "%s", %s "timezone": "UTC", "startedAt": "%s", "endedAt": "%s"}
                        """.formatted(kind, activity, startedAt, endedAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    // Moves the session's start back, as if it had been started that long ago. Returns the new start.
    private Instant rewind(long sessionId, Duration ago) {
        return jdbc.queryForObject(
                "update sessions set started_at = started_at - ?::interval where id = ? returning started_at",
                OffsetDateTime.class, ago.toSeconds() + " seconds", sessionId).toInstant();
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
                        {"username": "atin", "email": "%s@sessions.test", "password": "correct horse battery"}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static long id(String body) {
        return JsonPath.<Number>read(body, "$.id").longValue();
    }

    private static Instant instant(String body, String path) {
        return OffsetDateTime.parse(JsonPath.<String>read(body, path)).toInstant();
    }
}
