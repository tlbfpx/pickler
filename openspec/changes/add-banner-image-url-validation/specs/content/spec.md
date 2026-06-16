# content Specification Delta: Banner 图片地址校验

## Added Requirements

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

## Added Requirements

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

## Modified Requirements

### Requirement: Admin create banner (was: create with any URL)
The system SHALL provide an endpoint (`POST /api/admin/banners`) for admins to create a banner with `image_url`, `link_url`, `sort_order`, and `status`. The `image_url` MUST pass format and reachability validation.

#### Scenario: Create banner with valid URL
- **WHEN** an admin sends a POST with `{ "imageUrl": "https://cdn.example.com/b.jpg", "linkUrl": "...", "sortOrder": 1, "status": "ACTIVE" }`
- **THEN** the system SHALL create the banner and return `{ "code": 0, "data": { "id": <id> } }`

#### Scenario: Create banner with invalid URL
- **WHEN** an admin sends a POST with `{ "imageUrl": "https://test.com/x.jpg", ... }` (unreachable host)
- **THEN** the system SHALL return `{ "code": <non-zero>, "message": "图片地址校验失败: ..." }`
