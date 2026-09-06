package dev.achu.campusqueue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class CampusQueueService {
    private final Map<UUID, User> users = new ConcurrentHashMap<>();
    private final Map<String, UUID> userIdsByEmail = new ConcurrentHashMap<>();
    private final Map<UUID, WorkshopState> workshops = new ConcurrentHashMap<>();

    public synchronized User registerUser(String email, Role role) {
        if (email == null || !email.trim().matches("^\\S+@\\S+\\.\\S+$")) {
            throw new IllegalArgumentException("Email is invalid");
        }
        if (role == null) throw new IllegalArgumentException("Role is required");
        var normalized = email.trim().toLowerCase(Locale.ROOT);
        if (userIdsByEmail.containsKey(normalized)) {
            throw new IllegalArgumentException("Email is already registered");
        }
        var user = new User(UUID.randomUUID(), normalized, role);
        users.put(user.id(), user);
        userIdsByEmail.put(normalized, user.id());
        return user;
    }

    public WorkshopView createWorkshop(UUID organizerId, String title, int capacity) {
        requireRole(organizerId, Role.ORGANIZER, "Only organizers can create workshops");
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Workshop title is required");
        }
        if (capacity < 1) throw new IllegalArgumentException("Capacity must be at least one");
        var state = new WorkshopState(UUID.randomUUID(), organizerId, title.trim(), capacity);
        workshops.put(state.id, state);
        return state.snapshot();
    }

    public WorkshopView publishWorkshop(UUID organizerId, UUID workshopId) {
        var state = requireWorkshop(workshopId);
        state.lock.lock();
        try {
            requireOwner(state, organizerId);
            if (state.cancelled) throw new IllegalArgumentException("Cancelled workshop cannot be published");
            state.published = true;
            return state.snapshotWithoutLock();
        } finally {
            state.lock.unlock();
        }
    }

    public WorkshopView cancelWorkshop(UUID organizerId, UUID workshopId) {
        var state = requireWorkshop(workshopId);
        state.lock.lock();
        try {
            requireOwner(state, organizerId);
            state.cancelled = true;
            return state.snapshotWithoutLock();
        } finally {
            state.lock.unlock();
        }
    }

    public RegistrationResult register(UUID workshopId, UUID studentId) {
        requireRole(studentId, Role.STUDENT, "Only students can register for workshops");
        var state = requireWorkshop(workshopId);
        state.lock.lock();
        try {
            if (!state.published) throw new IllegalArgumentException("Workshop is not published");
            if (state.cancelled) throw new IllegalArgumentException("Workshop is cancelled");
            if (state.confirmed.contains(studentId) || state.waitlist.contains(studentId)) {
                throw new IllegalArgumentException("Student is already registered for this workshop");
            }
            if (state.confirmed.size() < state.capacity) {
                state.confirmed.add(studentId);
                return new RegistrationResult(workshopId, studentId, RegistrationStatus.CONFIRMED, 0);
            }
            state.waitlist.addLast(studentId);
            return new RegistrationResult(workshopId, studentId, RegistrationStatus.WAITLISTED, state.waitlist.size());
        } finally {
            state.lock.unlock();
        }
    }

    public CancellationResult cancelRegistration(UUID workshopId, UUID studentId) {
        var state = requireWorkshop(workshopId);
        state.lock.lock();
        try {
            UUID promoted = null;
            if (state.confirmed.remove(studentId)) {
                promoted = state.waitlist.pollFirst();
                if (promoted != null) state.confirmed.add(promoted);
            } else if (!state.waitlist.remove(studentId)) {
                throw new IllegalArgumentException("Registration was not found");
            }
            return new CancellationResult(workshopId, studentId, promoted);
        } finally {
            state.lock.unlock();
        }
    }

    public WorkshopView getWorkshop(UUID workshopId) {
        return requireWorkshop(workshopId).snapshot();
    }

    public List<WorkshopView> listPublishedWorkshops() {
        return workshops.values().stream()
            .map(WorkshopState::snapshot)
            .filter(view -> view.published() && !view.cancelled())
            .sorted(Comparator.comparing(WorkshopView::title).thenComparing(WorkshopView::id))
            .toList();
    }

    private User requireRole(UUID userId, Role role, String message) {
        var user = users.get(userId);
        if (user == null || user.role() != role) throw new IllegalArgumentException(message);
        return user;
    }

    private WorkshopState requireWorkshop(UUID workshopId) {
        var state = workshops.get(workshopId);
        if (state == null) throw new IllegalArgumentException("Workshop was not found");
        return state;
    }

    private static void requireOwner(WorkshopState state, UUID organizerId) {
        if (!state.organizerId.equals(organizerId)) {
            throw new IllegalArgumentException("Only the workshop organizer can change it");
        }
    }

    private static final class WorkshopState {
        final UUID id;
        final UUID organizerId;
        final String title;
        final int capacity;
        final ReentrantLock lock = new ReentrantLock(true);
        final List<UUID> confirmed = new ArrayList<>();
        final ArrayDeque<UUID> waitlist = new ArrayDeque<>();
        boolean published;
        boolean cancelled;

        WorkshopState(UUID id, UUID organizerId, String title, int capacity) {
            this.id = id;
            this.organizerId = organizerId;
            this.title = title;
            this.capacity = capacity;
        }

        WorkshopView snapshot() {
            lock.lock();
            try {
                return snapshotWithoutLock();
            } finally {
                lock.unlock();
            }
        }

        WorkshopView snapshotWithoutLock() {
            return new WorkshopView(
                id,
                organizerId,
                title,
                capacity,
                published,
                cancelled,
                List.copyOf(confirmed),
                List.copyOf(waitlist)
            );
        }
    }
}
