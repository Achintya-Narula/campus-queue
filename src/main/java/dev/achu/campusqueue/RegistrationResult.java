package dev.achu.campusqueue;

import java.util.UUID;

public record RegistrationResult(
    UUID workshopId,
    UUID studentId,
    RegistrationStatus status,
    int waitlistPosition
) {}

