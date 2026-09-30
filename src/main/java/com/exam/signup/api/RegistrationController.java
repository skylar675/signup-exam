package com.exam.signup.api;

import com.exam.signup.api.dto.ApiData;
import com.exam.signup.api.dto.CreateRegistrationRequest;
import com.exam.signup.api.dto.PageResponse;
import com.exam.signup.api.dto.RegistrationResponse;
import com.exam.signup.application.InputRules;
import com.exam.signup.application.RegistrationHttpResult;
import com.exam.signup.application.RegistrationService;
import com.exam.signup.domain.BusinessException;
import com.exam.signup.domain.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/api/registrations")
    public ResponseEntity<ApiData<RegistrationResponse>> create(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody(required = false) CreateRegistrationRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请求体无效");
        }
        long user = InputRules.parseUserId(userId);
        long activityId = InputRules.parseActivityId(request.getActivityId());
        String requestId = InputRules.parseRequestId(request.getRequestId());
        RegistrationHttpResult result = registrationService.register(user, activityId, requestId);
        return ResponseEntity.status(result.httpStatus())
                .body(new ApiData<>(RegistrationResponse.from(result.registration())));
    }

    @GetMapping("/api/registrations")
    public ApiData<PageResponse<RegistrationResponse>> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(value = "page", required = false) String page,
            @RequestParam(value = "size", required = false) String size) {
        return new ApiData<>(registrationService.list(InputRules.parseUserId(userId), InputRules.parsePage(page, size)));
    }

    @GetMapping("/api/registrations/{id}")
    public ApiData<RegistrationResponse> detail(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable("id") String id) {
        return new ApiData<>(registrationService.getOwn(InputRules.parseUserId(userId), id));
    }
}
