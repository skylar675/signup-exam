package com.exam.signup;

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
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "signup.fault.before-insert=true")
class RegistrationFaultTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void resetState() throws Exception {
        DbScripts.reset(dataSource);
    }

    @Test
    void quotaRollsBackWhenInsertIsAborted() {
        int beforeRemaining = jdbc.queryForObject("SELECT remaining_quota FROM activity WHERE id = 1001", Integer.class);
        int beforeCount = jdbc.queryForObject("SELECT COUNT(*) FROM registration", Integer.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "1101");
        ResponseEntity<String> response = rest.exchange(
                "/api/registrations",
                HttpMethod.POST,
                new HttpEntity<>("{\"activityId\":1001,\"requestId\":\"fault_key\"}", headers),
                String.class);

        assertEquals(500, response.getStatusCode().value());
        assertFalse(response.getBody().contains("Duplicate entry"));
        assertFalse(response.getBody().contains("SQL"));
        int afterRemaining = jdbc.queryForObject("SELECT remaining_quota FROM activity WHERE id = 1001", Integer.class);
        int afterCount = jdbc.queryForObject("SELECT COUNT(*) FROM registration", Integer.class);
        assertEquals(beforeRemaining, afterRemaining);
        assertEquals(beforeCount, afterCount);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE request_id = 'fault_key'", Integer.class));
    }
}
