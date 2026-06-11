## 1. Project Scaffold

- [x] 1.1 Create Maven project with pom.xml (Spring Boot 3.x, Java 17, all dependencies: MyBatis-Plus, Redis, jjwt, Swagger/Knife4j, Flyway, MySQL Connector, Validation)
- [x] 1.2 Create `HeyPicklerApplication.java` main class
- [x] 1.3 Create `application.yml`, `application-dev.yml`, `application-prod.yml` with database, Redis, JWT, OSS, WeChat config placeholders
- [x] 1.4 Create Flyway migration `V1__init_schema.sql` with all 8 tables (user, event, registration, point_record, ranking, admin_user, banner, ban_record) including indexes and constraints
- [x] 1.5 Create Flyway migration `V2__init_data.sql` with default SUPER_ADMIN account (username: admin, bcrypt-hashed password)

## 2. Common Infrastructure

- [x] 2.1 Create enums: `EventType`, `EventStatus`, `Tier`, `MatchType`, `RegistrationStatus`, `UserRole`, `UserStatus`, `BanAction`
- [x] 2.2 Create `Result<T>` and `PageResult<T>` unified response classes
- [x] 2.3 Create `ErrorCode` enum and `BizException` runtime exception
- [x] 2.4 Create `GlobalExceptionHandler` (handle BizException, MethodArgumentNotValidException, generic Exception)
- [x] 2.5 Create `JwtUtil` (generate, validate, parse tokens; app 7-day, admin 24-hour expiry)
- [x] 2.6 Create `AesUtil` (encrypt/decrypt with configurable key)
- [x] 2.7 Create `RedisKey` constant class with key naming convention `heypickler:<module>:<identifier>`

## 3. Configuration

- [x] 3.1 Create `MyBatisPlusConfig` (pagination interceptor, auto-fill handler for created_at/updated_at)
- [x] 3.2 Create `RedisConfig` (RedisTemplate with Jackson serialization)
- [x] 3.3 Create `SwaggerConfig` (Knife4j, enabled in dev profile only)
- [x] 3.4 Create `CorsConfig` (allow admin management domain only)
- [x] 3.5 Create `AsyncConfig` (thread pool: core 2, max 4, queue 100)
- [x] 3.6 Create `SecurityConfig` (disable Spring Security default, register custom filters)

## 4. Entity & Mapper Layer

- [x] 4.1 Create entity classes with MyBatis-Plus annotations: `User`, `Event`, `Registration`, `PointRecord`, `Ranking`, `AdminUser`, `Banner`, `BanRecord`
- [x] 4.2 Create mapper interfaces: `UserMapper`, `EventMapper`, `RegistrationMapper`, `PointRecordMapper`, `RankingMapper`, `AdminUserMapper`, `BannerMapper`, `BanRecordMapper`

## 5. Auth Module

- [x] 5.1 Create `AppAuthFilter` (validate JWT for `/api/app/*`, set userId in context, check ban status)
- [x] 5.2 Create `AdminAuthFilter` (validate JWT for `/api/admin/*`, verify Redis session, set adminId+role in context)
- [x] 5.3 Create `RateLimitFilter` (Redis token bucket: 60/min for app, 120/min for admin)
- [x] 5.4 Create `AuthService` interface and `AuthServiceImpl` (WeChat code2Session, user creation, JWT issuance, phone binding, token refresh)
- [x] 5.5 Create `AppAuthController` (login, bindPhone, refresh endpoints)
- [x] 5.6 Create `AdminAuthController` (login endpoint with Redis session storage)
- [x] 5.7 Write unit tests for `AuthService` (login, phone binding, token refresh)
- [x] 5.8 Write unit tests for auth filters (valid token, missing token, banned user, expired session)

## 6. User Module

- [x] 6.1 Create DTOs: `PhoneBindRequest`, `UserUpdateRequest`, `UserQueryRequest`
- [x] 6.2 Create VOs: `UserProfileVO`, `UserAdminVO`, `PointRecordVO`, `MyEventVO`
- [x] 6.3 Create `UserService` interface and `UserServiceImpl` (getProfile, updateProfile, getMyEvents, getPointHistory, adminListUsers, adminGetUser, adminUpdateUser)
- [x] 6.4 Create `AppUserController` (profile GET/PUT, events, points endpoints)
- [x] 6.5 Create `AdminUserController` (user list, detail, edit, ban, unban)
- [x] 6.6 Write unit tests for `UserService` (profile CRUD, ban/unban logic)

## 7. Event Module

- [x] 7.1 Create DTOs: `EventListQuery`, `RegisterRequest`, `EventCreateRequest`, `EventUpdateRequest`
- [x] 7.2 Create VOs: `EventVO`, `EventDetailVO`
- [x] 7.3 Create `EventService` interface and `EventServiceImpl` (list, detail, register, cancel, admin CRUD, soft delete, status transition validation)
- [x] 7.4 Create `AppEventController` (list, detail, register, cancel)
- [x] 7.5 Create `AdminEventController` (list, create, update, delete)
- [x] 7.6 Write unit tests for `EventService` (registration with concurrency check, cancel before deadline, status transitions, soft delete)

## 8. Ranking Module

- [x] 8.1 Create DTOs: `RankingQuery`, `PointEntryRequest`
- [x] 8.2 Create `RankingVO`
- [x] 8.3 Create `PointChangeListener` (@Async Spring Event listener for ranking refresh)
- [x] 8.4 Create `RankingService` interface and `RankingServiceImpl` (point entry with transaction, tier calculation, async ranking refresh, cache invalidation, ranking query with Redis cache-aside)
- [x] 8.5 Create `AppRankingController` (rankings endpoint)
- [x] 8.6 Create admin point entry endpoint in `AdminEventController` (POST events/{id}/points)
- [x] 8.7 Write unit tests for `RankingService` (tier calculation, ranking refresh, cache invalidation, negative points floor at 0)

## 9. Content Module

- [x] 9.1 Create DTOs: `BannerCreateRequest`
- [x] 9.2 Create `BannerVO`
- [x] 9.3 Create `BannerService` interface and `BannerServiceImpl` (list enabled, admin list all, create, update, delete)
- [x] 9.4 Create `AppBannerController` (list banners)
- [x] 9.5 Create `AdminBannerController` (list, create, update, delete)
- [x] 9.6 Write unit tests for `BannerService` (CRUD, sort order, enabled filter)

## 10. Admin Management Module

- [x] 10.1 Create DTOs: `AdminLoginRequest`, `AdminUserCreateRequest`
- [x] 10.2 Create `AdminUserService` interface and `AdminUserServiceImpl` (list, create with bcrypt, get/edit, reset password with session invalidation)
- [x] 10.3 Create `AdminAdminController` (list, create, get, edit, resetPassword — SUPER_ADMIN only)
- [x] 10.4 Write unit tests for `AdminUserService` (create with duplicate check, role validation, self-modification guard, password reset with Redis cleanup)

## 11. Integration & Verification

- [x] 11.1 Verify application starts successfully with `dev` profile against MySQL and Redis
- [x] 11.2 Verify Flyway migrations execute cleanly on empty database
- [x] 11.3 Verify Swagger UI accessible at `/doc.html` with all endpoints documented
- [x] 11.4 Run all unit tests with `mvn test` and ensure pass
- [x] 11.5 Verify CORS, rate limiting, and auth filter chain work as specified via manual API testing
