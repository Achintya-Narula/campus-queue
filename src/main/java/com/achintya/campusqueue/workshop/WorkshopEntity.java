package com.achintya.campusqueue.workshop;

import com.achintya.campusqueue.user.UserEntity;
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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workshop")
public class WorkshopEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private UserEntity organizer;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkshopStatus status;

    @Column(name = "next_waitlist_sequence", nullable = false)
    private long nextWaitlistSequence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkshopEntity() {
    }

    public WorkshopEntity(
            UserEntity organizer,
            String title,
            String description,
            int capacity,
            Instant now) {
        this.organizer = organizer;
        this.title = title;
        this.description = description;
        this.capacity = capacity;
        this.status = WorkshopStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void publish(Instant now) {
        if (status != WorkshopStatus.DRAFT) {
            throw new IllegalStateException("Only draft workshops can be published");
        }
        status = WorkshopStatus.PUBLISHED;
        updatedAt = now;
    }

    public void updateDetails(String title, String description, int capacity, Instant now) {
        this.title = title;
        this.description = description;
        this.capacity = capacity;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        if (status == WorkshopStatus.CANCELLED) {
            throw new IllegalStateException("Workshop is already cancelled");
        }
        status = WorkshopStatus.CANCELLED;
        updatedAt = now;
    }

    public long allocateWaitlistSequence() {
        return ++nextWaitlistSequence;
    }

    public UUID getId() {
        return id;
    }

    public UserEntity getOrganizer() {
        return organizer;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getCapacity() {
        return capacity;
    }

    public WorkshopStatus getStatus() {
        return status;
    }

    public long getNextWaitlistSequence() {
        return nextWaitlistSequence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
