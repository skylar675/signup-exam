package com.exam.signup.application;

import com.exam.signup.api.dto.PageResponse;
import com.exam.signup.api.dto.RegistrationResponse;
import com.exam.signup.domain.BusinessException;
import com.exam.signup.domain.ErrorCode;
import com.exam.signup.persistence.ActivityMapper;
import com.exam.signup.persistence.ActivityRow;
import com.exam.signup.persistence.RegistrationMapper;
import com.exam.signup.persistence.RegistrationRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final RegistrationMapper registrationMapper;
    private final ActivityMapper activityMapper;
    private final RegistrationTxWorker txWorker;

    public RegistrationService(RegistrationMapper registrationMapper,
                               ActivityMapper activityMapper,
                               RegistrationTxWorker txWorker) {
        this.registrationMapper = registrationMapper;
        this.activityMapper = activityMapper;
        this.txWorker = txWorker;
    }

    public RegistrationHttpResult register(long userId, long activityId, String requestId) {
        try {
            return registerOnce(userId, activityId, requestId);
        } catch (DeadlockLoserDataAccessException ex) {
            log.warn("registration deadlock, retry once userId={} activityId={}", userId, activityId);
            return registerOnce(userId, activityId, requestId);
        }
    }

    private RegistrationHttpResult registerOnce(long userId, long activityId, String requestId) {
        RegistrationRow existingRequest = registrationMapper.findByUserAndRequest(userId, requestId);
        if (existingRequest != null) {
            if (existingRequest.getActivityId() == activityId) {
                return new RegistrationHttpResult(200, existingRequest);
            }
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (registrationMapper.findByUserAndActivity(userId, activityId) != null) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }
        if (activityMapper.selectById(activityId) == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        try {
            EnrollResult result = txWorker.enroll(userId, activityId, requestId);
            return new RegistrationHttpResult(result.created() ? 201 : 200, result.registration());
        } catch (DataIntegrityViolationException ex) {
            log.warn("registration constraint rejected userId={} activityId={} requestId={}", userId, activityId, requestId, ex);
            return classifyAfterRollback(userId, activityId, requestId);
        }
    }

    public PageResponse<RegistrationResponse> list(long userId, PageParams page) {
        long total = registrationMapper.countByUser(userId);
        List<RegistrationResponse> items = registrationMapper.selectPageByUser(userId, page.offset(), page.size()).stream()
                .map(RegistrationResponse::from)
                .toList();
        return new PageResponse<>(items, total, page.page(), page.size());
    }

    public RegistrationResponse getOwn(long userId, String rawId) {
        Long registrationId = InputRules.parseRegistrationIdOrNull(rawId);
        if (registrationId == null) {
            throw new BusinessException(ErrorCode.REGISTRATION_NOT_FOUND);
        }
        RegistrationRow row = registrationMapper.findByIdAndUser(registrationId, userId);
        if (row == null) {
            throw new BusinessException(ErrorCode.REGISTRATION_NOT_FOUND);
        }
        return RegistrationResponse.from(row);
    }

    private RegistrationHttpResult classifyAfterRollback(long userId, long activityId, String requestId) {
        RegistrationRow byRequest = registrationMapper.findByUserAndRequest(userId, requestId);
        if (byRequest != null) {
            if (byRequest.getActivityId() == activityId) {
                return new RegistrationHttpResult(200, byRequest);
            }
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (registrationMapper.findByUserAndActivity(userId, activityId) != null) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }
        ActivityRow activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        if (!"OPEN".equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.ACTIVITY_CLOSED);
        }
        if (activity.getRemainingQuota() <= 0) {
            throw new BusinessException(ErrorCode.SOLD_OUT);
        }
        log.error("constraint conflict could not be classified userId={} activityId={} requestId={}", userId, activityId, requestId);
        throw new IllegalStateException("registration constraint could not be classified");
    }
}
