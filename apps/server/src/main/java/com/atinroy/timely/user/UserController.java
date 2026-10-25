package com.atinroy.timely.user;

import com.atinroy.timely.api.UsersApi;
import com.atinroy.timely.api.model.PomodoroSettingsDto;
import com.atinroy.timely.api.model.UpdateUserRequestDto;
import com.atinroy.timely.api.model.UserDto;
import com.atinroy.timely.auth.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UsersApi {
    private final UserService users;

    @Override
    public ResponseEntity<UserDto> getMe() {
        return ResponseEntity.ok(UserMapper.toDto(getCurrentUser()));
    }

    @Override
    public ResponseEntity<UserDto> updateMe(UpdateUserRequestDto request) {
        PomodoroSettingsDto dto = request.getPomodoroSettings();
        PomodoroSettings settings = dto == null ? null : new PomodoroSettings(
                dto.getFocusMinutes(), dto.getShortBreakMinutes(),
                dto.getLongBreakMinutes(), dto.getPomodorosPerLongBreak());
        User user = users.update(AuthPrincipal.current().userId(), request.getUsername(), request.getTimezone(), settings);
        return ResponseEntity.ok(UserMapper.toDto(user));
    }

    private User getCurrentUser() {
        AuthPrincipal principal = AuthPrincipal.current();
        Integer userId = principal.userId();
        return users.get(userId);
    }
}
