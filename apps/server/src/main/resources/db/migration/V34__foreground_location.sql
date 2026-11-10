CREATE TABLE location_device (
    id VARCHAR(36) PRIMARY KEY,
    device_id VARCHAR(36) NOT NULL,
    generation BIGINT NOT NULL,
    binding_version BIGINT NOT NULL,
    active_member_id VARCHAR(36),
    sharing_version BIGINT NOT NULL,
    home_config_id VARCHAR(36),
    last_sequence BIGINT NOT NULL,
    last_observed_at TIMESTAMP(6),
    last_request_id VARCHAR(36),
    physical_state VARCHAR(12),
    received_at TIMESTAMP(6),
    last_confirmed VARCHAR(12),
    outside_at TIMESTAMP(6),
    last_arrival_at TIMESTAMP(6),
    CONSTRAINT fk_location_account FOREIGN KEY (id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_location_membership FOREIGN KEY (active_member_id) REFERENCES membership(id) ON DELETE SET NULL
);
CREATE INDEX ix_location_member ON location_device(active_member_id);

ALTER TABLE life_event ADD COLUMN event_source VARCHAR(12) NOT NULL DEFAULT 'MANUAL';
