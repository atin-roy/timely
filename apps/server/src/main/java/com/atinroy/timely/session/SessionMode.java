package com.atinroy.timely.session;

import jakarta.persistence.EnumeratedValue;

public enum SessionMode {
    STOPWATCH("stopwatch"),
    COUNTDOWN("countdown");

    @EnumeratedValue
    private final String value;

    SessionMode(String value) {
        this.value = value;
    }
}
