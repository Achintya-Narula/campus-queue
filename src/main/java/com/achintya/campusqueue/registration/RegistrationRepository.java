package com.achintya.campusqueue.registration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRepository extends JpaRepository<RegistrationEntity, UUID> {

    Optional<RegistrationEntity> findByWorkshop_IdAndStudent_Id(UUID workshopId, UUID studentId);

    long countByWorkshop_IdAndStatus(UUID workshopId, RegistrationStatus status);

    long countByWorkshop_IdAndStatusAndWaitlistSequenceLessThanEqual(
            UUID workshopId, RegistrationStatus status, long sequence);

    Optional<RegistrationEntity> findFirstByWorkshop_IdAndStatusOrderByWaitlistSequenceAsc(
            UUID workshopId, RegistrationStatus status);

    List<RegistrationEntity> findByWorkshop_IdAndStatusOrderByWaitlistSequenceAsc(
            UUID workshopId, RegistrationStatus status);

    List<RegistrationEntity> findByWorkshop_IdAndStatusOrderByCreatedAtAsc(
            UUID workshopId, RegistrationStatus status);

    Page<RegistrationEntity> findByStudent_Id(UUID studentId, Pageable pageable);
}
