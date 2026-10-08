package com.achintya.campusqueue.common.error;

import java.util.List;

public class RequestValidationException extends RuntimeException {

    private final List<FieldErrorDetail> fieldErrors;

    public RequestValidationException(List<FieldErrorDetail> fieldErrors) {
        super("Request validation failed");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<FieldErrorDetail> getFieldErrors() {
        return fieldErrors;
    }
}
