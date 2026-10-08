package com.achintya.campusqueue.registration.dto;

import com.achintya.campusqueue.registration.RegistrationEntity;
import com.achintya.campusqueue.registration.RegistrationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

public record RegistrationResponse(
        UUID id,
        UUID workshopId,
        RegistrationStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long queuePosition,
        Instant createdAt,
        Instant updatedAt) {

    public static RegistrationResponse from(RegistrationEntity registration, Long queuePosition) {
        return new RegistrationResponse(
                registration.getId(),
                registration.getWorkshop().getId(),
                registration.getStatus(),
                queuePosition,
                registration.getCreatedAt(),
                registration.getUpdatedAt());
    }
}
