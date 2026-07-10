# Alarm Recipient Filtering Design

## Goal

Send alarm in-app notifications and WebSocket summaries only to enabled business users who have an explicit assignment for the alarm's organization and cloud room.

## Recipient Rule

A recipient must satisfy every condition:

- Role is `ORG_ADMIN`, `OPERATOR`, or `VIEWER`.
- User status is enabled (`status = 0`).
- A non-deleted `sys_data_scope_assignment` row matches both `organization_id` and `cloud_room_id` from the alarm.
- The assignment type is `ROOMS` or `READ_ONLY`; both represent an explicit room assignment when `cloud_room_id` is present.

`PLATFORM_ADMIN`, `ORG_ALL`, users assigned only to another organization or room, and disabled users do not receive the alarm. Missing alarm organization or room fails closed and produces no recipients.

## Architecture

`AlarmRecipientResolver` owns recipient selection. It reads matching room assignments, deduplicates user IDs, and filters the corresponding users by role and enabled status. `AlarmEventServiceImpl` resolves recipients once per state transition and passes the same set to `InAppNotificationService` and `UserScopedWebSocketPublisher`, ensuring both channels apply identical scope rules.

Notification or WebSocket failures remain non-blocking for the alarm state transition. Each WebSocket recipient is sent independently so one failed session does not prevent delivery attempts to other users.

## Testing

Unit tests cover exact room matches, other organizations, other rooms, disabled users, platform administrators, unsupported roles, duplicate assignments, missing organization/room, notification persistence, and per-user WebSocket dispatch.
