package com.exam.signup.application;

import com.exam.signup.api.dto.ActivityResponse;
import com.exam.signup.api.dto.PageResponse;
import com.exam.signup.cache.ActivityCache;
import com.exam.signup.cache.ActivityCacheValue;
import com.exam.signup.cache.CacheLookup;
import com.exam.signup.domain.BusinessException;
import com.exam.signup.domain.ErrorCode;
import com.exam.signup.persistence.ActivityMapper;
import com.exam.signup.persistence.ActivityRow;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ActivityQueryService {

    private final ActivityMapper activityMapper;
    private final ActivityCache activityCache;

    public ActivityQueryService(ActivityMapper activityMapper, ActivityCache activityCache) {
        this.activityMapper = activityMapper;
        this.activityCache = activityCache;
    }

    public PageResponse<ActivityResponse> list(PageParams page) {
        long total = activityMapper.count();
        List<ActivityResponse> items = activityMapper.selectPage(page.offset(), page.size()).stream()
                .map(ActivityQueryService::toResponse)
                .toList();
        return new PageResponse<>(items, total, page.page(), page.size());
    }

    public ActivityResponse detail(long activityId) {
        CacheLookup lookup = activityCache.read(activityId);
        if (lookup.status() == CacheLookup.Status.HIT) {
            Integer remaining = activityMapper.selectRemaining(activityId);
            if (remaining == null) {
                throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
            }
            ActivityCacheValue cached = lookup.value();
            return new ActivityResponse(cached.id(), cached.title(), cached.status(), cached.totalQuota(), remaining);
        }
        ActivityRow row = activityMapper.selectById(activityId);
        if (row == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        if (lookup.status() == CacheLookup.Status.MISS) {
            activityCache.write(new ActivityCacheValue(row.getId(), row.getTitle(), row.getStatus(), row.getTotalQuota()));
        }
        return toResponse(row);
    }

    private static ActivityResponse toResponse(ActivityRow row) {
        return new ActivityResponse(row.getId(), row.getTitle(), row.getStatus(), row.getTotalQuota(), row.getRemainingQuota());
    }
}
