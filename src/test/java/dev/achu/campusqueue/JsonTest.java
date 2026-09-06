package dev.achu.campusqueue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class JsonTest {
    private int passed;

    public static void main(String[] args) {
        var suite = new JsonTest();
        suite.parsesFlatObjectWithDifferentTypes();
        suite.handlesEscapedQuotesAndNewlines();
        suite.rejectsNonObjectOrMalformedJson();
        suite.serializesUsersAndEscapesSpecialCharacters();
        suite.serializesWorkshopsAndRegistrations();
        suite.serializesErrorsWithConsistentStructure();
        System.out.printf("JsonTest: %d tests passed%n", suite.passed);
    }

    private void parsesFlatObjectWithDifferentTypes() {
        var json = "{\"name\":\"Alice\",\"age\":25,\"active\":true,\"balance\":-10,\"notes\":null}";
        var parsed = Json.parseObject(json);
        equal("Alice", parsed.get("name"), "name string");
        equal("25", parsed.get("age"), "number string");
        equal("true", parsed.get("active"), "boolean string");
        equal("-10", parsed.get("balance"), "negative number string");
        equal("null", parsed.get("notes"), "null string");
        passed++;
    }

    private void handlesEscapedQuotesAndNewlines() {
        var json = "{\"escaped\":\"Hello \\\"World\\\"\\nLine 2\\\\Done\"}";
        var parsed = Json.parseObject(json);
        equal("Hello \"World\"\nLine 2\\Done", parsed.get("escaped"), "unescaped content");
        passed++;
    }

    private void rejectsNonObjectOrMalformedJson() {
        throwsMessage(() -> Json.parseObject("not-json"), "Request body must be a JSON object");
        throwsMessage(() -> Json.parseObject("[1, 2, 3]"), "Request body must be a JSON object");
        throwsMessage(() -> Json.parseObject(null), "Request body must be a JSON object");
        throwsMessage(() -> Json.parseObject("   "), "Request body must be a JSON object");
        passed++;
    }

    private void serializesUsersAndEscapesSpecialCharacters() {
        var id = UUID.randomUUID();
        var user = new User(id, "test\"user\"@example.com", Role.STUDENT);
        var serialized = Json.user(user);
        contains(serialized, id.toString(), "should contain id");
        contains(serialized, "test\\\"user\\\"@example.com", "should quote internal quotes");
        contains(serialized, "STUDENT", "should contain role");
        passed++;
    }

    private void serializesWorkshopsAndRegistrations() {
        var workshopId = UUID.randomUUID();
        var organizerId = UUID.randomUUID();
        var studentId = UUID.randomUUID();
        var waitlistedId = UUID.randomUUID();

        var view = new WorkshopView(workshopId, organizerId, "Systems\nWorkshop", 2, true, false, List.of(studentId), List.of(waitlistedId));
        var workshopJson = Json.workshop(view);
        contains(workshopJson, "Systems\\nWorkshop", "newlines should be escaped");
        contains(workshopJson, studentId.toString(), "confirmed student id should be included");
        contains(workshopJson, waitlistedId.toString(), "waitlisted student id should be included");

        var reg = new RegistrationResult(workshopId, studentId, RegistrationStatus.CONFIRMED, 0);
        var regJson = Json.registration(reg);
        contains(regJson, "CONFIRMED", "registration status");
        contains(regJson, "\"waitlistPosition\":0", "waitlist position");

        var cancel = new CancellationResult(workshopId, studentId, waitlistedId);
        var cancelJson = Json.cancellation(cancel);
        contains(cancelJson, waitlistedId.toString(), "promoted student id");
        passed++;
    }

    private void serializesErrorsWithConsistentStructure() {
        var err = Json.error("VALIDATION_ERROR", "Field \"title\" is required");
        contains(err, "\"error\":", "should nest in error property");
        contains(err, "\"code\":\"VALIDATION_ERROR\"", "should contain error code");
        contains(err, "Field \\\"title\\\" is required", "message quotes should be escaped");
        passed++;
    }

    private static void equal(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void contains(String value, String expected, String message) {
        if (!value.contains(expected)) {
            throw new AssertionError(message + ": expected to contain '" + expected + "', but was: " + value);
        }
    }

    private static void throwsMessage(Runnable action, String expectedMessage) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            equal(expectedMessage, exception.getMessage(), "exception message");
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException: " + expectedMessage);
    }
}
