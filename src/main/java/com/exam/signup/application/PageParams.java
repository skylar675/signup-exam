package com.exam.signup.application;

public record PageParams(int page, int size) {

    public long offset() {
        return (long) (page - 1) * size;
    }
}
