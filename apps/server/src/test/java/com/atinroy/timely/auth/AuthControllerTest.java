package com.atinroy.timely.auth;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against the compose Postgres. Every user it creates has an @auth.test email and is deleted afterwards.
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {
    private static final String PASSWORD = "correct horse battery";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final String email = UUID.randomUUID() + "@auth.test";

    @AfterEach
    void deleteTestUsers() {
        jdbc.update("delete from users where email like '%@auth.test'");
    }

    @Test
    void signupCreatesUserWithDefaultsAndLogsThemIn() throws Exception {
        MvcResult result = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "atin", "email": "%s", "password": "%s"}
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value("atin"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.timezone").value("UTC"))
                .andExpect(jsonPath("$.user.pomodoroSettings.focusMinutes").value(25))
                .andExpect(jsonPath("$.user.pomodoroSettings.shortBreakMinutes").value(5))
                .andExpect(jsonPath("$.user.pomodoroSettings.longBreakMinutes").value(15))
                .andExpect(jsonPath("$.user.pomodoroSettings.pomodorosPerLongBreak").value(4))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn();

        String token = token(result);
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains(TokenAuthenticationFilter.COOKIE + "=" + token)
                .contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/");
        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void signupKeepsTheGivenTimezone() throws Exception {
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "atin", "email": "%s", "password": "%s", "timezone": "Asia/Kolkata"}
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.timezone").value("Asia/Kolkata"));
    }

    @Test
    void signupRejectsAnEmailTakenInAnyCase() throws Exception {
        signup();

        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "other", "email": "%s", "password": "%s"}
                        """.formatted(email.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email-taken"));
    }

    @Test
    void signupRejectsInvalidFields() throws Exception {
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "", "email": "not-an-email", "password": "short"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad-request"))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("username", "email", "password")));
    }

    @Test
    void signupRejectsTimezonesThatAreNotRegions() throws Exception {
        for (String timezone : new String[]{"Mars/Olympus", "+05:30"}) {
            mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                            {"username": "atin", "email": "%s", "password": "%s", "timezone": "%s"}
                            """.formatted(email, PASSWORD, timezone)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("bad-request"));
        }
        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email)).isZero();
    }

    @Test
    void loginIgnoresEmailCaseAndRecordsTheDevice() throws Exception {
        signup();

        MvcResult result = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s", "deviceName": "Pixel 9"}
                        """.formatted(email.toUpperCase(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andReturn();

        String deviceName = jdbc.queryForObject(
                "select device_name from auth_tokens where token_hash = ?", String.class, (Object) sha256(token(result)));
        assertThat(deviceName).isEqualTo("Pixel 9");
    }

    @Test
    void wrongPasswordAndUnknownEmailFailTheSameWay() throws Exception {
        signup();

        String wrongPassword = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "wrong password"}
                        """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid-credentials"))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "nobody-%s", "password": "%s"}
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    @Test
    void theReturnedExpiryIsExactlyWhatIsStored() throws Exception {
        MvcResult result = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "atin", "email": "%s", "password": "%s"}
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();

        String returned = JsonPath.read(result.getResponse().getContentAsString(), "$.expiresAt");
        OffsetDateTime stored = jdbc.queryForObject("select expires_at from auth_tokens where token_hash = ?",
                OffsetDateTime.class, (Object) sha256(token(result)));
        assertThat(OffsetDateTime.parse(returned).toInstant()).isEqualTo(stored.toInstant());
    }

    @Test
    void onlyTheTokenHashIsStored() throws Exception {
        String token = signup();

        byte[] stored = jdbc.queryForObject(
                "select t.token_hash from auth_tokens t join users u on u.id = t.user_id where u.email = ?",
                byte[].class, email);
        assertThat(stored).isEqualTo(sha256(token));
    }

    @Test
    void protectedEndpointsNeedAToken() throws Exception {
        mvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer made-up"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theCookieAuthenticatesWebClients() throws Exception {
        String token = signup();

        mvc.perform(get("/users/me").cookie(new Cookie(TokenAuthenticationFilter.COOKIE, token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void theBearerHeaderWinsOverTheCookie() throws Exception {
        String token = signup();

        mvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer made-up")
                        .cookie(new Cookie(TokenAuthenticationFilter.COOKIE, token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokensAreRejected() throws Exception {
        String token = signup();
        jdbc.update("update auth_tokens set expires_at = now() - interval '1 second' where token_hash = ?",
                (Object) sha256(token));

        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesOnlyTheTokenUsedAndClearsTheCookie() throws Exception {
        String first = signup();
        String second = login();

        mvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + first))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(TokenAuthenticationFilter.COOKIE + "=;")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + first))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + second))
                .andExpect(status().isOk());
    }

    @Test
    void logoutAllRevokesEveryToken() throws Exception {
        String first = signup();
        String second = login();

        mvc.perform(post("/auth/logout-all").header(HttpHeaders.AUTHORIZATION, "Bearer " + first))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + first))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + second))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutNeedsAToken() throws Exception {
        mvc.perform(post("/auth/logout")).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/logout-all")).andExpect(status().isUnauthorized());
    }

    private String signup() throws Exception {
        return token(mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "atin", "email": "%s", "password": "%s"}
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private String login() throws Exception {
        return token(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static String token(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private static byte[] sha256(String token) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
    }
}
