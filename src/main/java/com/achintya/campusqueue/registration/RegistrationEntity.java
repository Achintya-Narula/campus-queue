package com.achintya.campusqueue.registration;

import com.achintya.campusqueue.user.UserEntity;
import com.achintya.campusqueue.workshop.WorkshopEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "registration",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_registration_workshop_student",
                columnNames = {"workshop_id", "student_id"}))
public class RegistrationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workshop_id", nullable = false)
    private WorkshopEntity workshop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private UserEntity student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus status;

    @Column(name = "waitlist_sequence")
    private Long waitlistSequence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RegistrationEntity() {
    }

    public RegistrationEntity(WorkshopEntity workshop, UserEntity student, Instant now) {
        this.workshop = workshop;
        this.student = student;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void confirm(Instant now) {
        status = RegistrationStatus.CONFIRMED;
        waitlistSequence = null;
        updatedAt = now;
    }

    public void waitlist(long sequence, Instant now) {
        status = RegistrationStatus.WAITLISTED;
        waitlistSequence = sequence;
        updatedAt = now;
    }

    public void cancel(Instant now) {
        status = RegistrationStatus.CANCELLED;
        waitlistSequence = null;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public WorkshopEntity getWorkshop() {
        return workshop;
    }

    public UserEntity getStudent() {
        return student;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public Long getWaitlistSequence() {
        return waitlistSequence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
