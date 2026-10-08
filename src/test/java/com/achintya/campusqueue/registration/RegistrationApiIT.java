package com.achintya.campusqueue.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.achintya.campusqueue.support.PostgresIntegrationTestSupport;
import com.achintya.campusqueue.workshop.WorkshopEntity;
import com.achintya.campusqueue.workshop.WorkshopRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class RegistrationApiIT extends PostgresIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private WorkshopRepository workshopRepository;

    @Test
    void enrollmentConfirmsUntilCapacityThenUsesFifoWaitlist() throws Exception {
        String organizer = register("organizer@example.com", "ORGANIZER");
        String firstStudent = register("first@example.com", "STUDENT");
        String secondStudent = register("second@example.com", "STUDENT");
        UUID workshopId = createWorkshop(organizer, 1, true);

        enroll(firstStudent, workshopId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.queuePosition").doesNotExist());

        enroll(secondStudent, workshopId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITLISTED"))
                .andExpect(jsonPath("$.queuePosition").value(1));
    }

    @Test
    void studentViewsAreTokenScopedAndIgnoreClientSuppliedIdentity() throws Exception {
        String organizer = register("organizer@example.com", "ORGANIZER");
        AuthenticatedUser first = registerUser("first@example.com", "STUDENT");
        AuthenticatedUser second = registerUser("second@example.com", "STUDENT");
        UUID workshopId = createWorkshop(organizer, 2, true);

        String response = mockMvc.perform(post("/api/v1/workshops/{id}/registrations", workshopId)
                        .header("Authorization", bearer(first.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"" + second.id() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID registrationId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        RegistrationEntity stored = registrationRepository.findById(registrationId).orElseThrow();
        assertThat(stored.getStudent().getId()).isEqualTo(first.id());

        mockMvc.perform(get("/api/v1/me/registrations")
                        .header("Authorization", bearer(first.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(registrationId.toString()));

        mockMvc.perform(get("/api/v1/me/registrations")
                        .header("Authorization", bearer(second.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void duplicateRoleAndWorkshopLifecycleConflictsAreRejected() throws Exception {
        String organizer = register("organizer@example.com", "ORGANIZER");
        String student = register("student@example.com", "STUDENT");
        UUID publishedId = createWorkshop(organizer, 2, true);
        UUID draftId = createWorkshop(organizer, 2, false);

        enroll(student, publishedId).andExpect(status().isCreated());
        enroll(student, publishedId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REGISTERED"));

        enroll(organizer, publishedId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        enroll(student, draftId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKSHOP_NOT_OPEN"));

        WorkshopEntity cancelled = workshopRepository.findById(publishedId).orElseThrow();
        cancelled.cancel(Instant.now());
        workshopRepository.saveAndFlush(cancelled);

        String anotherStudent = register("another@example.com", "STUDENT");
        enroll(anotherStudent, publishedId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKSHOP_NOT_OPEN"));
    }

    @Test
    void cancelledRegistrationReactivatesSameRowWithANewSequence() throws Exception {
        String organizer = register("organizer@example.com", "ORGANIZER");
        String confirmedStudent = register("confirmed@example.com", "STUDENT");
        AuthenticatedUser returningStudent = registerUser("returning@example.com", "STUDENT");
        String activeWaiter = register("waiter@example.com", "STUDENT");
        UUID workshopId = createWorkshop(organizer, 1, true);

        enroll(confirmedStudent, workshopId).andExpect(status().isCreated());
        String firstAttempt = enroll(returningStudent.token(), workshopId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.queuePosition").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID originalRegistrationId = UUID.fromString(objectMapper.readTree(firstAttempt).get("id").asText());

        RegistrationEntity cancelled = registrationRepository.findById(originalRegistrationId).orElseThrow();
        assertThat(cancelled.getWaitlistSequence()).isEqualTo(1L);
        cancelled.cancel(Instant.now());
        registrationRepository.saveAndFlush(cancelled);

        enroll(activeWaiter, workshopId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.queuePosition").value(1));

        enroll(returningStudent.token(), workshopId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(originalRegistrationId.toString()))
                .andExpect(jsonPath("$.status").value("WAITLISTED"))
                .andExpect(jsonPath("$.queuePosition").value(2));

        RegistrationEntity reactivated = registrationRepository.findById(originalRegistrationId).orElseThrow();
        assertThat(reactivated.getWaitlistSequence()).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from registration where workshop_id = ? and student_id = ?",
                        Long.class,
                        workshopId,
                        returningStudent.id()))
                .isEqualTo(1L);
    }

    private org.springframework.test.web.servlet.ResultActions enroll(String token, UUID workshopId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/workshops/{id}/registrations", workshopId)
                .header("Authorization", bearer(token)));
    }

    private UUID createWorkshop(String organizerToken, int capacity, boolean publish) throws Exception {
        String response = mockMvc.perform(post("/api/v1/organizer/workshops")
                        .header("Authorization", bearer(organizerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WorkshopInput("Database Concurrency", "PostgreSQL locking", capacity))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        if (publish) {
            mockMvc.perform(post("/api/v1/organizer/workshops/{id}/publish", id)
                            .header("Authorization", bearer(organizerToken)))
                    .andExpect(status().isOk());
        }
        return id;
    }

    private String register(String email, String role) throws Exception {
        return registerUser(email, role).token();
    }

    private AuthenticatedUser registerUser(String email, String role) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegistrationInput(
                                email, "strong-pass-123", role))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var json = objectMapper.readTree(response);
        return new AuthenticatedUser(
                UUID.fromString(json.get("user").get("id").asText()),
                json.get("token").asText());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record RegistrationInput(String email, String password, String role) {
    }

    private record WorkshopInput(String title, String description, int capacity) {
    }

    private record AuthenticatedUser(UUID id, String token) {
    }
}
