package com.achintya.campusqueue.auth.dto;

import com.achintya.campusqueue.user.UserEntity;
import com.achintya.campusqueue.user.UserRole;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, UserRole role, Instant createdAt) {

    public static UserResponse from(UserEntity user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
