# content Specification

## Purpose
TBD - created by archiving change hey-pickler-backend. Update Purpose after archive.
## Requirements
### Requirement: App get banners
The system SHALL provide an endpoint (`GET /api/app/banners`) that returns all enabled banners ordered by `sort_order` ascending.

#### Scenario: List enabled banners
- **WHEN** a GET request is sent to `/api/app/banners`
- **THEN** the system SHALL return all banners with `status=ENABLED`, ordered by `sort_order`

#### Scenario: No banners
- **WHEN** no enabled banners exist
- **THEN** the system SHALL return `{ "code": 0, "data": [] }`

### Requirement: Admin list banners
The system SHALL provide an endpoint (`GET /api/admin/banners`) that returns all banners (including disabled) ordered by `sort_order` ascending.

#### Scenario: List all banners
- **WHEN** an admin sends a GET request to `/api/admin/banners`
- **THEN** the system SHALL return all banners regardless of status

### Requirement: Admin create banner
The system SHALL provide an endpoint (`POST /api/admin/banners`) for admins to create a banner with `image_url`, `link_url`, `sort_order`, and `status`.

#### Scenario: Create banner
- **WHEN** an admin sends a POST with `{ "imageUrl": "...", "linkUrl": "...", "sortOrder": 1, "status": "ENABLED" }`
- **THEN** the system SHALL create the banner and return `{ "code": 0, "data": { "id": <id> } }`

### Requirement: Admin update banner
The system SHALL provide an endpoint (`PUT /api/admin/banners/{id}`) for admins to update banner fields including `sort_order` for reordering.

#### Scenario: Update banner order
- **WHEN** an admin sends a PUT with `{ "sortOrder": 3 }`
- **THEN** the system SHALL update the sort order and return `{ "code": 0 }`

### Requirement: Admin delete banner
The system SHALL provide an endpoint (`DELETE /api/admin/banners/{id}`) for admins to permanently delete a banner.

#### Scenario: Delete banner
- **WHEN** an admin sends a DELETE request for a banner
- **THEN** the system SHALL remove the banner record and return `{ "code": 0 }`

#### Scenario: Delete non-existent banner
- **WHEN** the banner ID does not exist
- **THEN** the system SHALL return `{ "code": 404, "message": "Banner不存在" }`

