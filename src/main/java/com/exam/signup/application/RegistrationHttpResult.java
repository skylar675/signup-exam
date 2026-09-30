package com.exam.signup.application;

import com.exam.signup.persistence.RegistrationRow;

public record RegistrationHttpResult(int httpStatus, RegistrationRow registration) {
}
