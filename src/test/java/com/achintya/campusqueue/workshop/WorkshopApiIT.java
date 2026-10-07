package com.achintya.campusqueue.workshop;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.achintya.campusqueue.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class WorkshopApiIT extends PostgresIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void organizerCanCreateEditPublishAndListOwnedWorkshop() throws Exception {
        String organizerToken = register("organizer@example.com", "ORGANIZER");
        UUID workshopId = createDraft(organizerToken, "Java Concurrency", "Hands-on locks", 20);

        mockMvc.perform(put("/api/v1/organizer/workshops/{id}", workshopId)
                        .header("Authorization", bearer(organizerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workshopJson("Java Concurrency Lab", "Locks and transactions", 24)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Java Concurrency Lab"))
                .andExpect(jsonPath("$.description").value("Locks and transactions"))
                .andExpect(jsonPath("$.capacity").value(24))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(post("/api/v1/organizer/workshops/{id}/publish", workshopId)
                        .header("Authorization", bearer(organizerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workshopId.toString()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/v1/organizer/workshops")
                        .header("Authorization", bearer(organizerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(workshopId.toString()));
    }

    @Test
    void onlyOwnerCanManageWorkshopAndTransitionsAreChecked() throws Exception {
        String ownerToken = register("owner@example.com", "ORGANIZER");
        String otherToken = register("other@example.com", "ORGANIZER");
        String studentToken = register("student@example.com", "STUDENT");
        UUID workshopId = createDraft(ownerToken, "Backend APIs", "REST workshop", 15);

        mockMvc.perform(post("/api/v1/organizer/workshops")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workshopJson("Forbidden", "Student cannot create", 10)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(put("/api/v1/organizer/workshops/{id}", workshopId)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workshopJson("Hijacked", "Wrong organizer", 5)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSHOP_NOT_OWNED"));

        mockMvc.perform(post("/api/v1/organizer/workshops/{id}/publish", workshopId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/organizer/workshops/{id}/publish", workshopId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_WORKSHOP_TRANSITION"));

        mockMvc.perform(post("/api/v1/organizer/workshops/{id}/publish", UUID.randomUUID())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSHOP_NOT_FOUND"));
    }

    @Test
    void publicReadsExposePublishedWorkshopsButHideDrafts() throws Exception {
        String organizerToken = register("organizer@example.com", "ORGANIZER");
        UUID publishedId = createDraft(organizerToken, "Published Workshop", "Visible", 10);
        UUID draftId = createDraft(organizerToken, "Draft Workshop", "Hidden", 10);

        mockMvc.perform(post("/api/v1/organizer/workshops/{id}/publish", publishedId)
                        .header("Authorization", bearer(organizerToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/workshops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(publishedId.toString()))
                .andExpect(jsonPath("$.content[0].title").value("Published Workshop"));

        mockMvc.perform(get("/api/v1/workshops/{id}", publishedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publishedId.toString()));

        mockMvc.perform(get("/api/v1/workshops/{id}", draftId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSHOP_NOT_FOUND"));
    }

    @Test
    void workshopInputValidationNamesEveryInvalidField() throws Exception {
        String token = register("organizer@example.com", "ORGANIZER");

        assertInvalidWorkshop(token, workshopJson(" ", "Description", 10), "title");
        assertInvalidWorkshop(token, workshopJson("x".repeat(121), "Description", 10), "title");
        assertInvalidWorkshop(token, workshopJson("Valid title", "x".repeat(2001), 10), "description");
        assertInvalidWorkshop(token, workshopJson("Valid title", "Description", 0), "capacity");
    }

    private UUID createDraft(String token, String title, String description, int capacity) throws Exception {
        String response = mockMvc.perform(post("/api/v1/organizer/workshops")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workshopJson(title, description, capacity)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String register(String email, String role) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Registration(email, "strong-pass-123", role))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private void assertInvalidWorkshop(String token, String body, String field) throws Exception {
        mockMvc.perform(post("/api/v1/organizer/workshops")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == '" + field + "')]").exists());
    }

    private String workshopJson(String title, String description, int capacity) throws Exception {
        JsonNode body = objectMapper.valueToTree(new WorkshopInput(title, description, capacity));
        return objectMapper.writeValueAsString(body);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Registration(String email, String password, String role) {
    }

    private record WorkshopInput(String title, String description, int capacity) {
    }
}
