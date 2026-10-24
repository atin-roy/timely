package com.atinroy.timely.common;

import lombok.Getter;

// The code is the spec's problem `code`, e.g. "not-found" or "no-running-session".
@Getter
public class NotFoundException extends RuntimeException {
    private final String code;

    public NotFoundException(String code) {
        super(code);
        this.code = code;
    }
}
