package com.achintya.campusqueue.registration.dto;

import java.util.List;

public record WorkshopRosterResponse(
        List<RosterEntryResponse> confirmed,
        List<RosterEntryResponse> waitlisted) {
}
