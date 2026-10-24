package com.atinroy.timely.common;

import lombok.Getter;

// The code is the spec's problem `code`, e.g. "session-overlap" or "activity-name-taken".
@Getter
public class ConflictException extends RuntimeException {
    private final String code;

    public ConflictException(String code) {
        super(code);
        this.code = code;
    }
}
