package com.achintya.campusqueue.common.error;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldErrorDetail> fieldErrors) {

    public static ApiError of(
            Instant timestamp,
            int status,
            String code,
            String message,
            String path) {
        return new ApiError(timestamp, status, code, message, path, List.of());
    }
}
