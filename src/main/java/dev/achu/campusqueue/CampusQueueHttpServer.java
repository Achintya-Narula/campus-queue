package dev.achu.campusqueue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CampusQueueHttpServer {
    private final CampusQueueService service;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(12);

    public CampusQueueHttpServer(CampusQueueService service, InetSocketAddress address) throws IOException {
        this.service = service;
        this.server = HttpServer.create(address, 0);
        this.server.setExecutor(executor);
        this.server.createContext("/api/users", this::handleUsers);
        this.server.createContext("/api/workshops", this::handleWorkshops);
        this.server.createContext("/", this::handleRoot);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        var path = exchange.getRequestURI().getPath();
        if (path.equals("/")) {
            var info = "{\"service\":\"CampusQueue API\",\"version\":\"1.0.0\",\"status\":\"UP\",\"endpoints\":{\"workshops\":\"/api/workshops\",\"users\":\"/api/users\"},\"dashboard\":\"http://127.0.0.1:4200\"}";
            send(exchange, 200, info);
            return;
        }
        sendError(exchange, 404, "NOT_FOUND", "Route not found");
    }

    private void handleUsers(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestMethod().equals("POST") || !exchange.getRequestURI().getPath().equals("/api/users")) {
                sendError(exchange, 404, "NOT_FOUND", "Route not found");
                return;
            }
            var body = body(exchange);
            var user = service.registerUser(required(body, "email"), Role.valueOf(required(body, "role")));
            send(exchange, 201, Json.user(user));
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, "VALIDATION_ERROR", exception.getMessage());
        } catch (Exception exception) {
            sendError(exchange, 500, "INTERNAL_ERROR", "An unexpected error occurred");
        }
    }

    private void handleWorkshops(HttpExchange exchange) throws IOException {
        try {
            var method = exchange.getRequestMethod();
            var path = exchange.getRequestURI().getPath();
            if (method.equals("GET") && path.equals("/api/workshops")) {
                send(exchange, 200, Json.workshops(service.listPublishedWorkshops()));
                return;
            }
            if (method.equals("POST") && path.equals("/api/workshops")) {
                var body = body(exchange);
                var workshop = service.createWorkshop(
                    uuid(body, "organizerId"),
                    required(body, "title"),
                    integer(body, "capacity")
                );
                send(exchange, 201, Json.workshop(workshop));
                return;
            }

            var parts = path.split("/");
            if (parts.length < 4 || !parts[1].equals("api") || !parts[2].equals("workshops")) {
                sendError(exchange, 404, "NOT_FOUND", "Route not found");
                return;
            }
            var workshopId = UUID.fromString(parts[3]);
            if (method.equals("GET") && parts.length == 4) {
                send(exchange, 200, Json.workshop(service.getWorkshop(workshopId)));
                return;
            }
            if (method.equals("POST") && parts.length == 5 && parts[4].equals("publish")) {
                send(exchange, 200, Json.workshop(service.publishWorkshop(uuid(body(exchange), "organizerId"), workshopId)));
                return;
            }
            if (method.equals("POST") && parts.length == 5 && parts[4].equals("cancel")) {
                send(exchange, 200, Json.workshop(service.cancelWorkshop(uuid(body(exchange), "organizerId"), workshopId)));
                return;
            }
            if (parts.length == 5 && parts[4].equals("registrations")) {
                var studentId = uuid(body(exchange), "studentId");
                if (method.equals("POST")) {
                    send(exchange, 201, Json.registration(service.register(workshopId, studentId)));
                    return;
                }
                if (method.equals("DELETE")) {
                    send(exchange, 200, Json.cancellation(service.cancelRegistration(workshopId, studentId)));
                    return;
                }
            }
            sendError(exchange, 404, "NOT_FOUND", "Route not found");
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, "VALIDATION_ERROR", exception.getMessage());
        } catch (Exception exception) {
            sendError(exchange, 500, "INTERNAL_ERROR", "An unexpected error occurred");
        }
    }

    private static Map<String, String> body(HttpExchange exchange) throws IOException {
        var bytes = exchange.getRequestBody().readNBytes(1_000_001);
        if (bytes.length > 1_000_000) throw new IllegalArgumentException("Request body is too large");
        return Json.parseObject(new String(bytes, StandardCharsets.UTF_8));
    }

    private static String required(Map<String, String> body, String name) {
        var value = body.get(name);
        if (value == null || value.isBlank() || value.equals("null")) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static UUID uuid(Map<String, String> body, String name) {
        try {
            return UUID.fromString(required(body, name));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " must be a valid UUID");
        }
    }

    private static int integer(Map<String, String> body, String name) {
        try {
            return Integer.parseInt(required(body, name));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
    }

    private static void sendError(HttpExchange exchange, int status, String code, String message) throws IOException {
        send(exchange, status, Json.error(code, message));
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("content-type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("cache-control", "no-store");
        exchange.getResponseHeaders().set("x-content-type-options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
