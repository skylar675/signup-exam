package com.exam.signup.application;

import com.exam.signup.persistence.RegistrationRow;

public record EnrollResult(boolean created, RegistrationRow registration) {

    public static EnrollResult created(RegistrationRow registration) {
        return new EnrollResult(true, registration);
    }

    public static EnrollResult replay(RegistrationRow registration) {
        return new EnrollResult(false, registration);
    }
}
