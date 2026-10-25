package com.atinroy.timely.auth;

import com.atinroy.timely.common.UnauthorizedException;
import com.atinroy.timely.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    UserService users;

    @Mock
    AuthIdentityRepository identities;

    @Mock
    AuthTokenRepository tokens;

    @Mock
    PasswordEncoder encoder;

    // Skipping the hash check would let response timing reveal which emails have accounts.
    @Test
    void unknownEmailStillChecksAPasswordHash() {
        when(encoder.encode("dummy")).thenReturn("dummy-hash");
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        AuthService auth = new AuthService(users, identities, tokens, encoder);

        assertThatThrownBy(() -> auth.login("nobody@example.com", "guess", null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("invalid-credentials");

        verify(encoder).matches("guess", "dummy-hash");
        verifyNoInteractions(tokens);
    }
}
