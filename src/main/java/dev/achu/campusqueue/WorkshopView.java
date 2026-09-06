package dev.achu.campusqueue;

import java.util.List;
import java.util.UUID;

public record WorkshopView(
    UUID id,
    UUID organizerId,
    String title,
    int capacity,
    boolean published,
    boolean cancelled,
    List<UUID> confirmedStudentIds,
    List<UUID> waitlistedStudentIds
) {}

