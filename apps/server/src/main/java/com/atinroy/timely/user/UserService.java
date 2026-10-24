package com.atinroy.timely.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository users;

    public User get(Integer id) {
        return users.findById(id).orElseThrow();
    }

    public Optional<User> findByEmail(String email) {
        return users.findByEmailIgnoreCase(email);
    }

    @Transactional
    public User create(String username, String email, String timezone) {
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new EmailTakenException();
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setTimezone(requireRegionTimezone(timezone));
        return users.save(user);
    }

    // A null argument leaves that field as it is.
    @Transactional
    public User updateProfile(Integer id, String username, String timezone) {
        User user = get(id);
        if (username != null) {
            user.setUsername(username);
        }
        if (timezone != null) {
            user.setTimezone(requireRegionTimezone(timezone));
        }
        return user;
    }

    // Replaces all four settings. Cycles already started keep the settings they copied.
    @Transactional
    public User updatePomodoroSettings(Integer id, int focusMinutes, int shortBreakMinutes,
                                       int longBreakMinutes, int pomodorosPerLongBreak) {
        User user = get(id);
        user.setFocusMinutes(focusMinutes);
        user.setShortBreakMinutes(shortBreakMinutes);
        user.setLongBreakMinutes(longBreakMinutes);
        user.setPomodorosPerLongBreak(pomodorosPerLongBreak);
        return user;
    }

    // Region names only. Offsets like "+05:30" parse in Java but mean the opposite sign to Postgres's AT TIME ZONE.
    private static String requireRegionTimezone(String timezone) {
        if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new IllegalArgumentException("Unknown timezone: " + timezone);
        }
        return timezone;
    }
}
