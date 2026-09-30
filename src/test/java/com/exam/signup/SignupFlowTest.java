package com.exam.signup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SignupFlowTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void resetState() throws Exception {
        DbScripts.reset(dataSource);
        for (int id = 1001; id <= 1007; id++) {
            redis.delete("activity:" + id);
        }
    }

    @Test
    void activityListUsesDefaultsAndIncludesClosedAndZeroQuota() throws Exception {
        JsonNode data = data(rest.getForEntity("/api/activities", String.class));
        assertEquals(1, data.get("page").asInt());
        assertEquals(10, data.get("size").asInt());
        assertEquals(7, data.get("total").asInt());
        assertEquals(7, data.get("items").size());
        assertEquals(1001, data.get("items").get(0).get("id").asInt());
        assertEquals(1007, data.get("items").get(6).get("id").asInt());
        assertEquals("CLOSED", data.get("items").get(3).get("status").asText());
        assertEquals(0, data.get("items").get(2).get("remainingQuota").asInt());

        JsonNode beyond = data(rest.getForEntity("/api/activities?page=2&size=10", String.class));
        assertEquals(200, rest.getForEntity("/api/activities?page=2&size=10", String.class).getStatusCode().value());
        assertEquals(0, beyond.get("items").size());
        assertEquals(7, beyond.get("total").asInt());

        assertEquals(400, rest.getForEntity("/api/activities?page=0", String.class).getStatusCode().value());
        assertEquals("INVALID_ARGUMENT", code(rest.getForEntity("/api/activities?size=101", String.class)));
        assertEquals("INVALID_ARGUMENT", code(rest.getForEntity("/api/activities?page=abc", String.class)));
    }

    @Test
    void detailRemainingComesFromMysqlAndUnknownActivityIs404() throws Exception {
        ResponseEntity<String> missing = rest.getForEntity("/api/activities/9999", String.class);
        assertEquals(404, missing.getStatusCode().value());
        assertEquals("ACTIVITY_NOT_FOUND", code(missing));
        assertEquals(400, rest.getForEntity("/api/activities/abc", String.class).getStatusCode().value());

        JsonNode detail = data(rest.getForEntity("/api/activities/1001", String.class));
        assertEquals(5, detail.get("remainingQuota").asInt());
        assertEquals(5, detail.get("totalQuota").asInt());
    }

    @Test
    void redisMissThenHitThenExpire() throws Exception {
        ResponseEntity<String> first = rest.getForEntity("/api/activities/1001", String.class);
        assertEquals(200, first.getStatusCode().value());
        String cached = redis.opsForValue().get("activity:1001");
        assertNotNull(cached);
        assertFalse(cached.contains("remainingQuota"));
        assertTrue(cached.contains("Java 实战分享"));

        jdbc.update("UPDATE activity SET title = ?, remaining_quota = 4 WHERE id = 1001", "缓存对照标题");
        JsonNode second = data(rest.getForEntity("/api/activities/1001", String.class));
        assertEquals("Java 实战分享", second.get("title").asText());
        assertEquals(4, second.get("remainingQuota").asInt());

        redis.expire("activity:1001", Duration.ofSeconds(1));
        Thread.sleep(1500);
        JsonNode third = data(rest.getForEntity("/api/activities/1001", String.class));
        assertEquals("缓存对照标题", third.get("title").asText());
        assertEquals(4, third.get("remainingQuota").asInt());
        assertNotNull(redis.opsForValue().get("activity:1001"));
    }

    @Test
    void firstRegistrationIs201AndReplayIs200() throws Exception {
        ResponseEntity<String> created = post(1101, 1001, "signup_001");
        assertEquals(201, created.getStatusCode().value());
        long id = data(created).get("id").asLong();
        assertEquals(4, remaining(1001));

        ResponseEntity<String> replay = post(1101, 1001, "signup_001");
        assertEquals(200, replay.getStatusCode().value());
        assertEquals(id, data(replay).get("id").asLong());
        assertEquals(4, remaining(1001));
        assertEquals(1, registrationCount(1001));
        assertInvariant();
    }

    @Test
    void differentRequestIdCannotBypassDedup() throws Exception {
        assertEquals(201, post(1101, 1001, "key_a").getStatusCode().value());
        ResponseEntity<String> second = post(1101, 1001, "key_b");
        assertEquals(409, second.getStatusCode().value());
        assertEquals("ALREADY_REGISTERED", code(second));
        assertEquals(4, remaining(1001));
        assertEquals(1, registrationCount(1001));
    }

    @Test
    void sameRequestIdOnAnotherActivityConflictsWithoutDeducting() throws Exception {
        assertEquals(201, post(1101, 1001, "bound_key").getStatusCode().value());
        ResponseEntity<String> conflict = post(1101, 1005, "bound_key");
        assertEquals(409, conflict.getStatusCode().value());
        assertEquals("IDEMPOTENCY_CONFLICT", code(conflict));
        assertEquals(5, remaining(1005));
        assertEquals(0, registrationCount(1005));
        assertFalse(conflict.getBody().contains("Duplicate entry"));
    }

    @Test
    void failedSoldOutDoesNotBindRequestId() throws Exception {
        ResponseEntity<String> soldOut = post(1101, 1003, "retry_key");
        assertEquals(409, soldOut.getStatusCode().value());
        assertEquals("SOLD_OUT", code(soldOut));
        assertEquals(0, countRequest(1101, "retry_key"));
        assertEquals(201, post(1101, 1001, "retry_key").getStatusCode().value());
        assertEquals(1, countRequest(1101, "retry_key"));
        assertEquals(0, remaining(1003));
        assertEquals(4, remaining(1001));
    }

    @Test
    void closedActivityDoesNotBindRequestId() throws Exception {
        ResponseEntity<String> closed = post(1101, 1004, "closed_key");
        assertEquals(409, closed.getStatusCode().value());
        assertEquals("ACTIVITY_CLOSED", code(closed));
        assertEquals(0, countRequest(1101, "closed_key"));
        assertEquals(5, remaining(1004));
        assertEquals(201, post(1101, 1001, "closed_key").getStatusCode().value());
    }

    @Test
    void replayAfterSoldOutReturnsOriginalRow() throws Exception {
        ResponseEntity<String> created = post(1101, 1001, "signup_001");
        long id = data(created).get("id").asLong();
        jdbc.update("UPDATE activity SET remaining_quota = 0 WHERE id = 1001");
        ResponseEntity<String> replay = post(1101, 1001, "signup_001");
        assertEquals(200, replay.getStatusCode().value());
        assertEquals(id, data(replay).get("id").asLong());
        assertFalse(replay.getBody().contains("SOLD_OUT"));
        assertEquals(0, remaining(1001));
        assertEquals(1, registrationCount(1001));
    }

    @Test
    void replayAfterClosedReturnsOriginalRow() throws Exception {
        ResponseEntity<String> created = post(1101, 1001, "signup_001");
        long id = data(created).get("id").asLong();
        jdbc.update("UPDATE activity SET status = 'CLOSED' WHERE id = 1001");
        ResponseEntity<String> replay = post(1101, 1001, "signup_001");
        assertEquals(200, replay.getStatusCode().value());
        assertEquals(id, data(replay).get("id").asLong());
        assertFalse(replay.getBody().contains("ACTIVITY_CLOSED"));
        assertEquals(1, registrationCount(1001));
    }

    @Test
    void userCanOnlyReadOwnRegistration() throws Exception {
        long id = data(post(1101, 1001, "own_key")).get("id").asLong();
        ResponseEntity<String> own = exchangeGet("/api/registrations/" + id, "1101");
        assertEquals(200, own.getStatusCode().value());
        ResponseEntity<String> other = exchangeGet("/api/registrations/" + id, "1102");
        assertEquals(404, other.getStatusCode().value());
        assertEquals("REGISTRATION_NOT_FOUND", code(other));
        ResponseEntity<String> missing = exchangeGet("/api/registrations/999999", "1101");
        assertEquals(404, missing.getStatusCode().value());
        assertEquals("REGISTRATION_NOT_FOUND", code(missing));
        assertEquals(404, exchangeGet("/api/registrations/abc", "1101").getStatusCode().value());

        JsonNode list = data(exchangeGet("/api/registrations", "1101"));
        assertEquals(1, list.get("total").asInt());
        assertEquals(0, data(exchangeGet("/api/registrations", "1102")).get("total").asInt());
    }

    @Test
    void invalidUserAndRequestAre400() throws Exception {
        assertEquals(400, postRaw(null, 1001, "signup_001").getStatusCode().value());
        assertEquals("INVALID_ARGUMENT", code(postRaw("0", 1001, "signup_001")));
        assertEquals("INVALID_ARGUMENT", code(postRaw("abc", 1001, "signup_001")));
        assertEquals("INVALID_ARGUMENT", code(postRaw("1101", 1001, "bad key")));
        assertEquals("INVALID_ARGUMENT", code(postRaw("1101", 0, "signup_001")));
        ResponseEntity<String> missingActivity = post(1101, 9999, "missing_act");
        assertEquals(404, missingActivity.getStatusCode().value());
        assertEquals("ACTIVITY_NOT_FOUND", code(missingActivity));
        assertEquals(0, countRequest(1101, "missing_act"));
    }

    @Test
    void sameRequestIdIsScopedPerUser() throws Exception {
        assertEquals(201, post(1101, 1001, "shared_key").getStatusCode().value());
        assertEquals(201, post(1102, 1001, "shared_key").getStatusCode().value());
        assertEquals(3, remaining(1001));
        assertEquals(2, registrationCount(1001));
        assertInvariant();
    }

    @Test
    void requestIdCaseIsDistinct() throws Exception {
        assertEquals(201, post(1101, 1001, "Ab").getStatusCode().value());
        assertEquals(201, post(1101, 1005, "ab").getStatusCode().value());
        assertEquals(4, remaining(1001));
        assertEquals(4, remaining(1005));
    }

    @Test
    void fiftyUsersCompeteForTenSeats() throws Exception {
        List<ResponseEntity<String>> responses = concurrentPosts(50, (index) -> new Call(5100L + index, 1002L, "seat_" + index));
        long created = responses.stream().filter(response -> response.getStatusCode().value() == 201).count();
        long soldOut = responses.stream().filter(response -> "SOLD_OUT".equals(codeQuiet(response))).count();
        assertEquals(10, created);
        assertEquals(40, soldOut);
        assertEquals(10, registrationCount(1002));
        assertEquals(0, remaining(1002));
        assertInvariant();
        assertNoDuplicateLeak(responses);
    }

    @Test
    void sameUserSameRequestIdTwentyTimesConverges() throws Exception {
        List<ResponseEntity<String>> responses = concurrentPosts(20, (index) -> new Call(1101L, 1007L, "same_key"));
        long created = responses.stream().filter(response -> response.getStatusCode().value() == 201).count();
        long replay = responses.stream().filter(response -> response.getStatusCode().value() == 200).count();
        assertEquals(1, created);
        assertEquals(19, replay);
        Set<Long> ids = new HashSet<>();
        for (ResponseEntity<String> response : responses) {
            ids.add(data(response).get("id").asLong());
        }
        assertEquals(1, ids.size());
        assertEquals(1, registrationCount(1007));
        assertEquals(4, remaining(1007));
        assertInvariant();
    }

    @Test
    void sameUserDifferentRequestIdsAllowOnlyOneSuccess() throws Exception {
        List<ResponseEntity<String>> responses = concurrentPosts(20, (index) -> new Call(1101L, 1006L, "diff_" + index));
        long created = responses.stream().filter(response -> response.getStatusCode().value() == 201).count();
        long already = responses.stream().filter(response -> "ALREADY_REGISTERED".equals(codeQuiet(response))).count();
        assertEquals(1, created);
        assertEquals(19, already);
        assertEquals(1, registrationCount(1006));
        assertEquals(4, remaining(1006));
        assertInvariant();
        assertNoDuplicateLeak(responses);
    }

    @Test
    void sameRequestIdAcrossActivitiesAllowsOnlyOneBinding() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ResponseEntity<String>> first = pool.submit(() -> {
                start.await();
                return post(1101, 1005, "cross_key");
            });
            Future<ResponseEntity<String>> second = pool.submit(() -> {
                start.await();
                return post(1101, 1001, "cross_key");
            });
            start.countDown();
            ResponseEntity<String> left = first.get(20, TimeUnit.SECONDS);
            ResponseEntity<String> right = second.get(20, TimeUnit.SECONDS);
            List<ResponseEntity<String>> responses = List.of(left, right);
            long created = responses.stream().filter(response -> response.getStatusCode().value() == 201).count();
            long conflict = responses.stream().filter(response -> "IDEMPOTENCY_CONFLICT".equals(codeQuiet(response))).count();
            assertEquals(1, created);
            assertEquals(1, conflict);
            assertEquals(1, countRequest(1101, "cross_key"));
            int deducted = (remaining(1005) == 4 ? 1 : 0) + (remaining(1001) == 4 ? 1 : 0);
            assertEquals(1, deducted);
            assertEquals(9, remaining(1005) + remaining(1001));
            assertInvariant();
            assertNoDuplicateLeak(responses);
        } finally {
            pool.shutdownNow();
        }
    }

    private List<ResponseEntity<String>> concurrentPosts(int count, java.util.function.IntFunction<Call> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                Call call = calls.apply(index);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start timeout");
                    }
                    return post(call.userId(), call.activityId(), call.requestId());
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<ResponseEntity<String>> responses = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    private ResponseEntity<String> post(long userId, long activityId, String requestId) {
        return postRaw(Long.toString(userId), activityId, requestId);
    }

    private ResponseEntity<String> postRaw(String userId, long activityId, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (userId != null) {
            headers.set("X-User-Id", userId);
        }
        String body = "{\"activityId\":" + activityId + ",\"requestId\":\"" + requestId + "\"}";
        return rest.exchange("/api/registrations", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> exchangeGet(String path, String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private JsonNode data(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody()).get("data");
    }

    private String code(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody()).get("code").asText();
    }

    private String codeQuiet(ResponseEntity<String> response) {
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.has("code") ? root.get("code").asText() : "";
        } catch (Exception ex) {
            return "";
        }
    }

    private int remaining(long activityId) {
        return jdbc.queryForObject("SELECT remaining_quota FROM activity WHERE id = ?", Integer.class, activityId);
    }

    private int registrationCount(long activityId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM registration WHERE activity_id = ?", Integer.class, activityId);
    }

    private int countRequest(long userId, String requestId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE user_id = ? AND request_id = ?",
                Integer.class, userId, requestId);
    }

    private void assertInvariant() {
        Integer broken = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT a.id
                    FROM activity a
                    LEFT JOIN registration r ON r.activity_id = a.id
                    GROUP BY a.id, a.total_quota, a.remaining_quota
                    HAVING a.remaining_quota < 0
                        OR a.total_quota <> a.remaining_quota + COUNT(r.id)
                ) broken
                """, Integer.class);
        Integer duplicateActivity = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT user_id, activity_id
                    FROM registration
                    GROUP BY user_id, activity_id
                    HAVING COUNT(*) > 1
                ) duplicate_activity
                """, Integer.class);
        Integer duplicateRequest = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT user_id, request_id
                    FROM registration
                    GROUP BY user_id, request_id
                    HAVING COUNT(DISTINCT activity_id) > 1
                ) duplicate_request
                """, Integer.class);
        assertEquals(0, broken);
        assertEquals(0, duplicateActivity);
        assertEquals(0, duplicateRequest);
    }

    private void assertNoDuplicateLeak(List<ResponseEntity<String>> responses) {
        for (ResponseEntity<String> response : responses) {
            assertNotNull(response.getBody());
            assertFalse(response.getBody().contains("Duplicate entry"));
        }
    }

    private record Call(long userId, long activityId, String requestId) {
    }
}
