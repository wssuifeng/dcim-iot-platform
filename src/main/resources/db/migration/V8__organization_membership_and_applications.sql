-- Organization memberships and approval workflows.
CREATE TABLE organization_membership (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    membership_role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    approved_by BIGINT NULL,
    approved_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_membership_user_org (user_id, organization_id),
    KEY idx_membership_user_status (user_id, status),
    KEY idx_membership_org_status_role (organization_id, status, membership_role),
    CONSTRAINT chk_membership_role CHECK (membership_role IN ('ORG_ADMIN', 'OPERATOR', 'VIEWER')),
    CONSTRAINT chk_membership_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REMOVED')),
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_membership_org FOREIGN KEY (organization_id) REFERENCES sys_organization(id),
    CONSTRAINT fk_membership_approver FOREIGN KEY (approved_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Organization membership';

CREATE TABLE organization_creation_application (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    applicant_user_id BIGINT NOT NULL,
    proposed_org_name VARCHAR(100) NOT NULL,
    proposed_org_code VARCHAR(50) NOT NULL,
    description VARCHAR(200) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewer_user_id BIGINT NULL,
    review_note VARCHAR(500) NULL,
    review_time DATETIME NULL,
    resulting_organization_id BIGINT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_org_creation_applicant_status (applicant_user_id, status),
    KEY idx_org_creation_status_time (status, create_time),
    CONSTRAINT chk_org_creation_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT fk_org_creation_applicant FOREIGN KEY (applicant_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_org_creation_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_org_creation_result FOREIGN KEY (resulting_organization_id) REFERENCES sys_organization(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Organization creation application';

CREATE TABLE organization_join_application (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    applicant_user_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    request_note VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    assigned_membership_role VARCHAR(20) NULL,
    reviewer_user_id BIGINT NULL,
    review_note VARCHAR(500) NULL,
    review_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_org_join_applicant_org_status (applicant_user_id, organization_id, status),
    KEY idx_org_join_org_status_time (organization_id, status, create_time),
    CONSTRAINT chk_org_join_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_org_join_role CHECK (assigned_membership_role IS NULL OR assigned_membership_role IN ('ORG_ADMIN', 'OPERATOR', 'VIEWER')),
    CONSTRAINT fk_org_join_applicant FOREIGN KEY (applicant_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_org_join_org FOREIGN KEY (organization_id) REFERENCES sys_organization(id),
    CONSTRAINT fk_org_join_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Organization join application';

INSERT INTO organization_membership
    (user_id, organization_id, membership_role, status, approved_time)
SELECT id, organization_id,
       CASE WHEN role = 'ADMIN' THEN 'ORG_ADMIN' ELSE role END,
       'ACTIVE', CURRENT_TIMESTAMP
FROM sys_user
WHERE organization_id IS NOT NULL
  AND role IN ('ADMIN', 'ORG_ADMIN', 'OPERATOR', 'VIEWER');
