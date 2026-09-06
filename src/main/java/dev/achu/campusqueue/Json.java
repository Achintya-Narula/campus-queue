package dev.achu.campusqueue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class Json {
    private static final Pattern FIELD = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*(\\\"(?:\\\\.|[^\\\"])*\\\"|-?\\d+|true|false|null)");

    private Json() {}

    static Map<String, String> parseObject(String input) {
        if (input == null || !input.trim().startsWith("{") || !input.trim().endsWith("}")) {
            throw new IllegalArgumentException("Request body must be a JSON object");
        }
        var fields = new LinkedHashMap<String, String>();
        var matcher = FIELD.matcher(input);
        while (matcher.find()) {
            var value = matcher.group(2);
            fields.put(matcher.group(1), value.startsWith("\"") ? unescape(value.substring(1, value.length() - 1)) : value);
        }
        return fields;
    }

    static String user(User user) {
        return "{\"id\":" + quote(user.id().toString()) + ",\"email\":" + quote(user.email()) + ",\"role\":" + quote(user.role().name()) + "}";
    }

    static String registration(RegistrationResult result) {
        return "{\"workshopId\":" + quote(result.workshopId().toString())
            + ",\"studentId\":" + quote(result.studentId().toString())
            + ",\"status\":" + quote(result.status().name())
            + ",\"waitlistPosition\":" + result.waitlistPosition() + "}";
    }

    static String cancellation(CancellationResult result) {
        return "{\"workshopId\":" + quote(result.workshopId().toString())
            + ",\"studentId\":" + quote(result.studentId().toString())
            + ",\"promotedStudentId\":" + (result.promotedStudentId() == null ? "null" : quote(result.promotedStudentId().toString())) + "}";
    }

    static String workshop(WorkshopView workshop) {
        return "{\"id\":" + quote(workshop.id().toString())
            + ",\"organizerId\":" + quote(workshop.organizerId().toString())
            + ",\"title\":" + quote(workshop.title())
            + ",\"capacity\":" + workshop.capacity()
            + ",\"published\":" + workshop.published()
            + ",\"cancelled\":" + workshop.cancelled()
            + ",\"confirmedStudentIds\":" + ids(workshop.confirmedStudentIds())
            + ",\"waitlistedStudentIds\":" + ids(workshop.waitlistedStudentIds()) + "}";
    }

    static String workshops(List<WorkshopView> workshops) {
        return "{\"workshops\":[" + workshops.stream().map(Json::workshop).collect(Collectors.joining(",")) + "]}";
    }

    static String error(String code, String message) {
        return "{\"error\":{\"code\":" + quote(code) + ",\"message\":" + quote(message) + "}}";
    }

    private static String ids(List<UUID> ids) {
        return "[" + ids.stream().map(UUID::toString).map(Json::quote).collect(Collectors.joining(",")) + "]";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }
}

