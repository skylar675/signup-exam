package com.exam.signup.domain;

public enum ErrorCode {
    INVALID_ARGUMENT(400, "INVALID_ARGUMENT", "参数错误"),
    ACTIVITY_NOT_FOUND(404, "ACTIVITY_NOT_FOUND", "活动不存在"),
    REGISTRATION_NOT_FOUND(404, "REGISTRATION_NOT_FOUND", "报名不存在"),
    ACTIVITY_CLOSED(409, "ACTIVITY_CLOSED", "活动已关闭"),
    SOLD_OUT(409, "SOLD_OUT", "名额已满"),
    ALREADY_REGISTERED(409, "ALREADY_REGISTERED", "已报名该活动"),
    IDEMPOTENCY_CONFLICT(409, "IDEMPOTENCY_CONFLICT", "请求键已绑定其他活动");

    private final int httpStatus;
    private final String code;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
