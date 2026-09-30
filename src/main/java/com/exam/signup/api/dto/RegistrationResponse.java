package com.exam.signup.api.dto;

import com.exam.signup.persistence.RegistrationRow;

public record RegistrationResponse(long id, long activityId, long userId, String requestId, String status) {

    public static RegistrationResponse from(RegistrationRow row) {
        return new RegistrationResponse(row.getId(), row.getActivityId(), row.getUserId(), row.getRequestId(), row.getStatus());
    }
}
