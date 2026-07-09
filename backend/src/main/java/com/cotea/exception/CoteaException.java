package com.cotea.exception;

import lombok.Getter;

@Getter
public class CoteaException extends RuntimeException {

    private final String errorCode;
    private final int status;

    public CoteaException(String errorCode, String message, int status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }
}
