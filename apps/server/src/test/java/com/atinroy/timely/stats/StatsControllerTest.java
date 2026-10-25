package com.atinroy.timely.stats;

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

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against the compose Postgres. Every user it creates has a @stats.test email and is deleted afterwards.
@SpringBootTest
@AutoConfigureMockMvc
class StatsControllerTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private String token;
    private int coding;
    private int guitar;

    @BeforeEach
    void signUp() throws Exception {
        token = signup();
        coding = createActivity("Coding");
        guitar = createActivity("Guitar");
    }

    @AfterEach
    void deleteTestUsers() {
        jdbc.update("delete from users where email like '%@stats.test'");
    }

    @Test
    void sumsFocusPerActivityAndKeepsBreaksApart() throws Exception {
        log("focus", coding, "UTC", "2026-03-10T09:00:00Z", "2026-03-10T10:00:00Z");
        log("break", null, "UTC", "2026-03-10T10:00:00Z", "2026-03-10T10:15:00Z");
        log("focus", coding, "UTC", "2026-03-10T11:00:00Z", "2026-03-10T11:30:00Z");
        log("focus", guitar, "UTC", "2026-03-10T12:00:00Z", "2026-03-10T12:20:00Z");
        log("break", null, "UTC", "2026-03-10T12:20:00Z", "2026-03-10T12:25:00Z");

        mvc.perform(stats("2026-03-10", "2026-03-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].day").value("2026-03-10"))
                .andExpect(jsonPath("$[0].activities.length()").value(2))
                .andExpect(jsonPath("$[0].activities[0].activityId").value(coding))
                .andExpect(jsonPath("$[0].activities[0].totalSeconds").value(5400))
                .andExpect(jsonPath("$[0].activities[0].totalSessions").value(2))
                .andExpect(jsonPath("$[0].activities[1].activityId").value(guitar))
                .andExpect(jsonPath("$[0].activities[1].totalSeconds").value(1200))
                .andExpect(jsonPath("$[0].activities[1].totalSessions").value(1))
                .andExpect(jsonPath("$[0].breakSeconds").value(1200));
    }

    @Test
    void leavesOutDaysWithNoSessionsAndListsTheRestOldestFirst() throws Exception {
        log("focus", coding, "UTC", "2026-03-12T09:00:00Z", "2026-03-12T10:00:00Z");
        log("focus", coding, "UTC", "2026-03-10T09:00:00Z", "2026-03-10T10:00:00Z");
        log("focus", coding, "UTC", "2026-03-14T09:00:00Z", "2026-03-14T10:00:00Z");

        mvc.perform(stats("2026-03-10", "2026-03-13"))
                .andExpect(jsonPath("$[*].day").value(contains("2026-03-10", "2026-03-12")));
        mvc.perform(stats("2026-03-11", "2026-03-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aDayWithOnlyBreaksHasNoActivities() throws Exception {
        log("break", null, "UTC", "2026-03-10T09:00:00Z", "2026-03-10T09:10:00Z");

        mvc.perform(stats("2026-03-10", "2026-03-10"))
                .andExpect(jsonPath("$[0].activities.length()").value(0))
                .andExpect(jsonPath("$[0].breakSeconds").value(600));
    }

    @Test
    void aSessionAcrossMidnightSplitsItsTimeButCountsOnceOnItsFirstDay() throws Exception {
        log("focus", coding, "UTC", "2026-03-10T23:00:00Z", "2026-03-11T01:30:00Z");

        mvc.perform(stats("2026-03-10", "2026-03-11"))
                .andExpect(jsonPath("$[*].day").value(contains("2026-03-10", "2026-03-11")))
                .andExpect(jsonPath("$[0].activities[0].totalSeconds").value(3600))
                .andExpect(jsonPath("$[0].activities[0].totalSessions").value(1))
                .andExpect(jsonPath("$[1].activities[0].totalSeconds").value(5400))
                .andExpect(jsonPath("$[1].activities[0].totalSessions").value(0));
    }

    @Test
    void daysFollowTheTimezoneTheSessionWasStartedIn() throws Exception {
        // 20:00 UTC is 01:30 the next day in Kolkata.
        log("focus", coding, "Asia/Kolkata", "2026-03-10T20:00:00Z", "2026-03-10T20:30:00Z");
        log("focus", guitar, "America/New_York", "2026-03-11T02:00:00Z", "2026-03-11T02:30:00Z");

        mvc.perform(stats("2026-03-09", "2026-03-12"))
                .andExpect(jsonPath("$[*].day").value(contains("2026-03-10", "2026-03-11")))
                .andExpect(jsonPath("$[0].activities[0].activityId").value(guitar))
                .andExpect(jsonPath("$[1].activities[0].activityId").value(coding));
    }

    @Test
    void aRunningSessionCountsUpToNow() throws Exception {
        long id = startStopwatch();
        jdbc.update("update sessions set started_at = now() - interval '1 hour' where id = ?", id);

        assertThat(focusSecondsAroundToday()).isBetween(3600L, 3660L);
    }

    @Test
    void aCountdownPastItsEndCountsOnlyItsPlannedLength() throws Exception {
        long id = id(mvc.perform(asMe(post("/sessions/countdown/start")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC", "plannedSeconds": 600}
                        """.formatted(coding)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        jdbc.update("update sessions set started_at = now() - interval '2 hours' where id = ?", id);

        assertThat(focusSecondsAroundToday()).isEqualTo(600);
    }

    @Test
    void editsAndDeletesShowUpImmediately() throws Exception {
        long id = log("focus", coding, "UTC", "2026-03-10T09:00:00Z", "2026-03-10T10:00:00Z");

        mvc.perform(asMe(patch("/sessions/{id}", id)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"endedAt": "2026-03-10T09:30:00Z", "activityId": %d}
                        """.formatted(guitar)))
                .andExpect(status().isOk());
        mvc.perform(stats("2026-03-10", "2026-03-10"))
                .andExpect(jsonPath("$[0].activities[0].activityId").value(guitar))
                .andExpect(jsonPath("$[0].activities[0].totalSeconds").value(1800));

        mvc.perform(asMe(delete("/sessions/{id}", id))).andExpect(status().isNoContent());
        mvc.perform(stats("2026-03-10", "2026-03-10")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void archivedActivitiesStillCount() throws Exception {
        log("focus", coding, "UTC", "2026-03-10T09:00:00Z", "2026-03-10T10:00:00Z");
        mvc.perform(asMe(patch("/activities/{id}", coding)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"archived": true}
                        """))
                .andExpect(status().isOk());

        mvc.perform(stats("2026-03-10", "2026-03-10"))
                .andExpect(jsonPath("$[0].activities[0].activityId").value(coding))
                .andExpect(jsonPath("$[0].activities[0].totalSeconds").value(3600));
    }

    @Test
    void rejectsABackwardsOrTooLongRange() throws Exception {
        mvc.perform(stats("2026-03-10", "2026-03-09"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad-request"));
        mvc.perform(stats("2026-01-01", "2027-01-03")).andExpect(status().isBadRequest());
        mvc.perform(stats("2026-01-01", "2027-01-02")).andExpect(status().isOk());
        mvc.perform(asMe(get("/stats/daily").param("from", "2026-03-10"))).andExpect(status().isBadRequest());
        mvc.perform(stats("2026-03-10", "yesterday")).andExpect(status().isBadRequest());
    }

    @Test
    void usersOnlySeeTheirOwnStats() throws Exception {
        log("focus", coding, "UTC", "2026-03-10T09:00:00Z", "2026-03-10T10:00:00Z");
        String otherToken = signup();

        mvc.perform(get("/stats/daily").param("from", "2026-03-10").param("to", "2026-03-10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void needsAToken() throws Exception {
        mvc.perform(get("/stats/daily").param("from", "2026-03-10").param("to", "2026-03-10"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder asMe(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder stats(String from, String to) {
        return asMe(get("/stats/daily").param("from", from).param("to", to));
    }

    // Yesterday through tomorrow in UTC, so a session started shortly before midnight is still counted in full.
    private long focusSecondsAroundToday() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String body = mvc.perform(stats(today.minusDays(1).toString(), today.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> seconds = JsonPath.read(body, "$[*].activities[*].totalSeconds");
        return seconds.stream().mapToLong(Number::longValue).sum();
    }

    private long startStopwatch() throws Exception {
        return id(mvc.perform(asMe(post("/sessions/stopwatch/start")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"activityId": %d, "timezone": "UTC"}
                        """.formatted(coding)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private long log(String kind, Integer activityId, String timezone, String startedAt, String endedAt) throws Exception {
        String activity = activityId == null ? "" : "\"activityId\": %d,".formatted(activityId);
        return id(mvc.perform(asMe(post("/sessions")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind": "%s", %s "timezone": "%s", "startedAt": "%s", "endedAt": "%s"}
                        """.formatted(kind, activity, timezone, startedAt, endedAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
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
                        {"username": "atin", "email": "%s@stats.test", "password": "correct horse battery"}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static long id(String body) {
        return JsonPath.<Number>read(body, "$.id").longValue();
    }
}
