# Registration and Organization Membership Design

## Goal

Build a self-service registration and organization onboarding system based on global accounts and organization memberships. Public users register with a verified email, platform administrators control account and organization registration policy, ordinary users apply to create or join organizations, and organization administrators approve membership without directly creating user credentials.

## Identity Model

`SysUser` is the global login identity. Email and username are both globally unique and both may be used to log in. Organization roles are no longer authoritative fields on `sys_user`; they are resolved from active organization memberships.

Platform authorization and organization authorization are separate:

- `PLATFORM_ADMIN` is a platform-level role and has no organization membership by default.
- An ordinary global account has no platform role and may have zero or more organization memberships.
- `ORG_ADMIN`, `OPERATOR`, and `VIEWER` are membership roles.
- An account with an active `ORG_ADMIN` membership may not hold any other active organization membership.
- `OPERATOR` and `VIEWER` accounts may hold active memberships in multiple organizations.
- One organization may have multiple `ORG_ADMIN` members.

Existing `sys_user.role` and `sys_user.organization_id` remain temporarily for migration compatibility but cease to be authorization sources after membership migration.

## Platform Registration Policy

Platform settings are stored in the database and editable only by `PLATFORM_ADMIN`:

- `accountRegistrationEnabled`: enables or disables public global-account registration.
- `organizationRegistrationMode`: `CLOSED`, `REVIEW`, or `OPEN`.

Behavior:

- `CLOSED`: organization creation and creation applications are rejected.
- `REVIEW`: ordinary users submit organization creation applications for platform review.
- `OPEN`: eligible ordinary users immediately create an organization through the same provisioning service used by approval.

Email verification is mandatory and is not configurable.

Each organization owns a separate `joinApplicationEnabled` setting. The organization administrator controls whether ordinary users may submit join applications.

## Email Registration

Public registration creates only a global account. It does not create an organization or membership.

Required fields:

- `email`
- `username`
- `nickname`
- `password`
- `confirmPassword`
- `verificationCode`

Email is normalized with trim and lowercase. Username and organization code are trimmed. Email, username, organization name, and organization code are globally unique.

Login accepts either verified email or username plus password. There is no legacy username mode; both identifiers are first-class login identifiers.

## Verification Code Security

Spring Mail and QQ SMTP are used during development and demonstration:

- Host: `smtp.qq.com`
- Port: `587`
- STARTTLS enabled
- SMTP authorization code supplied by environment variable

Verification codes are six decimal digits generated with `SecureRandom`. Plain codes are sent by email but never stored in the database or logs. Redis stores an HMAC digest keyed by a normalized-email hash.

Limits:

- Code lifetime: 5 minutes.
- Maximum failed checks per code: 5.
- Same IP send cooldown: 60 seconds.
- Same email send cooldown: 60 seconds.
- Same email hourly quota: 10 messages.
- Same IP hourly quota: 30 messages.
- Successful verification consumes the code immediately.
- Sending a new code invalidates the previous code after successful mail delivery.

Mail failure removes the pending code but retains cooldown keys to prevent SMTP hammering.

## Platform Administrator Bootstrap

The first `PLATFORM_ADMIN` is created through a one-time setup flow available only while no platform administrator exists.

The setup requires:

- `PLATFORM_BOOTSTRAP_TOKEN` supplied by deployment environment.
- A verified email code.
- Globally unique email and username.
- A strong password.

After the first platform administrator is created, setup endpoints fail closed. Additional platform administrators are managed by an existing platform administrator and must verify their email through an invitation flow.

## Organization Creation

`REVIEW` flow:

1. An ordinary account submits organization name, organization code, and description.
2. The system creates an `OrganizationCreationApplication` in `PENDING` state.
3. A platform administrator approves or rejects the application.
4. Approval transaction creates the organization, creates the applicant's active `ORG_ADMIN` membership, and grants `ORG_ALL` scope.
5. The application becomes `APPROVED` and records reviewer and review time.

The applicant must have no active organization membership when submitting and when approving the creation application. This enforces the rule that an organization administrator belongs to exactly one organization.

`OPEN` mode calls the same provisioning transaction immediately. It does not duplicate organization creation logic.

## Organization Join Applications

Only organizations with `joinApplicationEnabled=true` accept applications.

Flow:

1. An ordinary user searches organizations using organization name or code. Only minimal public metadata is returned.
2. The user submits a join application.
3. An organization administrator approves or rejects the application.
4. Approval assigns `ORG_ADMIN`, `OPERATOR`, or `VIEWER` and creates an active membership.
5. Room-level assignments are configured separately after membership approval.

Constraints:

- A current `ORG_ADMIN` cannot submit a join application.
- A user with memberships in other organizations cannot be approved as `ORG_ADMIN` until all other memberships are removed.
- Duplicate pending applications for the same user and organization are rejected.
- The final active organization administrator cannot leave or be removed.

Organization administrators do not create user passwords. They approve self-registered users or send an email invitation to an existing or new email address.

## Current Organization Context

JWT represents the global account and optional platform role. Organization role is not trusted from the token.

Ordinary business requests send:

```http
X-Organization-Id: <organizationId>
```

The backend validates an active membership and builds `DataScopeContext` from membership role plus existing room assignments. A platform administrator uses platform endpoints without an organization header and does not automatically receive organization business access.

The frontend loads active memberships after login:

