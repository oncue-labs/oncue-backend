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
@Table(name = "personas")
public class Persona {

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
    @Column(name = "default_instructions", columnDefinition = "TEXT")
    private String defaultInstructions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dialogue_rules", columnDefinition = "json")
    private List<String> dialogueRules = List.of();

    @Column(name = "voice_id", length = 255)
    private String voiceId;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "preview_audio_url", length = 2048)
    private String previewAudioUrl;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Persona() {
    }

    public Persona(
            Long id,
            String key,
            String name,
            String description,
            String contextPlaceholder,
            String defaultInstructions,
            List<String> dialogueRules,
            String voiceId,
            String imageUrl,
            String previewAudioUrl,
            String status
    ) {
        this.id = id;
        this.key = key;
        this.name = name;
        this.description = description;
        this.contextPlaceholder = contextPlaceholder;
        this.defaultInstructions = defaultInstructions;
        this.dialogueRules = dialogueRules == null ? List.of() : List.copyOf(dialogueRules);
        this.voiceId = voiceId;
        this.imageUrl = imageUrl;
        this.previewAudioUrl = previewAudioUrl;
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

    public String getDefaultInstructions() {
        return defaultInstructions;
    }

    public List<String> getDialogueRules() {
        return dialogueRules == null ? List.of() : List.copyOf(dialogueRules);
    }

    public String getVoiceId() {
        return voiceId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getPreviewAudioUrl() {
        return previewAudioUrl;
    }

    public String getStatus() {
        return status;
    }

    public boolean isActive() {
        return ACTIVE_STATUS.equals(status);
    }
}
