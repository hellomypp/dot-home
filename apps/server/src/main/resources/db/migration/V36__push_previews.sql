ALTER TABLE household_notification_preference ADD COLUMN lockscreen_preview BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE browser_push_subscription ADD COLUMN client_public_key VARCHAR(88);
ALTER TABLE browser_push_subscription ADD COLUMN client_auth_secret VARCHAR(24);
