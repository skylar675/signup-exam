package com.exam.signup.api;

import com.exam.signup.api.dto.ApiError;
import com.exam.signup.domain.BusinessException;
import com.exam.signup.domain.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        ErrorCode errorCode = ex.errorCode();
        return ResponseEntity.status(errorCode.httpStatus())
                .body(new ApiError(errorCode.code(), ex.clientMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("unreadable request body");
        return invalid("请求体无效");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("unhandled error", ex);
        return ResponseEntity.internalServerError().body(new ApiError("INTERNAL_ERROR", "内部错误"));
    }

    private static ResponseEntity<ApiError> invalid(String message) {
        return ResponseEntity.badRequest().body(new ApiError(ErrorCode.INVALID_ARGUMENT.code(), message));
    }
}
