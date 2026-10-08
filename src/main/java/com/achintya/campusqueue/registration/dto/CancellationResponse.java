package com.achintya.campusqueue.registration.dto;

import com.achintya.campusqueue.registration.RegistrationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

public record CancellationResponse(
        UUID cancelledRegistrationId,
        RegistrationStatus previousStatus,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID promotedRegistrationId) {
}
