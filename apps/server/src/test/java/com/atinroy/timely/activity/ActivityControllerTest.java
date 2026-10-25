package com.atinroy.timely.activity;

import com.atinroy.timely.session.SessionKind;
import com.atinroy.timely.session.SessionService;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

// Runs against the compose Postgres. Every user it creates has an @activities.test email and is deleted afterwards,
// which cascades to their activities and sessions.
@SpringBootTest
@AutoConfigureMockMvc
class ActivityControllerTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SessionService sessions;

    private String token;
    private Integer userId;

    @BeforeEach
    void signUp() throws Exception {
        token = signup();
        userId = JsonPath.read(mvc.perform(asMe(get("/users/me"))).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @AfterEach
    void deleteTestUsers() {
        jdbc.update("delete from users where email like '%@activities.test'");
    }

    @Test
    void createReturnsTheNewActivity() throws Exception {
        mvc.perform(asMe(post("/activities")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Coding", "color": "#4f46e5"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Coding"))
                .andExpect(jsonPath("$.color").value("#4f46e5"))
                .andExpect(jsonPath("$.archivedAt").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void createRejectsANameTakenInAnyCase() throws Exception {
        create("Coding");

        mvc.perform(asMe(post("/activities")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "CODING", "color": "#000000"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("activity-name-taken"));
    }

    @Test
    void reusingAnArchivedNameUnarchivesItWithTheNewColour() throws Exception {
        int id = create("Guitar");
        archive(id);

        mvc.perform(asMe(post("/activities")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "guitar", "color": "#ff0000"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Guitar"))
                .andExpect(jsonPath("$.color").value("#ff0000"))
                .andExpect(jsonPath("$.archivedAt").value(nullValue()));

        assertThat(jdbc.queryForObject("select count(*) from activities where user_id = ?", Integer.class, userId))
                .isEqualTo(1);
    }

    @Test
    void createRejectsInvalidFields() throws Exception {
        mvc.perform(asMe(post("/activities")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "%s", "color": "blue"}
                        """.formatted("x".repeat(51))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad-request"))
                .andExpect(jsonPath("$.errors.length()").value(2));
        mvc.perform(asMe(post("/activities")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "", "color": "#12345"}
                        """))
                .andExpect(status().isBadRequest());
        mvc.perform(asMe(post("/activities")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listIsOrderedByNameAndHidesArchivedUnlessAsked() throws Exception {
        create("Reading");
        create("Coding");
        archive(create("Guitar"));

        mvc.perform(asMe(get("/activities")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(contains("Coding", "Reading")));
        mvc.perform(asMe(get("/activities").param("includeArchived", "true")))
                .andExpect(jsonPath("$[*].name").value(contains("Coding", "Guitar", "Reading")));
    }

    @Test
    void getReturnsTheActivity() throws Exception {
        int id = create("Coding");

        mvc.perform(asMe(get("/activities/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Coding"));
        mvc.perform(asMe(get("/activities/{id}", Integer.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not-found"));
    }

    @Test
    void updateChangesOnlyTheGivenFields() throws Exception {
        int id = create("Coding");

        mvc.perform(asMe(patch("/activities/{id}", id)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Programming"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Programming"))
                .andExpect(jsonPath("$.color").value("#4f46e5"));
        mvc.perform(asMe(patch("/activities/{id}", id)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"color": "#00ff00"}
                        """))
                .andExpect(status().isOk());

        mvc.perform(asMe(get("/activities/{id}", id)))
                .andExpect(jsonPath("$.name").value("Programming"))
                .andExpect(jsonPath("$.color").value("#00ff00"))
                .andExpect(jsonPath("$.archivedAt").value(nullValue()));
    }

    @Test
    void renamingKeepsTheActivitysHistory() throws Exception {
        int id = create("Coding");
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
        sessions.log(userId, SessionKind.FOCUS, id, "UTC", start, start.plus(1, ChronoUnit.HOURS));

        mvc.perform(asMe(patch("/activities/{id}", id)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Programming"}
                        """))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from sessions where activity_id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void renamingToItsOwnNameInAnotherCaseIsAllowed() throws Exception {
        int id = create("coding");

        mvc.perform(asMe(patch("/activities/{id}", id)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Coding"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Coding"));
    }

    @Test
    void renamingToAnotherActivitysNameIsRejectedEvenIfArchived() throws Exception {
        int coding = create("Coding");
        archive(create("Guitar"));
        create("Reading");

        for (String name : new String[]{"guitar", "READING"}) {
            mvc.perform(asMe(patch("/activities/{id}", coding)).contentType(MediaType.APPLICATION_JSON).content("""
                            {"name": "%s"}
                            """.formatted(name)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("activity-name-taken"));
        }
        mvc.perform(asMe(get("/activities/{id}", coding))).andExpect(jsonPath("$.name").value("Coding"));
    }

    @Test
    void archivingTwiceKeepsTheFirstArchiveTime() throws Exception {
        int id = create("Coding");
        String first = JsonPath.read(archive(id), "$.archivedAt");

        String second = JsonPath.read(archive(id), "$.archivedAt");

        assertThat(second).isEqualTo(first);
    }

    @Test
    void unarchivingClearsTheArchiveTime() throws Exception {
        int id = create("Coding");
        archive(id);

        mvc.perform(asMe(patch("/activities/{id}", id)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"archived": false}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").value(nullValue()));
    }

    @Test
    void archivingStopsASessionRunningOnTheActivity() throws Exception {
        int id = create("Coding");
        Long session = sessions.startStopwatch(userId, id, "UTC").getId();

        archive(id);

        assertThat(jdbc.queryForObject("select ended_at is not null from sessions where id = ?", Boolean.class, session))
                .isTrue();
    }

    @Test
    void archivingLeavesASessionOnAnotherActivityRunning() throws Exception {
        int coding = create("Coding");
        int guitar = create("Guitar");
        Long session = sessions.startStopwatch(userId, guitar, "UTC").getId();

        archive(coding);

        assertThat(jdbc.queryForObject("select ended_at is null from sessions where id = ?", Boolean.class, session))
                .isTrue();
    }

    @Test
    void updateRejectsAnInvalidColour() throws Exception {
        int id = create("Coding");

        mvc.perform(asMe(patch("/activities/{id}", id)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"color": "#GGGGGG"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("color"));
    }

    @Test
    void deleteWipesTheActivityAndItsSessions() throws Exception {
        int id = create("Coding");
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
        sessions.log(userId, SessionKind.FOCUS, id, "UTC", start, start.plus(1, ChronoUnit.HOURS));

        mvc.perform(asMe(delete("/activities/{id}", id))).andExpect(status().isNoContent());

        mvc.perform(asMe(get("/activities/{id}", id))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from sessions where user_id = ?", Integer.class, userId))
                .isZero();
    }

    @Test
    void deletedNamesCanBeReused() throws Exception {
        int id = create("Coding");
        mvc.perform(asMe(delete("/activities/{id}", id))).andExpect(status().isNoContent());

        mvc.perform(asMe(post("/activities")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Coding", "color": "#4f46e5"}
                        """))
                .andExpect(status().isCreated());
    }

    @Test
    void usersCannotSeeOrTouchEachOthersActivities() throws Exception {
        int mine = create("Coding");
        String otherToken = signup();
        MockHttpServletRequestBuilder[] requests = {
                get("/activities/{id}", mine),
                patch("/activities/{id}", mine).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Stolen"}
                        """),
                delete("/activities/{id}", mine),
        };

        for (MockHttpServletRequestBuilder request : requests) {
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/activities").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(asMe(get("/activities/{id}", mine))).andExpect(jsonPath("$.name").value("Coding"));
    }

    @Test
    void namesAreOnlyUniquePerUser() throws Exception {
        create("Coding");
        String otherToken = signup();

        mvc.perform(post("/activities").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name": "Coding", "color": "#4f46e5"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void endpointsNeedAToken() throws Exception {
        mvc.perform(get("/activities")).andExpect(status().isUnauthorized());
        mvc.perform(post("/activities").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Coding", "color": "#4f46e5"}
                        """))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/activities/1")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/activities/1")).andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder asMe(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private int create(String name) throws Exception {
        String body = mvc.perform(asMe(post("/activities")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "%s", "color": "#4f46e5"}
                        """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String archive(int id) throws Exception {
        return mvc.perform(asMe(patch("/activities/{id}", id)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"archived": true}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").isString())
                .andReturn().getResponse().getContentAsString();
    }

    private String signup() throws Exception {
        String body = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "atin", "email": "%s@activities.test", "password": "correct horse battery"}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
