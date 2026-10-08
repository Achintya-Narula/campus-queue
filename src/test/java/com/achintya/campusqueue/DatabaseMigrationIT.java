package com.achintya.campusqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.achintya.campusqueue.support.PostgresIntegrationTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class DatabaseMigrationIT extends PostgresIntegrationTestSupport {

    @Test
    void migratesTablesConstraintsAndIndexes() {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = 'public'",
                String.class);

        assertThat(tables).contains("app_user", "workshop", "registration");

        UUID organizerId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into app_user (id, email, password_hash, role, created_at) "
                        + "values (?, ?, ?, 'ORGANIZER', now())",
                organizerId,
                "organizer@example.com",
                "hash");
        jdbcTemplate.update(
                "insert into app_user (id, email, password_hash, role, created_at) "
                        + "values (?, ?, ?, 'STUDENT', now())",
                studentId,
                "student@example.com",
                "hash");
        jdbcTemplate.update(
                "insert into workshop "
                        + "(id, organizer_id, title, description, capacity, status, "
                        + "next_waitlist_sequence, created_at, updated_at) "
                        + "values (?, ?, ?, '', 1, 'PUBLISHED', 0, now(), now())",
                workshopId,
                organizerId,
                "Backend Workshop");
        jdbcTemplate.update(
                "insert into registration "
                        + "(id, workshop_id, student_id, status, created_at, updated_at) "
                        + "values (?, ?, ?, 'CONFIRMED', now(), now())",
                UUID.randomUUID(),
                workshopId,
                studentId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "insert into registration "
                                + "(id, workshop_id, student_id, status, created_at, updated_at) "
                                + "values (?, ?, ?, 'CONFIRMED', now(), now())",
                        UUID.randomUUID(),
                        workshopId,
                        studentId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_registration_workshop_student");

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "update workshop set capacity = 0 where id = ?", workshopId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_workshop_capacity_positive");
    }
}
