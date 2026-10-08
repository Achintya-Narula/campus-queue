package com.achintya.campusqueue.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.achintya.campusqueue.registration.dto.RegistrationResponse;
import com.achintya.campusqueue.support.PostgresIntegrationTestSupport;
import com.achintya.campusqueue.user.UserEntity;
import com.achintya.campusqueue.user.UserRepository;
import com.achintya.campusqueue.user.UserRole;
import com.achintya.campusqueue.workshop.WorkshopEntity;
import com.achintya.campusqueue.workshop.WorkshopRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RegistrationConcurrencyIT extends PostgresIntegrationTestSupport {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private WorkshopRepository workshopRepository;

    @Autowired
    private UserRepository userRepository;

    @RepeatedTest(5)
    void twentyRegistrationsAcrossEightThreadsNeverOverbookCapacityThree() throws Exception {
        UserEntity organizer = createUser("organizer@example.com", UserRole.ORGANIZER);
        WorkshopEntity workshop = createPublishedWorkshop(organizer, 3);
        List<UserEntity> students = createStudents("parallel", 20);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            List<Future<RegistrationResponse>> futures = students.stream()
                    .map(student -> executor.submit(() -> {
                        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                        return registrationService.register(student.getId(), workshop.getId());
                    }))
                    .toList();
            start.countDown();

            List<RegistrationResponse> results = new ArrayList<>();
            for (Future<RegistrationResponse> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(results).filteredOn(result -> result.status() == RegistrationStatus.CONFIRMED)
                    .hasSize(3);
            assertThat(results).filteredOn(result -> result.status() == RegistrationStatus.WAITLISTED)
                    .hasSize(17);
            List<RegistrationEntity> waitlisted = registrationRepository
                    .findByWorkshop_IdAndStatusOrderByWaitlistSequenceAsc(
                            workshop.getId(), RegistrationStatus.WAITLISTED);
            assertThat(waitlisted).extracting(RegistrationEntity::getWaitlistSequence)
                    .containsExactlyElementsOf(IntStream.rangeClosed(1, 17)
                            .mapToObj(value -> (long) value)
                            .toList());
            assertThat(waitlisted).extracting(RegistrationEntity::getWaitlistSequence)
                    .doesNotHaveDuplicates();
            assertThat(jdbcTemplate.queryForObject(
                            "select count(*) from registration where workshop_id = ?",
                            Long.class,
                            workshop.getId()))
                    .isEqualTo(20L);
            assertThat(jdbcTemplate.queryForObject(
                            "select count(distinct student_id) from registration where workshop_id = ?",
                            Long.class,
                            workshop.getId()))
                    .isEqualTo(20L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentCancellationAndEnrollmentPreserveCapacityAndEarliestWaiter() throws Exception {
        UserEntity organizer = createUser("organizer@example.com", UserRole.ORGANIZER);
        WorkshopEntity workshop = createPublishedWorkshop(organizer, 3);
        List<UserEntity> initialStudents = createStudents("initial", 5);
        for (UserEntity student : initialStudents) {
            registrationService.register(student.getId(), workshop.getId());
        }
        UserEntity newcomer = createUser("newcomer@example.com", UserRole.STUDENT);
        RegistrationEntity earliestWaiter = registrationRepository
                .findFirstByWorkshop_IdAndStatusOrderByWaitlistSequenceAsc(
                        workshop.getId(), RegistrationStatus.WAITLISTED)
                .orElseThrow();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> cancellation = executor.submit(() -> {
                await(start);
                return registrationService.cancel(initialStudents.get(0).getId(), workshop.getId());
            });
            Future<?> enrollment = executor.submit(() -> {
                await(start);
                return registrationService.register(newcomer.getId(), workshop.getId());
            });
            start.countDown();
            cancellation.get(30, TimeUnit.SECONDS);
            enrollment.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(registrationRepository.countByWorkshop_IdAndStatus(
                        workshop.getId(), RegistrationStatus.CONFIRMED))
                .isEqualTo(3);
        assertThat(registrationRepository.findById(earliestWaiter.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from registration where workshop_id = ?",
                        Long.class,
                        workshop.getId()))
                .isEqualTo(6L);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(distinct student_id) from registration where workshop_id = ?",
                        Long.class,
                        workshop.getId()))
                .isEqualTo(6L);
        assertThat(registrationRepository.findByWorkshop_IdAndStudent_Id(
                        workshop.getId(), newcomer.getId()))
                .isPresent();
    }

    private UserEntity createUser(String email, UserRole role) {
        return userRepository.saveAndFlush(
                new UserEntity(email, "test-only-password-hash", role, Instant.now()));
    }

    private List<UserEntity> createStudents(String prefix, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new UserEntity(
                        prefix + index + "@example.com",
                        "test-only-password-hash",
                        UserRole.STUDENT,
                        Instant.now()))
                .map(userRepository::saveAndFlush)
                .toList();
    }

    private WorkshopEntity createPublishedWorkshop(UserEntity organizer, int capacity) {
        WorkshopEntity workshop = new WorkshopEntity(
                organizer, "Concurrency Workshop", "Database row locking", capacity, Instant.now());
        workshop.publish(Instant.now());
        return workshopRepository.saveAndFlush(workshop);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent operation did not receive the start signal");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent operation was interrupted", exception);
        }
    }
}
