-- Global identity and platform registration policy.
ALTER TABLE sys_user
    ADD COLUMN email VARCHAR(254) NULL COMMENT 'Normalized globally unique email' AFTER username,
    ADD COLUMN email_verified TINYINT NOT NULL DEFAULT 0 COMMENT '0-unverified 1-verified' AFTER email,
    ADD COLUMN email_verified_time DATETIME NULL COMMENT 'Email verification time' AFTER email_verified,
    ADD COLUMN platform_role VARCHAR(20) NULL COMMENT 'Platform role, currently PLATFORM_ADMIN only' AFTER role,
    ADD UNIQUE KEY uk_user_email (email);

ALTER TABLE sys_organization
    ADD COLUMN join_application_enabled TINYINT NOT NULL DEFAULT 1 COMMENT 'Whether join applications are accepted' AFTER status,
    ADD UNIQUE KEY uk_org_name (org_name);

CREATE TABLE platform_registration_settings (
    id TINYINT PRIMARY KEY,
    account_registration_enabled TINYINT NOT NULL DEFAULT 1,
    organization_registration_mode VARCHAR(20) NOT NULL DEFAULT 'REVIEW',
    updated_by BIGINT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_registration_settings_singleton CHECK (id = 1),
    CONSTRAINT chk_organization_registration_mode
        CHECK (organization_registration_mode IN ('CLOSED', 'REVIEW', 'OPEN')),
    CONSTRAINT fk_registration_settings_user FOREIGN KEY (updated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Platform registration settings';

INSERT INTO platform_registration_settings
    (id, account_registration_enabled, organization_registration_mode)
VALUES (1, 1, 'REVIEW');

UPDATE sys_user
SET platform_role = 'PLATFORM_ADMIN'
WHERE role = 'PLATFORM_ADMIN';
