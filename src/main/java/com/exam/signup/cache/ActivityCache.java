package com.exam.signup.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ActivityCache {

    private static final Logger log = LoggerFactory.getLogger(ActivityCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;

    public ActivityCache(StringRedisTemplate redis,
                         ObjectMapper objectMapper,
                         @Value("${signup.activity-cache-ttl-seconds:60}") long ttlSeconds) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttlSeconds = ttlSeconds;
    }

    public CacheLookup read(long activityId) {
        try {
            String json = redis.opsForValue().get(key(activityId));
            if (json == null) {
                log.info("activity cache miss id={}", activityId);
                return CacheLookup.miss();
            }
            ActivityCacheValue value = objectMapper.readValue(json, ActivityCacheValue.class);
            log.info("activity cache hit id={}", activityId);
            return CacheLookup.hit(value);
        } catch (DataAccessException | JsonProcessingException ex) {
            log.warn("activity cache read failed id={}, fallback to mysql", activityId, ex);
            return CacheLookup.error();
        }
    }

    public void write(ActivityCacheValue value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redis.opsForValue().set(key(value.id()), json, Duration.ofSeconds(ttlSeconds));
            log.info("activity cache write id={} ttlSeconds={}", value.id(), ttlSeconds);
        } catch (DataAccessException | JsonProcessingException ex) {
            log.warn("activity cache write failed id={}", value.id(), ex);
        }
    }

    public static String key(long activityId) {
        return "activity:" + activityId;
    }
}
