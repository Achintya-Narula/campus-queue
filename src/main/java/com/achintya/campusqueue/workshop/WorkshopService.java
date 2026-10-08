package com.achintya.campusqueue.workshop;

import com.achintya.campusqueue.common.error.ConflictException;
import com.achintya.campusqueue.common.error.ForbiddenException;
import com.achintya.campusqueue.common.error.NotFoundException;
import com.achintya.campusqueue.registration.RegistrationEntity;
import com.achintya.campusqueue.registration.RegistrationRepository;
import com.achintya.campusqueue.registration.RegistrationStatus;
import com.achintya.campusqueue.registration.dto.RosterEntryResponse;
import com.achintya.campusqueue.registration.dto.WorkshopRosterResponse;
import com.achintya.campusqueue.user.UserEntity;
import com.achintya.campusqueue.user.UserRepository;
import com.achintya.campusqueue.workshop.dto.CreateWorkshopRequest;
import com.achintya.campusqueue.workshop.dto.UpdateWorkshopRequest;
import com.achintya.campusqueue.workshop.dto.WorkshopResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkshopService {

    private final WorkshopRepository workshopRepository;
    private final UserRepository userRepository;
    private final RegistrationRepository registrationRepository;
    private final Clock clock;

    public WorkshopService(
            WorkshopRepository workshopRepository,
            UserRepository userRepository,
            RegistrationRepository registrationRepository,
            Clock clock) {
        this.workshopRepository = workshopRepository;
        this.userRepository = userRepository;
        this.registrationRepository = registrationRepository;
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
        WorkshopEntity workshop = findOwnedLocked(organizerId, workshopId);
        if (workshop.getStatus() == WorkshopStatus.CANCELLED) {
            throw new ConflictException(
                    "INVALID_WORKSHOP_TRANSITION",
                    "Cancelled workshops cannot be edited");
        }
        long confirmed = registrationRepository.countByWorkshop_IdAndStatus(
                workshopId, RegistrationStatus.CONFIRMED);
        if (request.capacity() < confirmed) {
            throw new ConflictException(
                    "CAPACITY_BELOW_CONFIRMED",
                    "Capacity cannot be lower than the number of confirmed registrations");
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

    @Transactional(readOnly = true)
    public WorkshopRosterResponse getRoster(UUID organizerId, UUID workshopId) {
        findOwned(organizerId, workshopId);
        List<RosterEntryResponse> confirmed = registrationRepository
                .findByWorkshop_IdAndStatusOrderByCreatedAtAsc(
                        workshopId, RegistrationStatus.CONFIRMED)
                .stream()
                .map(RosterEntryResponse::from)
                .toList();
        List<RosterEntryResponse> waitlisted = registrationRepository
                .findByWorkshop_IdAndStatusOrderByWaitlistSequenceAsc(
                        workshopId, RegistrationStatus.WAITLISTED)
                .stream()
                .map(RosterEntryResponse::from)
                .toList();
        return new WorkshopRosterResponse(confirmed, waitlisted);
    }

    @Transactional
    public WorkshopResponse cancel(UUID organizerId, UUID workshopId) {
        WorkshopEntity workshop = findOwnedLocked(organizerId, workshopId);
        if (workshop.getStatus() == WorkshopStatus.CANCELLED) {
            throw new ConflictException(
                    "INVALID_WORKSHOP_TRANSITION",
                    "Workshop is already cancelled");
        }

        Instant now = Instant.now(clock);
        workshop.cancel(now);
        List<RegistrationEntity> confirmed = registrationRepository
                .findByWorkshop_IdAndStatusOrderByCreatedAtAsc(
                        workshopId, RegistrationStatus.CONFIRMED);
        List<RegistrationEntity> waitlisted = registrationRepository
                .findByWorkshop_IdAndStatusOrderByWaitlistSequenceAsc(
                        workshopId, RegistrationStatus.WAITLISTED);
        confirmed.forEach(registration -> registration.cancel(now));
        waitlisted.forEach(registration -> registration.cancel(now));
        registrationRepository.flush();
        return WorkshopResponse.from(workshop);
    }

    private WorkshopEntity findOwned(UUID organizerId, UUID workshopId) {
        WorkshopEntity workshop = workshopRepository.findById(workshopId)
                .orElseThrow(this::workshopNotFound);
        if (!workshop.getOrganizer().getId().equals(organizerId)) {
            throw new ForbiddenException("WORKSHOP_NOT_OWNED", "Workshop belongs to another organizer");
        }
        return workshop;
    }

    private WorkshopEntity findOwnedLocked(UUID organizerId, UUID workshopId) {
        WorkshopEntity workshop = workshopRepository.findByIdForUpdate(workshopId)
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
