package com.achintya.campusqueue.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.achintya.campusqueue.support.PostgresIntegrationTestSupport;
import com.achintya.campusqueue.user.UserEntity;
import com.achintya.campusqueue.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;

class AuthApiIT extends PostgresIntegrationTestSupport {

    private static final String REGISTER_BODY = """
            {
              "email": " Student@Example.com ",
              "password": "strong-pass-123",
              "role": "STUDENT"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registrationHashesPasswordAndLoginUsesNormalizedEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("student@example.com"))
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        UserEntity stored = userRepository.findByEmail("student@example.com").orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo("strong-pass-123");
        assertThat(passwordEncoder.matches("strong-pass-123", stored.getPasswordHash())).isTrue();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": " STUDENT@example.com ",
                                  "password": "strong-pass-123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("student@example.com"));
    }

    @Test
    void duplicateEmailIsCaseInsensitive() throws Exception {
        registerStudent();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "STUDENT@EXAMPLE.COM",
                                  "password": "another-pass-123",
                                  "role": "STUDENT"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));
    }

    @Test
    void invalidCredentialsReturnSafeUnauthorizedProblem() throws Exception {
        registerStudent();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "student@example.com",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Email or password is incorrect"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void missingMalformedTamperedAndExpiredTokensReturnSafeJson() throws Exception {
        assertUnauthorized(null);
        assertUnauthorized("not-a-jwt");

        String validToken = registerStudent();
        assertUnauthorized(tamperSignature(validToken));

        Instant now = Instant.now();
        JwtClaimsSet expiredClaims = JwtClaimsSet.builder()
                .issuer("campus-queue")
                .subject(UUID.randomUUID().toString())
                .issuedAt(now.minus(2, ChronoUnit.HOURS))
                .expiresAt(now.minus(1, ChronoUnit.HOURS))
                .claim("role", "STUDENT")
                .build();
        String expiredToken = jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), expiredClaims))
                .getTokenValue();
        assertUnauthorized(expiredToken);

        JwtClaimsSet wrongIssuerClaims = JwtClaimsSet.builder()
                .issuer("different-service")
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("role", "STUDENT")
                .build();
        String wrongIssuerToken = jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), wrongIssuerClaims))
                .getTokenValue();
        assertUnauthorized(wrongIssuerToken);
    }

    @Test
    void studentTokenCannotUseOrganizerRoutes() throws Exception {
        String token = registerStudent();

        mockMvc.perform(post("/api/v1/organizer/workshops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private String registerStudent() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private void assertUnauthorized(String token) throws Exception {
        var request = get("/api/v1/private-probe");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/private-probe"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    private String tamperSignature(String token) {
        String[] segments = token.split("\\.");
        char replacement = segments[2].charAt(0) == 'a' ? 'b' : 'a';
        segments[2] = replacement + segments[2].substring(1);
        return String.join(".", segments);
    }
}