- Zero memberships: show organization onboarding.
- One membership: select it automatically.
- Multiple memberships: require or remember an organization selection.

## Data Model

### `sys_user` additions

- `email VARCHAR(254)` with unique index, nullable only for migration compatibility.
- `email_verified TINYINT NOT NULL DEFAULT 0`.
- `email_verified_time DATETIME NULL`.
- `platform_role VARCHAR(20) NULL`, currently only `PLATFORM_ADMIN`.

### `platform_registration_settings`

- Singleton row ID.
- `account_registration_enabled`.
- `organization_registration_mode`.
- `updated_by` and `update_time`.

### `organization_membership`

- `user_id`.
- `organization_id`.
- `membership_role`: `ORG_ADMIN`, `OPERATOR`, or `VIEWER`.
- `status`: `ACTIVE`, `SUSPENDED`, or `REMOVED`.
- `approved_by`, `approved_time`, `create_time`, `update_time`.
- Unique active membership per user and organization.
- Database or service guard preventing an active organization administrator from holding another active membership.

### `organization_creation_application`

- Applicant, proposed name, proposed code, description.
- `PENDING`, `APPROVED`, `REJECTED`, or `CANCELLED`.
- Reviewer, review note, review time, resulting organization ID.

### `organization_join_application`

- Applicant, target organization, requested note.
- `PENDING`, `APPROVED`, `REJECTED`, or `CANCELLED`.
- Assigned membership role on approval.
- Reviewer and review metadata.

### `sys_organization` addition

- `join_application_enabled TINYINT NOT NULL DEFAULT 1`.
- Organization name receives a global unique constraint; organization code remains globally unique.

Existing `sys_data_scope_assignment` remains the source for room-level scope. `ORG_ADMIN` receives `ORG_ALL`; `OPERATOR` and `VIEWER` receive explicit room or read-only assignments.

## Migration Strategy

Migrations are append-only. Existing `V1` through `V6` are never modified.

- `V7__registration_identity_and_settings.sql`: email identity, platform role, registration settings, organization name uniqueness, and join switch.
- `V8__organization_membership_and_applications.sql`: memberships, organization creation applications, join applications, indexes, and compatibility backfill.

Backfill converts existing organization-scoped users into active memberships. Existing platform administrators populate `platform_role`. Existing accounts without email remain manageable by administrators but cannot use email-dependent recovery until an email is verified.

## API Surface

Public and authentication:

- `GET /setup/status`
- `POST /setup/verification-codes`
- `POST /setup/platform-admin`
- `POST /auth/registration-codes`
- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/password-reset-codes`
- `POST /auth/password-reset`
- `GET /auth/memberships`

Organization creation:

- `POST /organization-applications`
- `GET /organization-applications/mine`
- `GET /platform/organization-applications`
- `PUT /platform/organization-applications/{id}/approve`
- `PUT /platform/organization-applications/{id}/reject`

Organization membership:

- `GET /organizations/public`
- `POST /organizations/{id}/join-applications`
- `GET /organizations/{id}/join-applications`
- `PUT /organizations/{id}/join-applications/{applicationId}/approve`
- `PUT /organizations/{id}/join-applications/{applicationId}/reject`
- `GET /organizations/{id}/members`
- `PUT /organizations/{id}/members/{userId}/role`
- `DELETE /organizations/{id}/members/{userId}`
- `PUT /organizations/{id}/join-setting`

Platform settings:

- `GET /platform/registration-settings`
- `PUT /platform/registration-settings`

## Frontend Surface

- `/setup`: first platform administrator initialization.
- `/register`: email verification and account registration.
- `/login`: email or username login.
- `/account/organizations`: organization memberships and organization switcher.
- `/organizations/apply`: organization creation application.
- `/organizations/join`: organization discovery and join application.
- `/platform/registration-settings`: platform registration policy.
- `/platform/organization-applications`: platform review queue.
- `/organization/member-applications`: organization join review queue.
- `/organization/members`: membership and role management.

## Audit and Error Handling

Audit events cover verification sends, registration success or rejection, setup attempts, application submission, approval, rejection, membership role changes, member removal, registration-setting changes, and organization join-setting changes.

Logs mask email addresses and never include passwords, SMTP authorization codes, JWTs, or verification codes.

Stable errors include registration closed, verification cooldown, verification expired, verification attempts exhausted, duplicate email, duplicate username, duplicate organization name or code, organization creation closed, join applications closed, membership already exists, application already pending, administrator multi-organization violation, and last-administrator removal rejection.

## Deferred Billing Boundary

Payment, plans, subscriptions, renewals, and entitlement limits are not implemented in this stage. Organization activation is centralized in one provisioning service so a future payment-success callback can reuse the same transaction used by manual approval and open registration.

## Test Strategy

Tests cover:

- Verification expiration, cooldowns, hourly quotas, failed-attempt exhaustion, resend invalidation, and SMTP failure.
- One-time platform setup and concurrent setup rejection.
- Email and username login and uniqueness.
- Registration transaction behavior.
- All three organization registration modes.
- Organization application approval rollback and duplicate handling.
- Organization join switch and approval authorization.
- Multi-organization ordinary memberships.
- Strict single-organization rule for organization administrators.
- Last-administrator protection.
- Current organization header validation and cross-organization rejection.
- Frontend builds and end-to-end setup, registration, application, approval, join, and organization switching flows.
