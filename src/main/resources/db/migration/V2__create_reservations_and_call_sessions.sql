CREATE TABLE reservations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    persona_id BIGINT UNSIGNED NOT NULL,
    scenario_id BIGINT UNSIGNED NOT NULL,
    scenario_context TEXT NOT NULL,
    call_goal TEXT NOT NULL,
    scheduled_at_utc DATETIME(6) NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    reservation_status VARCHAR(32) NOT NULL,
    cancelled_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reservations_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reservations_persona
        FOREIGN KEY (persona_id) REFERENCES personas (id),
    CONSTRAINT fk_reservations_scenario
        FOREIGN KEY (scenario_id) REFERENCES scenarios (id)
) ENGINE = InnoDB;

CREATE TABLE call_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT UNSIGNED NOT NULL,
    voice_session_id VARCHAR(255),
    call_status VARCHAR(32) NOT NULL,
    call_outcome VARCHAR(32),
    started_at DATETIME(6),
    ended_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_call_sessions_reservation UNIQUE (reservation_id),
    CONSTRAINT fk_call_sessions_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations (id)
) ENGINE = InnoDB;
