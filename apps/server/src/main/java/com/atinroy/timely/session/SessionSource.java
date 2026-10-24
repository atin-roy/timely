package com.atinroy.timely.session;

import jakarta.persistence.EnumeratedValue;

public enum SessionSource {
    TIMED("timed"),
    LOGGED("logged");

    @EnumeratedValue
    private final String value;

    SessionSource(String value) {
        this.value = value;
    }
}
