package com.nutritrack.common;

import java.time.Instant;
import java.util.List;

/** Error body returned by every failing API call. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }
}
