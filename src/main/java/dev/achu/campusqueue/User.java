package dev.achu.campusqueue;

import java.util.UUID;

public record User(UUID id, String email, Role role) {}

