package com.atinroy.timely.common;

import lombok.Getter;

// The code is the spec's problem `code`, e.g. "invalid-credentials".
@Getter
public class UnauthorizedException extends RuntimeException {
    private final String code;

    public UnauthorizedException(String code) {
        super(code);
        this.code = code;
    }
}
