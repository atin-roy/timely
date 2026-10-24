package com.atinroy.timely.session;

import jakarta.persistence.EnumeratedValue;

public enum SessionKind {
    FOCUS("focus"),
    BREAK("break");

    @EnumeratedValue
    private final String value;

    SessionKind(String value) {
        this.value = value;
    }
}
