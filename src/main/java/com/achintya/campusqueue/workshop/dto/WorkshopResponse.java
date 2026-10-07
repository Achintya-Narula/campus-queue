package com.achintya.campusqueue.workshop.dto;

import com.achintya.campusqueue.workshop.WorkshopEntity;
import com.achintya.campusqueue.workshop.WorkshopStatus;
import java.time.Instant;
import java.util.UUID;

public record WorkshopResponse(
        UUID id,
        UUID organizerId,
        String title,
        String description,
        int capacity,
        WorkshopStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static WorkshopResponse from(WorkshopEntity workshop) {
        return new WorkshopResponse(
                workshop.getId(),
                workshop.getOrganizer().getId(),
                workshop.getTitle(),
                workshop.getDescription(),
                workshop.getCapacity(),
                workshop.getStatus(),
                workshop.getCreatedAt(),
                workshop.getUpdatedAt());
    }
}
