package com.exam.signup.cache;

public record CacheLookup(Status status, ActivityCacheValue value) {

    public enum Status {
        HIT, MISS, ERROR
    }

    public static CacheLookup hit(ActivityCacheValue value) {
        return new CacheLookup(Status.HIT, value);
    }

    public static CacheLookup miss() {
        return new CacheLookup(Status.MISS, null);
    }

    public static CacheLookup error() {
        return new CacheLookup(Status.ERROR, null);
    }
}
