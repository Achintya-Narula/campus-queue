package dev.achu.campusqueue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Pattern;

public final class CampusQueueHttpServerTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private int passed;

    public static void main(String[] args) throws Exception {
        var suite = new CampusQueueHttpServerTest();
        suite.exposesWorkshopAndRegistrationFlowOverHttp();
        suite.returnsStructuredValidationErrors();
        suite.handlesCancellationAndPromotionOverHttp();
        suite.handlesWorkshopDetailsAndCancellationOverHttp();
        suite.returnsNotFoundForUnknownRoutesAndInvalidMethods();
        suite.returnsBadRequestForMalformedJsonAndInvalidUuids();
        suite.enforcesSecurityHeadersOnResponses();
        System.out.printf("CampusQueueHttpServerTest: %d tests passed%n", suite.passed);
    }

    private void exposesWorkshopAndRegistrationFlowOverHttp() throws Exception {
        var server = new CampusQueueHttpServer(new CampusQueueService(), new InetSocketAddress("127.0.0.1", 0));
        server.start();
        try {
            var organizer = post(server, "/api/users", "{\"email\":\"organizer@example.com\",\"role\":\"ORGANIZER\"}");
            equal(201, organizer.statusCode(), "organizer creation status");
            var organizerId = field(organizer.body(), "id");

            var workshop = post(server, "/api/workshops", "{\"organizerId\":\"" + organizerId + "\",\"title\":\"Backend Workshop\",\"capacity\":1}");
            equal(201, workshop.statusCode(), "workshop creation status");
            var workshopId = field(workshop.body(), "id");
            equal(200, post(server, "/api/workshops/" + workshopId + "/publish", "{\"organizerId\":\"" + organizerId + "\"}").statusCode(), "publish status");

            var firstStudentId = field(post(server, "/api/users", "{\"email\":\"first@example.com\",\"role\":\"STUDENT\"}").body(), "id");
            var secondStudentId = field(post(server, "/api/users", "{\"email\":\"second@example.com\",\"role\":\"STUDENT\"}").body(), "id");
            var firstRegistration = post(server, "/api/workshops/" + workshopId + "/registrations", "{\"studentId\":\"" + firstStudentId + "\"}");
            var secondRegistration = post(server, "/api/workshops/" + workshopId + "/registrations", "{\"studentId\":\"" + secondStudentId + "\"}");

            equal("CONFIRMED", field(firstRegistration.body(), "status"), "first registration status");
            equal("WAITLISTED", field(secondRegistration.body(), "status"), "second registration status");
            equal("1", numberField(secondRegistration.body(), "waitlistPosition"), "waitlist position");

            var listed = get(server, "/api/workshops");
            equal(200, listed.statusCode(), "workshop list status");
            contains(listed.body(), "Backend Workshop", "workshop list should contain title");
            contains(listed.body(), secondStudentId, "workshop list should contain waitlisted student");
            passed++;
        } finally {
            server.stop();
        }
    }

    private void returnsStructuredValidationErrors() throws Exception {
        var server = new CampusQueueHttpServer(new CampusQueueService(), new InetSocketAddress("127.0.0.1", 0));
        server.start();
        try {
            var response = post(server, "/api/users", "{\"email\":\"not-an-email\",\"role\":\"STUDENT\"}");
            equal(400, response.statusCode(), "invalid email status");
            equal("VALIDATION_ERROR", field(response.body(), "code"), "error code");
            equal("Email is invalid", field(response.body(), "message"), "error message");
            passed++;
        } finally {
            server.stop();
        }
    }

    private void handlesCancellationAndPromotionOverHttp() throws Exception {
        var server = new CampusQueueHttpServer(new CampusQueueService(), new InetSocketAddress("127.0.0.1", 0));
        server.start();
        try {
            var orgId = field(post(server, "/api/users", "{\"email\":\"org-cancel@example.com\",\"role\":\"ORGANIZER\"}").body(), "id");
            var wId = field(post(server, "/api/workshops", "{\"organizerId\":\"" + orgId + "\",\"title\":\"Cancel Test Workshop\",\"capacity\":1}").body(), "id");
            post(server, "/api/workshops/" + wId + "/publish", "{\"organizerId\":\"" + orgId + "\"}");

            var s1 = field(post(server, "/api/users", "{\"email\":\"student1-del@example.com\",\"role\":\"STUDENT\"}").body(), "id");
            var s2 = field(post(server, "/api/users", "{\"email\":\"student2-del@example.com\",\"role\":\"STUDENT\"}").body(), "id");

            post(server, "/api/workshops/" + wId + "/registrations", "{\"studentId\":\"" + s1 + "\"}");
            post(server, "/api/workshops/" + wId + "/registrations", "{\"studentId\":\"" + s2 + "\"}");

            // DELETE registration triggers promotion
            var deleteRes = delete(server, "/api/workshops/" + wId + "/registrations", "{\"studentId\":\"" + s1 + "\"}");
            equal(200, deleteRes.statusCode(), "delete registration status");
            equal(s2, field(deleteRes.body(), "promotedStudentId"), "second student promoted");

            // Verify via GET workshop
            var workshopRes = get(server, "/api/workshops/" + wId);
            equal(200, workshopRes.statusCode(), "get workshop status");
            contains(workshopRes.body(), s2, "promoted student is now in workshop state");
            passed++;
        } finally {
            server.stop();
        }
    }

    private void handlesWorkshopDetailsAndCancellationOverHttp() throws Exception {
        var server = new CampusQueueHttpServer(new CampusQueueService(), new InetSocketAddress("127.0.0.1", 0));
        server.start();
        try {
            var orgId = field(post(server, "/api/users", "{\"email\":\"owner-ws@example.com\",\"role\":\"ORGANIZER\"}").body(), "id");
            var wId = field(post(server, "/api/workshops", "{\"organizerId\":\"" + orgId + "\",\"title\":\"Detail Workshop\",\"capacity\":3}").body(), "id");
            post(server, "/api/workshops/" + wId + "/publish", "{\"organizerId\":\"" + orgId + "\"}");

            var details = get(server, "/api/workshops/" + wId);
            equal(200, details.statusCode(), "get workshop detail status");
            equal("Detail Workshop", field(details.body(), "title"), "workshop title");
            contains(details.body(), "\"published\":true", "workshop published state");

            // Cancel workshop
            var cancelRes = post(server, "/api/workshops/" + wId + "/cancel", "{\"organizerId\":\"" + orgId + "\"}");
            equal(200, cancelRes.statusCode(), "cancel workshop status");
            contains(cancelRes.body(), "\"cancelled\":true", "workshop cancelled state");

            // Cancelled workshop should be excluded from listing
            var listRes = get(server, "/api/workshops");
            if (listRes.body().contains("Detail Workshop")) {
                throw new AssertionError("Cancelled workshop should not be listed in /api/workshops");
            }
            passed++;
        } finally {
            server.stop();
        }
    }

    private void returnsNotFoundForUnknownRoutesAndInvalidMethods() throws Exception {
        var server = new CampusQueueHttpServer(new CampusQueueService(), new InetSocketAddress("127.0.0.1", 0));
        server.start();
        try {
            // Unsupported method on /api/users
            var getU = get(server, "/api/users");
            equal(404, getU.statusCode(), "GET /api/users should be 404");
            equal("NOT_FOUND", field(getU.body(), "code"), "error code NOT_FOUND");

            // Unknown subpath under /api/workshops/:id
            var validId = UUID.randomUUID();
            var badAction = post(server, "/api/workshops/" + validId + "/unknown-action", "{}");
            equal(404, badAction.statusCode(), "unknown action status");
            equal("NOT_FOUND", field(badAction.body(), "code"), "error code NOT_FOUND");

            // Non-existent route
            var badRoute = get(server, "/api/non-existent");
            equal(404, badRoute.statusCode(), "unknown route status");
            passed++;
        } finally {
            server.stop();
        }
    }

    private void returnsBadRequestForMalformedJsonAndInvalidUuids() throws Exception {
        var server = new CampusQueueHttpServer(new CampusQueueService(), new InetSocketAddress("127.0.0.1", 0));
        server.start();
        try {
            // Malformed body
            var badJson = post(server, "/api/users", "this is not json");
            equal(400, badJson.statusCode(), "malformed json status");
            equal("VALIDATION_ERROR", field(badJson.body(), "code"), "code");

            // Invalid UUID in request body
            var badUuid = post(server, "/api/workshops", "{\"organizerId\":\"not-a-uuid\",\"title\":\"Title\",\"capacity\":2}");
            equal(400, badUuid.statusCode(), "invalid uuid in body status");
            equal("VALIDATION_ERROR", field(badUuid.body(), "code"), "code");
            contains(badUuid.body(), "organizerId must be a valid UUID", "error message detail");

            // Missing required fields
            var missingTitle = post(server, "/api/workshops", "{\"organizerId\":\"" + UUID.randomUUID() + "\",\"capacity\":2}");
            equal(400, missingTitle.statusCode(), "missing title status");
            contains(missingTitle.body(), "title is required", "missing field message");

            // Invalid UUID in URL path
            var badPathUuid = get(server, "/api/workshops/not-a-valid-uuid");
            equal(400, badPathUuid.statusCode(), "invalid path uuid status");
            equal("VALIDATION_ERROR", field(badPathUuid.body(), "code"), "code");
            passed++;
        } finally {
            server.stop();
        }
    }

    private void enforcesSecurityHeadersOnResponses() throws Exception {
        var server = new CampusQueueHttpServer(new CampusQueueService(), new InetSocketAddress("127.0.0.1", 0));
        server.start();
        try {
            var response = get(server, "/api/workshops");
            equal(200, response.statusCode(), "status code");

            var headers = response.headers();
            var contentType = headers.firstValue("content-type").orElse("");
            contains(contentType, "application/json", "content-type header");
            contains(contentType, "charset=utf-8", "charset UTF-8");

            var nosniff = headers.firstValue("x-content-type-options").orElse("");
            equal("nosniff", nosniff, "nosniff header");

            var cacheControl = headers.firstValue("cache-control").orElse("");
            equal("no-store", cacheControl, "cache-control header");
            passed++;
        } finally {
            server.stop();
        }
    }

    private HttpResponse<String> post(CampusQueueHttpServer server, String path, String json) throws Exception {
        return client.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> delete(CampusQueueHttpServer server, String path, String json) throws Exception {
        return client.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .header("content-type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> get(CampusQueueHttpServer server, String path) throws Exception {
        return client.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private static String field(String json, String name) {
        var matcher = Pattern.compile("\\\"" + name + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        if (!matcher.find()) throw new AssertionError("Missing field " + name + " in " + json);
        return matcher.group(1);
    }

    private static String numberField(String json, String name) {
        var matcher = Pattern.compile("\\\"" + name + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) throw new AssertionError("Missing field " + name + " in " + json);
        return matcher.group(1);
    }

    private static void equal(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
    }

    private static void contains(String value, String expected, String message) {
        if (!value.contains(expected)) throw new AssertionError(message + ": " + value);
    }
}
