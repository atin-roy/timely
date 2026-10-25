package com.atinroy.timely.user;

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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against the compose Postgres. Every user it creates has a @users.test email and is deleted afterwards.
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final String email = UUID.randomUUID() + "@users.test";
    private String token;

    @BeforeEach
    void signUp() throws Exception {
        token = signup("atin", email);
    }

    @AfterEach
    void deleteTestUsers() {
        jdbc.update("delete from users where email like '%@users.test'");
    }

    @Test
    void getMeReturnsTheCallersProfile() throws Exception {
        mvc.perform(asMe(get("/users/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("atin"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.timezone").value("UTC"))
                .andExpect(jsonPath("$.pomodoroSettings.focusMinutes").value(25))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void updateChangesOnlyTheGivenFields() throws Exception {
        mvc.perform(asMe(patch("/users/me")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "renamed"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("renamed"));

        mvc.perform(asMe(get("/users/me")))
                .andExpect(jsonPath("$.username").value("renamed"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.timezone").value("UTC"))
                .andExpect(jsonPath("$.pomodoroSettings.focusMinutes").value(25));
    }

    @Test
    void updateChangesTheTimezone() throws Exception {
        mvc.perform(asMe(patch("/users/me")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"timezone": "Asia/Kolkata"}
                        """))
                .andExpect(status().isOk());

        mvc.perform(asMe(get("/users/me"))).andExpect(jsonPath("$.timezone").value("Asia/Kolkata"));
    }

    @Test
    void updateRejectsTimezonesThatAreNotRegions() throws Exception {
        for (String timezone : new String[]{"Mars/Olympus", "+05:30"}) {
            mvc.perform(asMe(patch("/users/me")).contentType(MediaType.APPLICATION_JSON).content("""
                            {"timezone": "%s"}
                            """.formatted(timezone)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("bad-request"));
        }
        mvc.perform(asMe(get("/users/me"))).andExpect(jsonPath("$.timezone").value("UTC"));
    }

    @Test
    void updateReplacesAllPomodoroSettings() throws Exception {
        mvc.perform(asMe(patch("/users/me")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"pomodoroSettings": {"focusMinutes": 50, "shortBreakMinutes": 10,
                                              "longBreakMinutes": 30, "pomodorosPerLongBreak": 3}}
                        """))
                .andExpect(status().isOk());

        mvc.perform(asMe(get("/users/me")))
                .andExpect(jsonPath("$.username").value("atin"))
                .andExpect(jsonPath("$.pomodoroSettings.focusMinutes").value(50))
                .andExpect(jsonPath("$.pomodoroSettings.shortBreakMinutes").value(10))
                .andExpect(jsonPath("$.pomodoroSettings.longBreakMinutes").value(30))
                .andExpect(jsonPath("$.pomodoroSettings.pomodorosPerLongBreak").value(3));
    }

    @Test
    void updateRejectsPartialOrOutOfRangePomodoroSettings() throws Exception {
        mvc.perform(asMe(patch("/users/me")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"pomodoroSettings": {"focusMinutes": 50}}
                        """))
                .andExpect(status().isBadRequest());
        mvc.perform(asMe(patch("/users/me")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"pomodoroSettings": {"focusMinutes": 0, "shortBreakMinutes": 5,
                                              "longBreakMinutes": 15, "pomodorosPerLongBreak": 101}}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));

        mvc.perform(asMe(get("/users/me"))).andExpect(jsonPath("$.pomodoroSettings.focusMinutes").value(25));
    }

    @Test
    void updateRejectsABlankUsername() throws Exception {
        mvc.perform(asMe(patch("/users/me")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": ""}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("username"));
    }

    @Test
    void usersOnlySeeAndChangeThemselves() throws Exception {
        String otherEmail = UUID.randomUUID() + "@users.test";
        String otherToken = signup("other", otherEmail);

        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(jsonPath("$.email").value(otherEmail));
        mvc.perform(patch("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"username": "hijacked"}
                                """))
                .andExpect(status().isOk());

        mvc.perform(asMe(get("/users/me")))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.username").value("atin"));
    }

    @Test
    void endpointsNeedAToken() throws Exception {
        mvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/users/me").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "anon"}
                        """))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder asMe(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String signup(String username, String email) throws Exception {
        String body = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "%s", "email": "%s", "password": "correct horse battery"}
                        """.formatted(username, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
