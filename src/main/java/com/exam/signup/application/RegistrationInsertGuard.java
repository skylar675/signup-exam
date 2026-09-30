package com.exam.signup.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RegistrationInsertGuard {

    private final boolean failBeforeInsert;

    public RegistrationInsertGuard(@Value("${signup.fault.before-insert:false}") boolean failBeforeInsert) {
        this.failBeforeInsert = failBeforeInsert;
    }

    public void beforeInsert() {
        if (failBeforeInsert) {
            throw new IllegalStateException("injected fault before registration insert");
        }
    }
}
