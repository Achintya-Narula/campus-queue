package com.achintya.campusqueue.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.achintya.campusqueue.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class CancellationApiIT extends PostgresIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Test
    void cancellingConfirmedRegistrationPromotesExactlyTheEarliestWaiter() throws Exception {
        String organizer = register("organizer@example.com", "ORGANIZER");
        Student confirmed = registerStudent("confirmed@example.com");
        Student firstWaiter = registerStudent("first@example.com");
        Student secondWaiter = registerStudent("second@example.com");
        Student thirdWaiter = registerStudent("third@example.com");
        UUID workshopId = createPublishedWorkshop(organizer, 1);

        UUID confirmedId = enroll(confirmed.token(), workshopId);
        UUID firstId = enroll(firstWaiter.token(), workshopId);
        UUID secondId = enroll(secondWaiter.token(), workshopId);
        UUID thirdId = enroll(thirdWaiter.token(), workshopId);

        mockMvc.perform(delete("/api/v1/workshops/{id}/registrations/me", workshopId)
                        .header("Authorization", bearer(confirmed.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledRegistrationId").value(confirmedId.toString()))
                .andExpect(jsonPath("$.previousStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.promotedRegistrationId").value(firstId.toString()));

        assertThat(registrationRepository.findById(confirmedId).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.CANCELLED);
        RegistrationEntity promoted = registrationRepository.findById(firstId).orElseThrow();
        assertThat(promoted.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(promoted.getWaitlistSequence()).isNull();
        assertThat(registrationRepository.findById(secondId).orElseThrow().getWaitlistSequence())
                .isEqualTo(2L);
        assertThat(registrationRepository.findById(thirdId).orElseThrow().getWaitlistSequence())
                .isEqualTo(3L);
    }

    @Test
    void waitlistCancellationUsesTokenIdentityAndRepeatedCancellationIsStable() throws Exception {
        String organizer = register("organizer@example.com", "ORGANIZER");
        Student confirmed = registerStudent("confirmed@example.com");
        Student waiter = registerStudent("waiter@example.com");
        UUID workshopId = createPublishedWorkshop(organizer, 1);

        UUID confirmedId = enroll(confirmed.token(), workshopId);
        UUID waiterId = enroll(waiter.token(), workshopId);

        mockMvc.perform(delete("/api/v1/workshops/{id}/registrations/me", workshopId)
                        .header("Authorization", bearer(waiter.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"" + confirmed.id() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledRegistrationId").value(waiterId.toString()))
                .andExpect(jsonPath("$.previousStatus").value("WAITLISTED"))
                .andExpect(jsonPath("$.promotedRegistrationId").doesNotExist());

        assertThat(registrationRepository.findById(confirmedId).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registrationRepository.findById(waiterId).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.CANCELLED);

        mockMvc.perform(delete("/api/v1/workshops/{id}/registrations/me", workshopId)
                        .header("Authorization", bearer(waiter.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_NOT_ACTIVE"));

        assertThat(registrationRepository.countByWorkshop_IdAndStatus(
                        workshopId, RegistrationStatus.CONFIRMED))
                .isEqualTo(1);
    }

    private UUID enroll(String studentToken, UUID workshopId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/workshops/{id}/registrations", workshopId)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createPublishedWorkshop(String organizerToken, int capacity) throws Exception {
        String response = mockMvc.perform(post("/api/v1/organizer/workshops")
                        .header("Authorization", bearer(organizerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WorkshopInput("FIFO Workshop", "Cancellation and promotion", capacity))))
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

    private Student registerStudent(String email) throws Exception {
        String response = registerResponse(email, "STUDENT");
        var json = objectMapper.readTree(response);
        return new Student(
                UUID.fromString(json.get("user").get("id").asText()),
                json.get("token").asText());
    }

    private String register(String email, String role) throws Exception {
        return objectMapper.readTree(registerResponse(email, role)).get("token").asText();
    }

    private String registerResponse(String email, String role) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegistrationInput(email, "strong-pass-123", role))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Student(UUID id, String token) {
    }

    private record RegistrationInput(String email, String password, String role) {
    }

    private record WorkshopInput(String title, String description, int capacity) {
    }
}
