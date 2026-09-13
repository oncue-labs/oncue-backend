package com.oncue.combination.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "scenarios")
public class Scenario {

    public static final String ACTIVE_STATUS = "ACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "`key`", nullable = false, unique = true, length = 100)
    private String key;

    @Column(nullable = false, length = 255)
    private String name;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "context_placeholder", nullable = false, columnDefinition = "TEXT")
    private String contextPlaceholder;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "call_goal_placeholder", nullable = false, columnDefinition = "TEXT")
    private String callGoalPlaceholder;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "default_instructions", columnDefinition = "TEXT")
    private String defaultInstructions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dialogue_rules", columnDefinition = "json")
    private List<String> dialogueRules = List.of();

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Scenario() {
    }

    public Scenario(
            Long id,
            String key,
            String name,
            String description,
            String contextPlaceholder,
            String callGoalPlaceholder,
            String defaultInstructions,
            List<String> dialogueRules,
            String status
    ) {
        this.id = id;
        this.key = key;
        this.name = name;
        this.description = description;
        this.contextPlaceholder = contextPlaceholder;
        this.callGoalPlaceholder = callGoalPlaceholder;
        this.defaultInstructions = defaultInstructions;
        this.dialogueRules = dialogueRules == null ? List.of() : List.copyOf(dialogueRules);
        this.status = status;
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

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getContextPlaceholder() {
        return contextPlaceholder;
    }

    public String getCallGoalPlaceholder() {
        return callGoalPlaceholder;
    }

    public String getDefaultInstructions() {
        return defaultInstructions;
    }

    public List<String> getDialogueRules() {
        return dialogueRules == null ? List.of() : List.copyOf(dialogueRules);
    }

    public String getStatus() {
        return status;
    }

    public boolean isActive() {
        return ACTIVE_STATUS.equals(status);
    }
}
