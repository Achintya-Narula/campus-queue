package com.achintya.campusqueue.workshop;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkshopRepository extends JpaRepository<WorkshopEntity, UUID> {

    Optional<WorkshopEntity> findByIdAndStatus(UUID id, WorkshopStatus status);

    Page<WorkshopEntity> findAllByStatus(WorkshopStatus status, Pageable pageable);

    @Query("""
            select w from WorkshopEntity w
            where w.status = :status
              and (:query = ''
                   or lower(w.title) like lower(concat('%', :query, '%'))
                   or lower(w.description) like lower(concat('%', :query, '%')))
            """)
    Page<WorkshopEntity> searchByStatus(
            @Param("status") WorkshopStatus status,
            @Param("query") String query,
            Pageable pageable);

    Page<WorkshopEntity> findAllByOrganizerId(UUID organizerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WorkshopEntity w where w.id = :id")
    Optional<WorkshopEntity> findByIdForUpdate(@Param("id") UUID id);
}
