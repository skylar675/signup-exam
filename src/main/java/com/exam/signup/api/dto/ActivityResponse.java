package com.exam.signup.api.dto;

public record ActivityResponse(long id, String title, String status, int totalQuota, int remainingQuota) {
}
