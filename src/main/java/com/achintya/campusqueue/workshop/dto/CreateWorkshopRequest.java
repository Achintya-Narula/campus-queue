package com.achintya.campusqueue.workshop.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkshopRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 2000) String description,
        @Min(1) int capacity) {

    public CreateWorkshopRequest {
        title = title == null ? null : title.trim();
        description = description == null ? null : description.trim();
    }
}
