package com.achintya.campusqueue.registration;

import com.achintya.campusqueue.registration.dto.RegistrationResponse;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/api/v1/workshops/{workshopId}/registrations")
    ResponseEntity<RegistrationResponse> register(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workshopId) {
        RegistrationResponse response = registrationService.register(subject(jwt), workshopId);
        URI location = URI.create("/api/v1/me/registrations/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/api/v1/me/registrations")
    Page<RegistrationResponse> listForStudent(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return registrationService.listForStudent(subject(jwt), pageable);
    }

    private UUID subject(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
