package com.exam.signup.application;

import com.exam.signup.domain.BusinessException;
import com.exam.signup.domain.ErrorCode;

public final class InputRules {

    private static final java.util.regex.Pattern REQUEST_ID = java.util.regex.Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final java.util.regex.Pattern DIGITS = java.util.regex.Pattern.compile("^[0-9]+$");
    private static final java.util.regex.Pattern INTEGER_TOKEN = java.util.regex.Pattern.compile("^-?[0-9]+$");

    private InputRules() {
    }

    public static long parseUserId(String header) {
        if (header == null || !DIGITS.matcher(header).matches()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "用户标识无效");
        }
        long userId = parseLong(header, "用户标识无效");
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "用户标识无效");
        }
        return userId;
    }

    public static long parseActivityId(Long activityId) {
        if (activityId == null || activityId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "活动 ID 无效");
        }
        return activityId;
    }

    public static long parseActivityPathId(String raw) {
        if (raw == null || !DIGITS.matcher(raw).matches()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "活动 ID 无效");
        }
        long activityId = parseLong(raw, "活动 ID 无效");
        if (activityId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "活动 ID 无效");
        }
        return activityId;
    }

    public static String parseRequestId(String requestId) {
        if (requestId == null || !REQUEST_ID.matcher(requestId).matches()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请求键无效");
        }
        return requestId;
    }

    public static PageParams parsePage(String pageRaw, String sizeRaw) {
        int page = pageRaw == null ? 1 : parsePageToken(pageRaw);
        int size = sizeRaw == null ? 10 : parsePageToken(sizeRaw);
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "分页参数错误");
        }
        return new PageParams(page, size);
    }

    public static Long parseRegistrationIdOrNull(String raw) {
        if (raw == null || !DIGITS.matcher(raw).matches()) {
            return null;
        }
        try {
            long value = Long.parseLong(raw);
            if (value <= 0) {
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int parsePageToken(String raw) {
        if (!INTEGER_TOKEN.matcher(raw).matches()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "分页参数错误");
        }
        long value = parseLong(raw, "分页参数错误");
        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "分页参数错误");
        }
        return (int) value;
    }

    private static long parseLong(String raw, String message) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
        }
    }
}
