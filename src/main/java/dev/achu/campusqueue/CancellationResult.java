package dev.achu.campusqueue;

import java.util.UUID;

public record CancellationResult(UUID workshopId, UUID studentId, UUID promotedStudentId) {}

