# user Specification Delta: 用户列表占位文案

## Modified Requirements

### Requirement: User list displays readable fallback for missing nickname
The admin user list (`/users` page) SHALL display a friendly placeholder for users whose `nickname` is empty or null.

#### Scenario: User with nickname
- **WHEN** the user has `nickname = "李明辉"`
- **THEN** the avatar shows "李" and the name shows "李明辉"

#### Scenario: User without nickname
- **WHEN** the user has `nickname = null` or empty string
- **THEN** the avatar SHALL show the Element Plus `<User />` icon (not the character `?`)
- **AND** the name field SHALL display "匿名用户" (not `-` or `?`)
