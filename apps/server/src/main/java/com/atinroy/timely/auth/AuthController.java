package com.atinroy.timely.auth;

import com.atinroy.timely.api.AuthApi;
import com.atinroy.timely.api.model.AuthResponseDto;
import com.atinroy.timely.api.model.LoginRequestDto;
import com.atinroy.timely.api.model.SignupRequestDto;
import com.atinroy.timely.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {
    private final AuthService auth;

    @Override
    public ResponseEntity<AuthResponseDto> signup(SignupRequestDto request) {
        return loggedIn(HttpStatus.CREATED,
                auth.signup(request.getUsername(), request.getEmail(), request.getPassword(), request.getTimezone()));
    }

    @Override
    public ResponseEntity<AuthResponseDto> login(LoginRequestDto request) {
        return loggedIn(HttpStatus.OK,
                auth.login(request.getEmail(), request.getPassword(), request.getDeviceName()));
    }

    @Override
    public ResponseEntity<Void> logout() {
        auth.logout(AuthPrincipal.current().tokenHash());
        return loggedOut();
    }

    @Override
    public ResponseEntity<Void> logoutAll() {
        auth.logoutAll(AuthPrincipal.current().userId());
        return loggedOut();
    }

    // Web clients use the cookie, everyone else reads the token from the body.
    private ResponseEntity<AuthResponseDto> loggedIn(HttpStatus status, AuthService.Login login) {
        ResponseCookie cookie = cookie(login.token(), Duration.between(Instant.now(), login.expiresAt()));
        AuthResponseDto body = new AuthResponseDto()
                .token(login.token())
                .expiresAt(OffsetDateTime.ofInstant(login.expiresAt(), ZoneOffset.UTC))
                .user(UserMapper.toDto(login.user()));
        return ResponseEntity.status(status).header(HttpHeaders.SET_COOKIE, cookie.toString()).body(body);
    }

    private ResponseEntity<Void> loggedOut() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(TokenAuthenticationFilter.COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
