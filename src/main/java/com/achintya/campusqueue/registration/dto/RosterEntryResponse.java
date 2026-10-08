package com.achintya.campusqueue.registration.dto;

import com.achintya.campusqueue.registration.RegistrationEntity;
import com.achintya.campusqueue.registration.RegistrationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

public record RosterEntryResponse(
        UUID registrationId,
        UUID studentId,
        String studentEmail,
        RegistrationStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long waitlistSequence,
        Instant registeredAt) {

    public static RosterEntryResponse from(RegistrationEntity registration) {
        return new RosterEntryResponse(
                registration.getId(),
                registration.getStudent().getId(),
                registration.getStudent().getEmail(),
                registration.getStatus(),
                registration.getWaitlistSequence(),
                registration.getCreatedAt());
    }
}
