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
The system SHALL provide an endpoint (`POST /api/admin/banners`) for admins to create a banner with `image_url`, `link_url`, `sort_order`, and `status`. The `image_url` MUST pass both format validation and reachability (HEAD) pre-check.

#### Scenario: Create banner
- **WHEN** an admin sends a POST with `{ "imageUrl": "https://cdn.example.com/b.jpg", "linkUrl": "...", "sortOrder": 1, "status": "ACTIVE" }`
- **THEN** the system SHALL create the banner and return `{ "code": 0, "data": { "id": <id> } }`

#### Scenario: Create banner with invalid URL format
- **WHEN** an admin sends a POST with `imageUrl = "http://cdn.example.com/b.jpg"` (HTTP, not HTTPS)
- **THEN** the system SHALL return `{ "code": 1001, "message": "..." }` indicating https is required

#### Scenario: Create banner with unreachable URL
- **WHEN** an admin sends a POST with `imageUrl = "https://test.com/x.jpg"` (host unreachable)
- **THEN** the system SHALL return `{ "code": 1001, "message": "图片地址校验失败: ..." }` and SHALL NOT persist the banner

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

### Requirement: Banner 图片地址格式
The system SHALL reject any banner `imageUrl` that does not match `^https://[^/]+/.*\.(jpg|jpeg|png|webp|gif)(\?.*)?$` (case-insensitive) at both the DTO validation layer and the frontend form layer.

#### Scenario: 合法 https 图片 URL
- **WHEN** admin submits `imageUrl = "https://cdn.example.com/banners/abc.jpg"`
- **THEN** the system SHALL accept the value and proceed to reachability check

#### Scenario: HTTP 协议被拒
- **WHEN** admin submits `imageUrl = "http://cdn.example.com/banners/abc.jpg"`
- **THEN** the system SHALL return 400 with message indicating https is required

#### Scenario: 无图片扩展名被拒
- **WHEN** admin submits `imageUrl = "https://cdn.example.com/banners/abc"`
- **THEN** the system SHALL return 400 with message indicating image extension is required

#### Scenario: 非图片扩展名被拒
- **WHEN** admin submits `imageUrl = "https://cdn.example.com/banners/abc.txt"`
- **THEN** the system SHALL return 400

### Requirement: Banner 图片地址可达性预检
The system SHALL HEAD-request the `imageUrl` before persisting a banner on create or update, requiring HTTP 2xx and `Content-Type: image/*`, with 3-second timeout on both connect and read.

#### Scenario: 可达且为图片
- **WHEN** HEAD returns `200 OK` with `Content-Type: image/jpeg`
- **THEN** the system SHALL persist the banner

#### Scenario: HTTP 404
- **WHEN** HEAD returns `404 Not Found`
- **THEN** the system SHALL return 400 with message "图片地址不可访问（HTTP 404）"

#### Scenario: 非 image Content-Type
- **WHEN** HEAD returns `200 OK` with `Content-Type: text/html`
- **THEN** the system SHALL return 400 with message indicating non-image content

#### Scenario: 连接超时
- **WHEN** the HEAD request times out (no response within 3s)
- **THEN** the system SHALL return 400 with message "图片地址校验失败: ..."

