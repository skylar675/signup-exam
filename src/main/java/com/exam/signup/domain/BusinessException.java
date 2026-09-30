package com.exam.signup.domain;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String clientMessage;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String clientMessage) {
        super(clientMessage);
        this.errorCode = errorCode;
        this.clientMessage = clientMessage;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String clientMessage() {
        return clientMessage;
    }
}
