package com.achintya.campusqueue.workshop;

import com.achintya.campusqueue.workshop.dto.CreateWorkshopRequest;
import com.achintya.campusqueue.workshop.dto.UpdateWorkshopRequest;
import com.achintya.campusqueue.workshop.dto.WorkshopResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizer/workshops")
public class OrganizerWorkshopController {

    private final WorkshopService workshopService;

    public OrganizerWorkshopController(WorkshopService workshopService) {
        this.workshopService = workshopService;
    }

    @PostMapping
    ResponseEntity<WorkshopResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateWorkshopRequest request) {
        WorkshopResponse response = workshopService.create(subject(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/workshops/" + response.id())).body(response);
    }

    @PutMapping("/{workshopId}")
    WorkshopResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workshopId,
            @Valid @RequestBody UpdateWorkshopRequest request) {
        return workshopService.update(subject(jwt), workshopId, request);
    }

    @PostMapping("/{workshopId}/publish")
    WorkshopResponse publish(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workshopId) {
        return workshopService.publish(subject(jwt), workshopId);
    }

    @GetMapping
    Page<WorkshopResponse> listOwned(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return workshopService.listOwned(subject(jwt), pageable);
    }

    private UUID subject(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
