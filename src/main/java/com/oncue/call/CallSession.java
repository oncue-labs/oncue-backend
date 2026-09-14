package com.oncue.call;

import com.oncue.reservation.model.Reservation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "call_sessions")
public class CallSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Column(name = "voice_session_id", length = 255)
    private String voiceSessionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "call_status", nullable = false, length = 32)
    private CallStatus callStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "call_outcome", length = 32)
    private CallOutcome callOutcome;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CallSession() {
    }

    public CallSession(Reservation reservation) {
        this.reservation = reservation;
        this.callStatus = CallStatus.PREPARING;
    }

    public CallSession(Long callSessionId, Reservation reservation) {
        this.id = callSessionId;
        this.reservation = reservation;
        this.callStatus = CallStatus.PREPARING;
    }

    public void markVoiceSession(String voiceSessionId) {
        if (isEnded()) {
            return;
        }
        this.voiceSessionId = voiceSessionId;
    }

    public void advanceTo(CallStatus nextCallStatus) {
        if (isEnded() || nextCallStatus == null || nextCallStatus.ordinal() <= callStatus.ordinal()) {
            return;
        }
        this.callStatus = nextCallStatus;
    }

    public void complete(CallOutcome outcome, Instant callStartedAt, Instant callEndedAt) {
        if (isEnded() || outcome == null || callEndedAt == null) {
            return;
        }
        this.callOutcome = outcome;
        this.startedAt = callStartedAt;
        this.endedAt = callEndedAt;
    }

    public boolean isEnded() {
        return endedAt != null;
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

    public Long getId() {
        return id;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public String getVoiceSessionId() {
        return voiceSessionId;
    }

    public CallStatus getCallStatus() {
        return callStatus;
    }

    public CallOutcome getCallOutcome() {
        return callOutcome;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
