package dev.achu.campusqueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public final class CampusQueueServiceTest {
    private int passed;

    public static void main(String[] args) throws Exception {
        var suite = new CampusQueueServiceTest();
        suite.registersUntilCapacityThenUsesFifoWaitlist();
        suite.rejectsDuplicateRegistrationAndWrongRoles();
        suite.cancellationPromotesEarliestWaitlistedStudent();
        suite.concurrentRegistrationNeverExceedsCapacity();
        suite.cancellingWaitlistedStudentPreservesQueueOrder();
        suite.cancellationWithoutWaitlistFreesSeatForSubsequentRegistration();
        suite.rejectsCancellingUnregisteredStudent();
        suite.enforcesWorkshopLifecycleTransitions();
        suite.enforcesWorkshopOwnership();
        suite.validatesUserAndWorkshopInputs();
        suite.listPublishedWorkshopsFiltersCancelledAndUnpublished();
        suite.concurrentCancellationAndRegistrationMaintainsConsistency();
        System.out.printf("CampusQueueServiceTest: %d tests passed%n", suite.passed);
    }

    private void registersUntilCapacityThenUsesFifoWaitlist() {
        var fixture = new Fixture(1);
        var first = fixture.service.register(fixture.workshopId, fixture.student("one@example.com").id());
        var second = fixture.service.register(fixture.workshopId, fixture.student("two@example.com").id());

        equal(RegistrationStatus.CONFIRMED, first.status(), "first student should be confirmed");
        equal(0, first.waitlistPosition(), "confirmed registration has no waitlist position");
        equal(RegistrationStatus.WAITLISTED, second.status(), "second student should be waitlisted");
        equal(1, second.waitlistPosition(), "first waitlisted student has position one");
        passed++;
    }

    private void rejectsDuplicateRegistrationAndWrongRoles() {
        var fixture = new Fixture(2);
        var student = fixture.student("student@example.com");
        fixture.service.register(fixture.workshopId, student.id());

        throwsMessage(
            () -> fixture.service.register(fixture.workshopId, student.id()),
            "Student is already registered for this workshop"
        );
        throwsMessage(
            () -> fixture.service.createWorkshop(student.id(), "Not allowed", 5),
            "Only organizers can create workshops"
        );
        throwsMessage(
            () -> fixture.service.register(fixture.workshopId, fixture.organizerId),
            "Only students can register for workshops"
        );
        passed++;
    }

    private void cancellationPromotesEarliestWaitlistedStudent() {
        var fixture = new Fixture(1);
        var confirmed = fixture.student("confirmed@example.com");
        var firstWaiting = fixture.student("first-waiting@example.com");
        var secondWaiting = fixture.student("second-waiting@example.com");
        fixture.service.register(fixture.workshopId, confirmed.id());
        fixture.service.register(fixture.workshopId, firstWaiting.id());
        fixture.service.register(fixture.workshopId, secondWaiting.id());

        var result = fixture.service.cancelRegistration(fixture.workshopId, confirmed.id());
        var view = fixture.service.getWorkshop(fixture.workshopId);

        equal(firstWaiting.id(), result.promotedStudentId(), "earliest waiting student should be promoted");
        equal(List.of(firstWaiting.id()), view.confirmedStudentIds(), "promoted student should fill the seat");
        equal(List.of(secondWaiting.id()), view.waitlistedStudentIds(), "remaining queue order should stay stable");
        passed++;
    }

    private void concurrentRegistrationNeverExceedsCapacity() throws Exception {
        var fixture = new Fixture(3);
        var students = new ArrayList<User>();
        for (int index = 0; index < 20; index++) students.add(fixture.student("student" + index + "@example.com"));

        var tasks = students.stream()
            .<Callable<RegistrationResult>>map(student -> () -> fixture.service.register(fixture.workshopId, student.id()))
            .toList();
        List<RegistrationResult> results;
        var executor = Executors.newFixedThreadPool(8);
        try {
            results = executor.invokeAll(tasks).stream().map(future -> {
                try { return future.get(); }
                catch (Exception exception) { throw new RuntimeException(exception); }
            }).toList();
        } finally {
            executor.shutdownNow();
        }

        equal(3L, results.stream().filter(result -> result.status() == RegistrationStatus.CONFIRMED).count(), "capacity should cap confirmations");
        equal(17L, results.stream().filter(result -> result.status() == RegistrationStatus.WAITLISTED).count(), "remaining students should be waitlisted");
        Set<Integer> positions = new HashSet<>(results.stream().map(RegistrationResult::waitlistPosition).filter(position -> position > 0).toList());
        equal(17, positions.size(), "waitlist positions should be unique");
        equal(3, fixture.service.getWorkshop(fixture.workshopId).confirmedStudentIds().size(), "stored confirmations should equal capacity");
        passed++;
    }

    private void cancellingWaitlistedStudentPreservesQueueOrder() {
        var fixture = new Fixture(1);
        var confirmed = fixture.student("confirmed-user@example.com");
        var wait1 = fixture.student("wait1@example.com");
        var wait2 = fixture.student("wait2@example.com");
        var wait3 = fixture.student("wait3@example.com");

        fixture.service.register(fixture.workshopId, confirmed.id());
        fixture.service.register(fixture.workshopId, wait1.id());
        fixture.service.register(fixture.workshopId, wait2.id());
        fixture.service.register(fixture.workshopId, wait3.id());

        // Cancel middle waitlisted student
        var cancelResult = fixture.service.cancelRegistration(fixture.workshopId, wait2.id());
        equal(null, cancelResult.promotedStudentId(), "waitlist cancellation should not promote anyone");

        var view = fixture.service.getWorkshop(fixture.workshopId);
        equal(List.of(confirmed.id()), view.confirmedStudentIds(), "confirmed student remains unchanged");
        equal(List.of(wait1.id(), wait3.id()), view.waitlistedStudentIds(), "waitlist order remains stable without cancelled student");
        passed++;
    }

    private void cancellationWithoutWaitlistFreesSeatForSubsequentRegistration() {
        var fixture = new Fixture(1);
        var first = fixture.student("first-seat@example.com");
        var second = fixture.student("second-seat@example.com");

        fixture.service.register(fixture.workshopId, first.id());
        var cancelResult = fixture.service.cancelRegistration(fixture.workshopId, first.id());
        equal(null, cancelResult.promotedStudentId(), "no promotion when waitlist is empty");

        var viewAfterCancel = fixture.service.getWorkshop(fixture.workshopId);
        equal(0, viewAfterCancel.confirmedStudentIds().size(), "seat is freed");

        var regResult = fixture.service.register(fixture.workshopId, second.id());
        equal(RegistrationStatus.CONFIRMED, regResult.status(), "subsequent student should now get confirmed");
        equal(0, regResult.waitlistPosition(), "confirmed student has position 0");
        passed++;
    }

    private void rejectsCancellingUnregisteredStudent() {
        var fixture = new Fixture(2);
        var registered = fixture.student("reg@example.com");
        var unregistered = fixture.student("unreg@example.com");

        fixture.service.register(fixture.workshopId, registered.id());

        throwsMessage(
            () -> fixture.service.cancelRegistration(fixture.workshopId, unregistered.id()),
            "Registration was not found"
        );

        // Cancel registered student once
        fixture.service.cancelRegistration(fixture.workshopId, registered.id());

        // Second cancellation attempt should fail
        throwsMessage(
            () -> fixture.service.cancelRegistration(fixture.workshopId, registered.id()),
            "Registration was not found"
        );
        passed++;
    }

    private void enforcesWorkshopLifecycleTransitions() {
        var service = new CampusQueueService();
        var organizer = service.registerUser("org-life@example.com", Role.ORGANIZER);
        var student = service.registerUser("stud-life@example.com", Role.STUDENT);

        // Unpublished workshop
        var workshop = service.createWorkshop(organizer.id(), "Draft Workshop", 2);
        throwsMessage(
            () -> service.register(workshop.id(), student.id()),
            "Workshop is not published"
        );

        // Cancelled workshop
        service.cancelWorkshop(organizer.id(), workshop.id());
        throwsMessage(
            () -> service.register(workshop.id(), student.id()),
            "Workshop is not published"
        );
        throwsMessage(
            () -> service.publishWorkshop(organizer.id(), workshop.id()),
            "Cancelled workshop cannot be published"
        );
        passed++;
    }

    private void enforcesWorkshopOwnership() {
        var service = new CampusQueueService();
        var owner = service.registerUser("owner@example.com", Role.ORGANIZER);
        var imposter = service.registerUser("imposter@example.com", Role.ORGANIZER);

        var workshop = service.createWorkshop(owner.id(), "Protected Workshop", 2);

        throwsMessage(
            () -> service.publishWorkshop(imposter.id(), workshop.id()),
            "Only the workshop organizer can change it"
        );
        throwsMessage(
            () -> service.cancelWorkshop(imposter.id(), workshop.id()),
            "Only the workshop organizer can change it"
        );

        // Owner can publish
        var published = service.publishWorkshop(owner.id(), workshop.id());
        equal(true, published.published(), "owner can publish");

        // Owner can cancel
        var cancelled = service.cancelWorkshop(owner.id(), workshop.id());
        equal(true, cancelled.cancelled(), "owner can cancel");
        passed++;
    }

    private void validatesUserAndWorkshopInputs() {
        var service = new CampusQueueService();

        // Email format validations
        throwsMessage(() -> service.registerUser(null, Role.STUDENT), "Email is invalid");
        throwsMessage(() -> service.registerUser("", Role.STUDENT), "Email is invalid");
        throwsMessage(() -> service.registerUser("not-an-email", Role.STUDENT), "Email is invalid");
        throwsMessage(() -> service.registerUser("user@domain", Role.STUDENT), "Email is invalid");
        throwsMessage(() -> service.registerUser("user@.com", Role.STUDENT), "Email is invalid");

        // Role required
        throwsMessage(() -> service.registerUser("valid@example.com", null), "Role is required");

        // Case-insensitive duplicate email
        service.registerUser("Unique@Example.Com", Role.STUDENT);
        throwsMessage(
            () -> service.registerUser("unique@example.com", Role.STUDENT),
            "Email is already registered"
        );

        var org = service.registerUser("valid-org@example.com", Role.ORGANIZER);

        // Workshop title validation
        throwsMessage(() -> service.createWorkshop(org.id(), null, 5), "Workshop title is required");
        throwsMessage(() -> service.createWorkshop(org.id(), "   ", 5), "Workshop title is required");

        // Capacity validation
        throwsMessage(() -> service.createWorkshop(org.id(), "Title", 0), "Capacity must be at least one");
        throwsMessage(() -> service.createWorkshop(org.id(), "Title", -5), "Capacity must be at least one");

        // Workshop not found
        throwsMessage(() -> service.getWorkshop(UUID.randomUUID()), "Workshop was not found");
        passed++;
    }

    private void listPublishedWorkshopsFiltersCancelledAndUnpublished() {
        var service = new CampusQueueService();
        var org = service.registerUser("lister-org@example.com", Role.ORGANIZER);

        var draft = service.createWorkshop(org.id(), "Alpha Draft", 2);
        var active1 = service.createWorkshop(org.id(), "Zeta Active", 2);
        var active2 = service.createWorkshop(org.id(), "Beta Active", 2);
        var cancelled = service.createWorkshop(org.id(), "Gamma Cancelled", 2);

        service.publishWorkshop(org.id(), active1.id());
        service.publishWorkshop(org.id(), active2.id());
        service.publishWorkshop(org.id(), cancelled.id());
        service.cancelWorkshop(org.id(), cancelled.id());

        var listed = service.listPublishedWorkshops();
        equal(2, listed.size(), "should only return active published workshops");
        equal("Beta Active", listed.get(0).title(), "should sort alphabetically by title");
        equal("Zeta Active", listed.get(1).title(), "second workshop in alphabetical order");
        passed++;
    }

    private void concurrentCancellationAndRegistrationMaintainsConsistency() throws Exception {
        var fixture = new Fixture(5);
        var initialStudents = new ArrayList<User>();
        for (int i = 0; i < 10; i++) {
            var student = fixture.student("initial-" + i + "@example.com");
            initialStudents.add(student);
            fixture.service.register(fixture.workshopId, student.id());
        }

        // Concurrently cancel 3 confirmed students while 10 new students attempt registration
        var executor = Executors.newFixedThreadPool(8);
        var tasks = new ArrayList<Callable<Void>>();

        for (int i = 0; i < 3; i++) {
            final var studentToCancel = initialStudents.get(i);
            tasks.add(() -> {
                fixture.service.cancelRegistration(fixture.workshopId, studentToCancel.id());
                return null;
            });
        }

        for (int i = 0; i < 10; i++) {
            final var newStudent = fixture.student("concurrent-new-" + i + "@example.com");
            tasks.add(() -> {
                fixture.service.register(fixture.workshopId, newStudent.id());
                return null;
            });
        }

        Collections.shuffle(tasks);
        try {
            executor.invokeAll(tasks);
        } finally {
            executor.shutdownNow();
        }

        var view = fixture.service.getWorkshop(fixture.workshopId);
        equal(5, view.confirmedStudentIds().size(), "confirmed seats should exactly match capacity");

        var allStudentIds = new HashSet<UUID>(view.confirmedStudentIds());
        allStudentIds.addAll(view.waitlistedStudentIds());
        equal(view.confirmedStudentIds().size() + view.waitlistedStudentIds().size(), allStudentIds.size(), "no duplicates across confirmed and waitlist");
        passed++;
    }

    private static final class Fixture {
        final CampusQueueService service = new CampusQueueService();
        final UUID organizerId;
        final UUID workshopId;

        Fixture(int capacity) {
            organizerId = service.registerUser("organizer@example.com", Role.ORGANIZER).id();
            workshopId = service.createWorkshop(organizerId, "Backend Systems Workshop", capacity).id();
            service.publishWorkshop(organizerId, workshopId);
        }

        User student(String email) {
            return service.registerUser(email, Role.STUDENT);
        }
    }

    private static void equal(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void throwsMessage(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            equal(message, exception.getMessage(), "exception message");
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException: " + message);
    }
}
