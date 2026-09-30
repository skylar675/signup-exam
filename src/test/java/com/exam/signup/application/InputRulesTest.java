package com.exam.signup.application;

import com.exam.signup.domain.BusinessException;
import com.exam.signup.domain.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputRulesTest {

    @Test
    void pageDefaultsWhenAbsentAndRejectsIllegalValues() {
        PageParams defaults = InputRules.parsePage(null, null);
        assertEquals(1, defaults.page());
        assertEquals(10, defaults.size());
        assertEquals(ErrorCode.INVALID_ARGUMENT, assertThrows(BusinessException.class,
                () -> InputRules.parsePage("0", "10")).errorCode());
        assertEquals(ErrorCode.INVALID_ARGUMENT, assertThrows(BusinessException.class,
                () -> InputRules.parsePage("1", "0")).errorCode());
        assertEquals(ErrorCode.INVALID_ARGUMENT, assertThrows(BusinessException.class,
                () -> InputRules.parsePage("1", "101")).errorCode());
        assertEquals(ErrorCode.INVALID_ARGUMENT, assertThrows(BusinessException.class,
                () -> InputRules.parsePage("1.5", "10")).errorCode());
        assertEquals(100, InputRules.parsePage("2", "100").size());
    }

    @Test
    void requestIdIsCaseSensitiveAscii() {
        assertEquals("Ab", InputRules.parseRequestId("Ab"));
        assertEquals("ab", InputRules.parseRequestId("ab"));
        assertEquals(ErrorCode.INVALID_ARGUMENT, assertThrows(BusinessException.class,
                () -> InputRules.parseRequestId("bad key")).errorCode());
        assertEquals(ErrorCode.INVALID_ARGUMENT, assertThrows(BusinessException.class,
                () -> InputRules.parseRequestId("")).errorCode());
    }

    @Test
    void userIdMustBePositiveInteger() {
        assertEquals(1101L, InputRules.parseUserId("1101"));
        assertEquals(ErrorCode.INVALID_ARGUMENT, assertThrows(BusinessException.class,
                () -> InputRules.parseUserId(null)).errorCode());
        assertEquals(ErrorCode.INVALID_ARGUMENT, assertThrows(BusinessException.class,
                () -> InputRules.parseUserId("0")).errorCode());
        assertEquals(ErrorCode.INVALID_ARGUMENT, assertThrows(BusinessException.class,
                () -> InputRules.parseUserId("abc")).errorCode());
    }

    @Test
    void malformedRegistrationIdIsNotAPositiveId() {
        assertNull(InputRules.parseRegistrationIdOrNull("abc"));
        assertNull(InputRules.parseRegistrationIdOrNull("0"));
        assertEquals(12L, InputRules.parseRegistrationIdOrNull("12"));
    }
}
