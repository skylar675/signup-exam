package com.exam.signup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6399",
        "spring.data.redis.timeout=300ms",
        "spring.data.redis.connect-timeout=200ms"
})
class RedisDownTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void resetState() throws Exception {
        DbScripts.reset(dataSource);
    }

    @Test
    void activityDetailAndRegistrationWorkWhenRedisIsUnreachable() throws Exception {
        ResponseEntity<String> detail = rest.getForEntity("/api/activities/1001", String.class);
        assertEquals(200, detail.getStatusCode().value());
        JsonNode activity = objectMapper.readTree(detail.getBody()).get("data");
        assertEquals("Java 实战分享", activity.get("title").asText());
        assertEquals(5, activity.get("remainingQuota").asInt());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "1101");
        ResponseEntity<String> created = rest.exchange(
                "/api/registrations",
                HttpMethod.POST,
                new HttpEntity<>("{\"activityId\":1001,\"requestId\":\"redis_down_key\"}", headers),
                String.class);
        assertEquals(201, created.getStatusCode().value());
        assertEquals(4, jdbc.queryForObject("SELECT remaining_quota FROM activity WHERE id = 1001", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE request_id = 'redis_down_key'", Integer.class));
    }
}
