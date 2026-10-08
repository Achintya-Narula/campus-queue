package com.achintya.campusqueue.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.achintya.campusqueue.registration.RegistrationRepository;
import com.achintya.campusqueue.registration.RegistrationStatus;
import com.achintya.campusqueue.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class OrganizerManagementApiIT extends PostgresIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Test
    void ownerCanIncreaseCapacityButCannotReduceBelowConfirmedCount() throws Exception {
        String organizer = register("organizer@example.com", "ORGANIZER");
        String first = register("first@example.com", "STUDENT");
        String second = register("second@example.com", "STUDENT");
        UUID workshopId = createPublishedWorkshop(organizer, 2);
        enroll(first, workshopId);
        enroll(second, workshopId);

        updateCapacity(organizer, workshopId, 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(3));

        updateCapacity(organizer, workshopId, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAPACITY_BELOW_CONFIRMED"));
    }

    @Test
    void rosterIsOwnerOnlyAndSeparatesConfirmedFromFifoWaitlist() throws Exception {
        String owner = register("owner@example.com", "ORGANIZER");
        String otherOrganizer = register("other@example.com", "ORGANIZER");
        String first = register("first@example.com", "STUDENT");
        String second = register("second@example.com", "STUDENT");
        String third = register("third@example.com", "STUDENT");
        String fourth = register("fourth@example.com", "STUDENT");
        UUID workshopId = createPublishedWorkshop(owner, 2);
        enroll(first, workshopId);
        enroll(second, workshopId);
        enroll(third, workshopId);
        enroll(fourth, workshopId);

        mockMvc.perform(get("/api/v1/organizer/workshops/{id}/roster", workshopId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed.length()").value(2))
                .andExpect(jsonPath("$.confirmed[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.waitlisted.length()").value(2))
                .andExpect(jsonPath("$.waitlisted[0].studentEmail").value("third@example.com"))
                .andExpect(jsonPath("$.waitlisted[0].waitlistSequence").value(1))
                .andExpect(jsonPath("$.waitlisted[1].studentEmail").value("fourth@example.com"))
                .andExpect(jsonPath("$.waitlisted[1].waitlistSequence").value(2))
                .andExpect(jsonPath("$.confirmed[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.waitlisted[0].passwordHash").doesNotExist());

        String forbiddenBody = mockMvc.perform(get("/api/v1/organizer/workshops/{id}/roster", workshopId)
                        .header("Authorization", bearer(otherOrganizer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSHOP_NOT_OWNED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(forbiddenBody).doesNotContain("first@example.com", "third@example.com");
    }

    @Test
    void cancellingWorkshopAtomicallyCancelsRegistrationsAndClosesLifecycle() throws Exception {
        String organizer = register("organizer@example.com", "ORGANIZER");
        String confirmed = register("confirmed@example.com", "STUDENT");
        String waiter = register("waiter@example.com", "STUDENT");
        String newStudent = register("new@example.com", "STUDENT");
        UUID workshopId = createPublishedWorkshop(organizer, 1);
        enroll(confirmed, workshopId);
        enroll(waiter, workshopId);

        mockMvc.perform(post("/api/v1/organizer/workshops/{id}/cancel", workshopId)
                        .header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(registrationRepository.countByWorkshop_IdAndStatus(
                        workshopId, RegistrationStatus.CANCELLED))
                .isEqualTo(2);
        assertThat(registrationRepository.countByWorkshop_IdAndStatus(
                        workshopId, RegistrationStatus.CONFIRMED))
                .isZero();
        assertThat(registrationRepository.countByWorkshop_IdAndStatus(
                        workshopId, RegistrationStatus.WAITLISTED))
                .isZero();

        mockMvc.perform(get("/api/v1/workshops/{id}", workshopId))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/workshops/{id}/registrations", workshopId)
                        .header("Authorization", bearer(newStudent)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKSHOP_NOT_OPEN"));
        mockMvc.perform(post("/api/v1/organizer/workshops/{id}/cancel", workshopId)
                        .header("Authorization", bearer(organizer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_WORKSHOP_TRANSITION"));
        mockMvc.perform(post("/api/v1/organizer/workshops/{id}/publish", workshopId)
                        .header("Authorization", bearer(organizer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_WORKSHOP_TRANSITION"));
    }

    private org.springframework.test.web.servlet.ResultActions updateCapacity(
            String token, UUID workshopId, int capacity) throws Exception {
        return mockMvc.perform(put("/api/v1/organizer/workshops/{id}", workshopId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new WorkshopInput("Managed Workshop", "Organizer controls", capacity))));
    }

    private void enroll(String studentToken, UUID workshopId) throws Exception {
        mockMvc.perform(post("/api/v1/workshops/{id}/registrations", workshopId)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isCreated());
    }

    private UUID createPublishedWorkshop(String organizerToken, int capacity) throws Exception {
        String response = mockMvc.perform(post("/api/v1/organizer/workshops")
                        .header("Authorization", bearer(organizerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WorkshopInput("Managed Workshop", "Organizer controls", capacity))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID workshopId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        mockMvc.perform(post("/api/v1/organizer/workshops/{id}/publish", workshopId)
                        .header("Authorization", bearer(organizerToken)))
                .andExpect(status().isOk());
        return workshopId;
    }

    private String register(String email, String role) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegistrationInput(email, "strong-pass-123", role))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record RegistrationInput(String email, String password, String role) {
    }

    private record WorkshopInput(String title, String description, int capacity) {
    }
}
