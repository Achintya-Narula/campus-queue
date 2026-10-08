package com.achintya.campusqueue.registration;

import com.achintya.campusqueue.common.error.ConflictException;
import com.achintya.campusqueue.common.error.NotFoundException;
import com.achintya.campusqueue.registration.dto.CancellationResponse;
import com.achintya.campusqueue.registration.dto.RegistrationResponse;
import com.achintya.campusqueue.user.UserEntity;
import com.achintya.campusqueue.user.UserRepository;
import com.achintya.campusqueue.workshop.WorkshopEntity;
import com.achintya.campusqueue.workshop.WorkshopRepository;
import com.achintya.campusqueue.workshop.WorkshopStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final RegistrationRepository registrationRepository;
    private final WorkshopRepository workshopRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public RegistrationService(
            RegistrationRepository registrationRepository,
            WorkshopRepository workshopRepository,
            UserRepository userRepository,
            Clock clock) {
        this.registrationRepository = registrationRepository;
        this.workshopRepository = workshopRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public RegistrationResponse register(UUID studentId, UUID workshopId) {
        WorkshopEntity workshop = workshopRepository.findByIdForUpdate(workshopId)
                .orElseThrow(() -> new NotFoundException("WORKSHOP_NOT_FOUND", "Workshop was not found"));
        if (workshop.getStatus() != WorkshopStatus.PUBLISHED) {
            throw new ConflictException("WORKSHOP_NOT_OPEN", "Workshop is not open for registration");
        }

        UserEntity student = userRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Authenticated user no longer exists"));
        RegistrationEntity registration = registrationRepository
                .findByWorkshop_IdAndStudent_Id(workshopId, studentId)
                .orElseGet(() -> new RegistrationEntity(workshop, student, Instant.now(clock)));
        if (registration.getStatus() == RegistrationStatus.CONFIRMED
                || registration.getStatus() == RegistrationStatus.WAITLISTED) {
            throw new ConflictException("ALREADY_REGISTERED", "Student already has an active registration");
        }

        Instant now = Instant.now(clock);
        long confirmed = registrationRepository.countByWorkshop_IdAndStatus(
                workshopId, RegistrationStatus.CONFIRMED);
        Long queuePosition = null;
        if (confirmed < workshop.getCapacity()) {
            registration.confirm(now);
        } else {
            long sequence = workshop.allocateWaitlistSequence();
            registration.waitlist(sequence, now);
        }
        RegistrationEntity saved = registrationRepository.saveAndFlush(registration);
        if (saved.getStatus() == RegistrationStatus.WAITLISTED) {
            queuePosition = registrationRepository
                    .countByWorkshop_IdAndStatusAndWaitlistSequenceLessThanEqual(
                            workshopId,
                            RegistrationStatus.WAITLISTED,
                            saved.getWaitlistSequence());
        }
        return RegistrationResponse.from(saved, queuePosition);
    }

    @Transactional(readOnly = true)
    public Page<RegistrationResponse> listForStudent(UUID studentId, Pageable pageable) {
        return registrationRepository.findByStudent_Id(studentId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public CancellationResponse cancel(UUID studentId, UUID workshopId) {
        workshopRepository.findByIdForUpdate(workshopId)
                .orElseThrow(() -> new NotFoundException("WORKSHOP_NOT_FOUND", "Workshop was not found"));
        RegistrationEntity registration = registrationRepository
                .findByWorkshop_IdAndStudent_Id(workshopId, studentId)
                .orElseThrow(this::registrationNotActive);
        if (registration.getStatus() == RegistrationStatus.CANCELLED) {
            throw registrationNotActive();
        }

        RegistrationStatus previousStatus = registration.getStatus();
        registration.cancel(Instant.now(clock));
        UUID promotedRegistrationId = null;
        if (previousStatus == RegistrationStatus.CONFIRMED) {
            RegistrationEntity promoted = registrationRepository
                    .findFirstByWorkshop_IdAndStatusOrderByWaitlistSequenceAsc(
                            workshopId, RegistrationStatus.WAITLISTED)
                    .orElse(null);
            if (promoted != null) {
                promoted.confirm(Instant.now(clock));
                promotedRegistrationId = promoted.getId();
            }
        }
        registrationRepository.flush();
        return new CancellationResponse(registration.getId(), previousStatus, promotedRegistrationId);
    }

    private RegistrationResponse toResponse(RegistrationEntity registration) {
        Long queuePosition = null;
        if (registration.getStatus() == RegistrationStatus.WAITLISTED) {
            queuePosition = registrationRepository
                    .countByWorkshop_IdAndStatusAndWaitlistSequenceLessThanEqual(
                            registration.getWorkshop().getId(),
                            RegistrationStatus.WAITLISTED,
                            registration.getWaitlistSequence());
        }
        return RegistrationResponse.from(registration, queuePosition);
    }

    private ConflictException registrationNotActive() {
        return new ConflictException(
                "REGISTRATION_NOT_ACTIVE",
                "Student does not have an active registration for this workshop");
    }
}
