package com.achintya.campusqueue.workshop;

import com.achintya.campusqueue.common.error.ConflictException;
import com.achintya.campusqueue.common.error.ForbiddenException;
import com.achintya.campusqueue.common.error.NotFoundException;
import com.achintya.campusqueue.user.UserEntity;
import com.achintya.campusqueue.user.UserRepository;
import com.achintya.campusqueue.workshop.dto.CreateWorkshopRequest;
import com.achintya.campusqueue.workshop.dto.UpdateWorkshopRequest;
import com.achintya.campusqueue.workshop.dto.WorkshopResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkshopService {

    private final WorkshopRepository workshopRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public WorkshopService(
            WorkshopRepository workshopRepository,
            UserRepository userRepository,
            Clock clock) {
        this.workshopRepository = workshopRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public WorkshopResponse create(UUID organizerId, CreateWorkshopRequest request) {
        UserEntity organizer = userRepository.findById(organizerId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Authenticated user no longer exists"));
        WorkshopEntity workshop = new WorkshopEntity(
                organizer,
                request.title(),
                request.description(),
                request.capacity(),
                Instant.now(clock));
        return WorkshopResponse.from(workshopRepository.save(workshop));
    }

    @Transactional
    public WorkshopResponse update(UUID organizerId, UUID workshopId, UpdateWorkshopRequest request) {
        WorkshopEntity workshop = findOwned(organizerId, workshopId);
        if (workshop.getStatus() == WorkshopStatus.CANCELLED) {
            throw new ConflictException(
                    "INVALID_WORKSHOP_TRANSITION",
                    "Cancelled workshops cannot be edited");
        }
        workshop.updateDetails(
                request.title(), request.description(), request.capacity(), Instant.now(clock));
        return WorkshopResponse.from(workshop);
    }

    @Transactional
    public WorkshopResponse publish(UUID organizerId, UUID workshopId) {
        WorkshopEntity workshop = findOwned(organizerId, workshopId);
        if (workshop.getStatus() != WorkshopStatus.DRAFT) {
            throw new ConflictException(
                    "INVALID_WORKSHOP_TRANSITION",
                    "Only draft workshops can be published");
        }
        workshop.publish(Instant.now(clock));
        return WorkshopResponse.from(workshop);
    }

    @Transactional(readOnly = true)
    public WorkshopResponse getPublished(UUID workshopId) {
        return workshopRepository.findByIdAndStatus(workshopId, WorkshopStatus.PUBLISHED)
                .map(WorkshopResponse::from)
                .orElseThrow(this::workshopNotFound);
    }

    @Transactional(readOnly = true)
    public Page<WorkshopResponse> listPublished(Pageable pageable) {
        return workshopRepository.findAllByStatus(WorkshopStatus.PUBLISHED, pageable)
                .map(WorkshopResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<WorkshopResponse> listOwned(UUID organizerId, Pageable pageable) {
        return workshopRepository.findAllByOrganizerId(organizerId, pageable)
                .map(WorkshopResponse::from);
    }

    private WorkshopEntity findOwned(UUID organizerId, UUID workshopId) {
        WorkshopEntity workshop = workshopRepository.findById(workshopId)
                .orElseThrow(this::workshopNotFound);
        if (!workshop.getOrganizer().getId().equals(organizerId)) {
            throw new ForbiddenException("WORKSHOP_NOT_OWNED", "Workshop belongs to another organizer");
        }
        return workshop;
    }

    private NotFoundException workshopNotFound() {
        return new NotFoundException("WORKSHOP_NOT_FOUND", "Workshop was not found");
    }
}
