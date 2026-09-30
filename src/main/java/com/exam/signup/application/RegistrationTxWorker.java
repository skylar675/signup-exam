package com.exam.signup.application;

import com.exam.signup.domain.BusinessException;
import com.exam.signup.domain.ErrorCode;
import com.exam.signup.persistence.ActivityMapper;
import com.exam.signup.persistence.ActivityRow;
import com.exam.signup.persistence.RegistrationMapper;
import com.exam.signup.persistence.RegistrationRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationTxWorker {

    private final ActivityMapper activityMapper;
    private final RegistrationMapper registrationMapper;
    private final RegistrationInsertGuard insertGuard;

    public RegistrationTxWorker(ActivityMapper activityMapper,
                                RegistrationMapper registrationMapper,
                                RegistrationInsertGuard insertGuard) {
        this.activityMapper = activityMapper;
        this.registrationMapper = registrationMapper;
        this.insertGuard = insertGuard;
    }

    @Transactional(rollbackFor = Exception.class)
    public EnrollResult enroll(long userId, long activityId, String requestId) {
        ActivityRow locked = activityMapper.selectForUpdate(activityId);

        RegistrationRow byRequest = registrationMapper.findByUserAndRequest(userId, requestId);
        if (byRequest != null) {
            if (byRequest.getActivityId() == activityId) {
                return EnrollResult.replay(byRequest);
            }
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }

        RegistrationRow byActivity = registrationMapper.findByUserAndActivity(userId, activityId);
        if (byActivity != null) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }

        if (locked == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        if (!"OPEN".equals(locked.getStatus())) {
            throw new BusinessException(ErrorCode.ACTIVITY_CLOSED);
        }
        if (locked.getRemainingQuota() <= 0) {
            throw new BusinessException(ErrorCode.SOLD_OUT);
        }

        int updated = activityMapper.decrementQuota(activityId);
        if (updated != 1) {
            ActivityRow again = activityMapper.selectForUpdate(activityId);
            if (again == null) {
                throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
            }
            if (!"OPEN".equals(again.getStatus())) {
                throw new BusinessException(ErrorCode.ACTIVITY_CLOSED);
            }
            throw new BusinessException(ErrorCode.SOLD_OUT);
        }

        insertGuard.beforeInsert();

        RegistrationRow created = new RegistrationRow();
        created.setUserId(userId);
        created.setActivityId(activityId);
        created.setRequestId(requestId);
        created.setStatus("REGISTERED");
        registrationMapper.insert(created);
        return EnrollResult.created(created);
    }
}
