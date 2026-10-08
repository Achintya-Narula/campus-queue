package com.achintya.campusqueue.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

class PublicWorkshopApiIT extends PostgresIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void searchIsCaseInsensitiveAndNeverReturnsDraftOrCancelledWorkshops() throws Exception {
        String organizer = registerOrganizer();
        UUID java = createWorkshop(organizer, "Java Concurrency", "Locks and threads", true);
        UUID database = createWorkshop(organizer, "Backend Reliability", "PostgreSQL transactions", true);
        UUID python = createWorkshop(organizer, "Python APIs", "FastAPI foundations", true);
        createWorkshop(organizer, "Java Draft", "Should remain hidden", false);
        UUID cancelled = createWorkshop(organizer, "Cancelled Event", "PostgreSQL hidden", true);
        cancelWorkshop(organizer, cancelled);

        mockMvc.perform(get("/api/v1/workshops").param("q", "jAvA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(java.toString()));

        mockMvc.perform(get("/api/v1/workshops").param("q", "POSTGRESQL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(database.toString()));

        mockMvc.perform(get("/api/v1/workshops").param("q", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.containsInAnyOrder(
                        java.toString(), database.toString(), python.toString())));
    }

    @Test
    void paginationMetadataIsStableAndBoundsAreValidated() throws Exception {
        String organizer = registerOrganizer();
        createWorkshop(organizer, "Workshop A", "Published", true);
        createWorkshop(organizer, "Workshop B", "Published", true);
        createWorkshop(organizer, "Workshop C", "Published", true);

        mockMvc.perform(get("/api/v1/workshops").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        assertInvalidPage("-1", "20", "page");
        assertInvalidPage("0", "0", "size");
        assertInvalidPage("0", "51", "size");
    }

    @Test
    void openApiAndSwaggerArePublicAndDescribeBearerProtectedRoutes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("CampusQueue API"))
                .andExpect(jsonPath("$.info.version").value("v2"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/register']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workshops']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workshops/{workshopId}/registrations']").exists());

        int swaggerStatus = mockMvc.perform(get("/swagger-ui.html"))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(swaggerStatus).isBetween(200, 399);
    }

    private void assertInvalidPage(String page, String size, String field) throws Exception {
        mockMvc.perform(get("/api/v1/workshops").param("page", page).param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == '" + field + "')]").exists());
    }

    private UUID createWorkshop(
            String organizerToken, String title, String description, boolean publish) throws Exception {
        String response = mockMvc.perform(post("/api/v1/organizer/workshops")
                        .header("Authorization", bearer(organizerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WorkshopInput(title, description, 20))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID workshopId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        if (publish) {
            mockMvc.perform(post("/api/v1/organizer/workshops/{id}/publish", workshopId)
                            .header("Authorization", bearer(organizerToken)))
                    .andExpect(status().isOk());
        }
        return workshopId;
    }

    private void cancelWorkshop(String organizerToken, UUID workshopId) throws Exception {
        mockMvc.perform(post("/api/v1/organizer/workshops/{id}/cancel", workshopId)
                        .header("Authorization", bearer(organizerToken)))
                .andExpect(status().isOk());
    }

    private String registerOrganizer() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegistrationInput(
                                "organizer@example.com", "strong-pass-123", "ORGANIZER"))))
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
