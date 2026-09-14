package com.oncue.reservation.model;

import com.oncue.auth.model.User;
import com.oncue.combination.model.Persona;
import com.oncue.combination.model.Scenario;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id", nullable = false)
    private Scenario scenario;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "scenario_context", nullable = false, columnDefinition = "TEXT")
    private String scenarioContext;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "call_goal", nullable = false, columnDefinition = "TEXT")
    private String callGoal;

    @Column(name = "scheduled_at_utc", nullable = false)
    private Instant scheduledAtUtc;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reservation_status", nullable = false, length = 32)
    private ReservationStatus reservationStatus;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reservation() {
    }

    public Reservation(
            User user,
            Persona persona,
            Scenario scenario,
            String scenarioContext,
            String callGoal,
            Instant scheduledAtUtc,
            String timeZone
    ) {
        this.user = user;
        this.persona = persona;
        this.scenario = scenario;
        this.scenarioContext = scenarioContext;
        this.callGoal = callGoal;
        this.scheduledAtUtc = scheduledAtUtc;
        this.timeZone = timeZone;
        this.reservationStatus = ReservationStatus.SCHEDULED;
    }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Persona getPersona() { return persona; }
    public Scenario getScenario() { return scenario; }
    public String getScenarioContext() { return scenarioContext; }
    public String getCallGoal() { return callGoal; }
    public Instant getScheduledAtUtc() { return scheduledAtUtc; }
    public String getTimeZone() { return timeZone; }
    public ReservationStatus getReservationStatus() { return reservationStatus; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getCreatedAt() { return createdAt; }

    public Instant getEditableUntil() {
        return scheduledAtUtc.minusSeconds(5 * 60L);
    }

    public void update(
            Persona persona,
            Scenario scenario,
            String scenarioContext,
            String callGoal,
            Instant scheduledAtUtc,
            String timeZone
    ) {
        this.persona = persona;
        this.scenario = scenario;
        this.scenarioContext = scenarioContext;
        this.callGoal = callGoal;
        this.scheduledAtUtc = scheduledAtUtc;
        this.timeZone = timeZone;
    }

    public void cancel(Instant cancelledAt) {
        this.reservationStatus = ReservationStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }
}
