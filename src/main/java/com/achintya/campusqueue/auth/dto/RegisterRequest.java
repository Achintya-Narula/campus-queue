package com.achintya.campusqueue.auth.dto;

import com.achintya.campusqueue.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull UserRole role) {

    public RegisterRequest {
        email = email == null ? null : email.trim();
    }
}
