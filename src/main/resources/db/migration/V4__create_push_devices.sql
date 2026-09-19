CREATE TABLE push_devices (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    device_token VARCHAR(512) NOT NULL,
    platform VARCHAR(16) NOT NULL,
    environment VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_push_devices_user UNIQUE (user_id),
    CONSTRAINT fk_push_devices_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;
