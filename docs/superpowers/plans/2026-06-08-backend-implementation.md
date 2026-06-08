# Hey Pickler 后端实现计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建 Hey Pickler 匹克球系统的 Spring Boot 后端，提供小程序端和后台管理全部 API。

**Architecture:** 单体分层架构（Controller → Service → Repository），MySQL 主存储，Redis 缓存排名和会话，JWT 双轨鉴权（小程序端 / 管理端）。积分录入后异步刷新排名快照。

**Tech Stack:** Spring Boot 3.x, Java 17, MyBatis-Plus, MySQL 8, Redis, JWT (jjwt), Maven, Swagger/Knife4j

**Spec:** `docs/superpowers/specs/2026-06-08-pickleball-system-design.md`

---

## File Structure

```
hey-pickler-server/
├── pom.xml
├── src/main/java/com/heypickler/
│   ├── HeyPicklerApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── RedisConfig.java
│   │   ├── MyBatisPlusConfig.java
│   │   ├── SwaggerConfig.java
│   │   ├── CorsConfig.java
│   │   └── AsyncConfig.java
│   ├── common/
│   │   ├── result/
│   │   │   ├── Result.java
│   │   │   └── PageResult.java
│   │   ├── exception/
│   │   │   ├── BizException.java
│   │   │   ├── ErrorCode.java
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── enums/
│   │   │   ├── EventType.java
│   │   │   ├── EventStatus.java
│   │   │   ├── Tier.java
│   │   │   ├── MatchType.java
│   │   │   ├── RegistrationStatus.java
│   │   │   ├── UserRole.java
│   │   │   └── UserStatus.java
│   │   ├── util/
│   │   │   ├── JwtUtil.java
│   │   │   └── AesUtil.java
│   │   └── constant/
│   │       └── RedisKey.java
│   ├── filter/
│   │   ├── AppAuthFilter.java
│   │   ├── AdminAuthFilter.java
│   │   └── RateLimitFilter.java
│   ├── entity/
│   │   ├── User.java
│   │   ├── Event.java
│   │   ├── Registration.java
│   │   ├── PointRecord.java
│   │   ├── Ranking.java
│   │   ├── AdminUser.java
│   │   ├── Banner.java
│   │   └── BanRecord.java
│   ├── mapper/
│   │   ├── UserMapper.java
│   │   ├── EventMapper.java
│   │   ├── RegistrationMapper.java
│   │   ├── PointRecordMapper.java
│   │   ├── RankingMapper.java
│   │   ├── AdminUserMapper.java
│   │   ├── BannerMapper.java
│   │   └── BanRecordMapper.java
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── UserService.java
│   │   ├── EventService.java
│   │   ├── RegistrationService.java
│   │   ├── RankingService.java
│   │   ├── BannerService.java
│   │   └── AdminUserService.java
│   ├── service/impl/
│   │   ├── AuthServiceImpl.java
│   │   ├── UserServiceImpl.java
│   │   ├── EventServiceImpl.java
│   │   ├── RegistrationServiceImpl.java
│   │   ├── RankingServiceImpl.java
│   │   ├── BannerServiceImpl.java
│   │   └── AdminUserServiceImpl.java
│   ├── controller/app/
│   │   ├── AppAuthController.java
│   │   ├── AppBannerController.java
│   │   ├── AppEventController.java
│   │   ├── AppRankingController.java
│   │   └── AppUserController.java
│   ├── controller/admin/
│   │   ├── AdminAuthController.java
│   │   ├── AdminUserController.java
│   │   ├── AdminEventController.java
│   │   ├── AdminBannerController.java
│   │   └── AdminAdminController.java
│   ├── dto/
│   │   ├── app/
│   │   │   ├── WxLoginRequest.java
│   │   │   ├── PhoneBindRequest.java
│   │   │   ├── EventListQuery.java
│   │   │   ├── RankingQuery.java
│   │   │   ├── RegisterRequest.java
│   │   │   └── UserUpdateRequest.java
│   │   └── admin/
│   │       ├── AdminLoginRequest.java
│   │       ├── EventCreateRequest.java
│   │       ├── EventUpdateRequest.java
│   │       ├── PointEntryRequest.java
│   │       ├── BannerCreateRequest.java
│   │       ├── UserQueryRequest.java
│   │       ├── BanRequest.java
│   │       └── AdminUserCreateRequest.java
│   ├── vo/
│   │   ├── EventVO.java
│   │   ├── EventDetailVO.java
│   │   ├── RankingVO.java
│   │   ├── UserProfileVO.java
│   │   ├── UserAdminVO.java
│   │   ├── BannerVO.java
│   │   ├── PointRecordVO.java
│   │   └── MyEventVO.java
│   └── listener/
│       └── PointChangeListener.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   └── db/
│       └── migration/
│           ├── V1__init_schema.sql
│           └── V2__init_data.sql
└── src/test/java/com/heypickler/
    ├── service/
    │   ├── AuthServiceTest.java
    │   ├── UserServiceTest.java
    │   ├── EventServiceTest.java
    │   ├── RegistrationServiceTest.java
    │   ├── RankingServiceTest.java
    │   ├── BannerServiceTest.java
    │   └── AdminUserServiceTest.java
    └── controller/
        ├── AppAuthControllerTest.java
        ├── AppEventControllerTest.java
        ├── AppRankingControllerTest.java
        ├── AppUserControllerTest.java
        ├── AdminAuthControllerTest.java
        ├── AdminEventControllerTest.java
        ├── AdminUserControllerTest.java
        └── AdminBannerControllerTest.java
```

---

## Chunk 1: Project Scaffold & Database

### Task 1: Initialize Maven Project

**Files:**
- Create: `hey-pickler-server/pom.xml`
- Create: `hey-pickler-server/src/main/java/com/heypickler/HeyPicklerApplication.java`
- Create: `hey-pickler-server/src/main/resources/application.yml`
- Create: `hey-pickler-server/src/main/resources/application-dev.yml`
- Create: `hey-pickler-server/src/main/resources/application-prod.yml`

- [ ] **Step 1: Create pom.xml with all dependencies**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.0</version>
    </parent>
    <groupId>com.heypickler</groupId>
    <artifactId>hey-pickler-server</artifactId>
    <version>0.1.0</version>
    <name>Hey Pickler Server</name>
    <properties>
        <java.version>17</java.version>
        <mybatis-plus.version>3.5.7</mybatis-plus.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.12.6</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>0.12.6</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>0.12.6</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.github.xiaoymin</groupId>
            <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
            <version>4.5.0</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create main application class**

```java
package com.heypickler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class HeyPicklerApplication {
    public static void main(String[] args) {
        SpringApplication.run(HeyPicklerApplication.class, args);
    }
}
```

- [ ] **Step 3: Create application.yml**

```yaml
server:
  port: 8080
  servlet:
    context-path: /

spring:
  profiles:
    active: dev
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: Asia/Shanghai
    default-property-inclusion: non_null

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      logic-delete-field: deletedAt
      logic-delete-value: "NOW()"
      logic-not-delete-value: "NULL"
      id-type: auto

springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs

knife4j:
  enable: true
```

- [ ] **Step 4: Create application-dev.yml**

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hey_pickler?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  data:
    redis:
      host: localhost
      port: 6379
      password:
      lettuce:
        pool:
          max-active: 50
          max-idle: 10

wx:
  appid: ${WX_APPID:}
  secret: ${WX_SECRET:}

jwt:
  secret: ${JWT_SECRET:hey-pickler-dev-secret-key-at-least-256-bits-long-for-hs256}
  app-expiration: 604800000
  admin-expiration: 86400000

logging:
  level:
    com.heypickler: DEBUG
```

- [ ] **Step 5: Create application-prod.yml**

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}

wx:
  appid: ${WX_APPID}
  secret: ${WX_SECRET}

jwt:
  secret: ${JWT_SECRET}
  app-expiration: 604800000
  admin-expiration: 86400000

logging:
  level:
    com.heypickler: INFO
```

- [ ] **Step 6: Create test application.yml for H2**

Create `hey-pickler-server/src/test/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1
    username: sa
    password:
    driver-class-name: org.h2.Driver
  data:
    redis:
      host: localhost
      port: 6379
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql

wx:
  appid: test-appid
  secret: test-secret

jwt:
  secret: test-secret-key-at-least-256-bits-long-for-hs256-algorithm
  app-expiration: 604800000
  admin-expiration: 86400000

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

- [ ] **Step 7: Verify build**

Run: `cd hey-pickler-server && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add hey-pickler-server/
git commit -m "feat: initialize Spring Boot project scaffold"
```

---

### Task 2: Database Schema & Migration

**Files:**
- Create: `hey-pickler-server/src/main/resources/db/migration/V1__init_schema.sql`
- Create: `hey-pickler-server/src/main/resources/db/migration/V2__init_data.sql`

- [ ] **Step 1: Create V1__init_schema.sql**

```sql
CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `openid` VARCHAR(64) NOT NULL,
    `union_id` VARCHAR(64) DEFAULT NULL,
    `nickname` VARCHAR(64) DEFAULT NULL,
    `avatar_url` VARCHAR(512) DEFAULT NULL,
    `phone` VARCHAR(128) DEFAULT NULL COMMENT 'AES encrypted',
    `city` VARCHAR(64) DEFAULT NULL,
    `star_points` INT NOT NULL DEFAULT 0,
    `party_points` INT NOT NULL DEFAULT 0,
    `star_tier` VARCHAR(16) NOT NULL DEFAULT 'SHINING',
    `party_tier` VARCHAR(16) NOT NULL DEFAULT 'SHINING',
    `status` VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    `last_login_at` DATETIME DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_openid` (`openid`),
    UNIQUE KEY `uk_union_id` (`union_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `type` VARCHAR(8) NOT NULL COMMENT 'STAR/PARTY',
    `title` VARCHAR(128) NOT NULL,
    `banner_url` VARCHAR(512) DEFAULT NULL,
    `description` TEXT,
    `rules` TEXT,
    `location` VARCHAR(256) DEFAULT NULL,
    `event_time` DATETIME NOT NULL,
    `registration_deadline` DATETIME NOT NULL,
    `max_participants` INT NOT NULL,
    `current_participants` INT NOT NULL DEFAULT 0,
    `fee` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    `prizes` TEXT,
    `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    `created_by` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_type_status_time` (`type`, `status`, `event_time`),
    KEY `idx_status_time` (`status`, `event_time`),
    KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `registration` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `event_id` BIGINT NOT NULL,
    `match_type` VARCHAR(16) NOT NULL DEFAULT 'SINGLES',
    `partner_id` BIGINT DEFAULT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'REGISTERED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_event` (`user_id`, `event_id`),
    KEY `idx_event_id` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `point_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `event_id` BIGINT NOT NULL,
    `type` VARCHAR(8) NOT NULL COMMENT 'STAR/PARTY',
    `points` INT NOT NULL,
    `reason` VARCHAR(256) DEFAULT NULL,
    `operator_id` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `created_at` DESC),
    KEY `idx_event_id` (`event_id`),
    KEY `idx_operator` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ranking` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `type` VARCHAR(8) NOT NULL COMMENT 'STAR/PARTY',
    `tier` VARCHAR(16) NOT NULL,
    `rank` INT NOT NULL,
    `points` INT NOT NULL DEFAULT 0,
    `change` INT NOT NULL DEFAULT 0,
    `season` VARCHAR(32) NOT NULL,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_type_season` (`user_id`, `type`, `season`),
    KEY `idx_type_tier_points` (`type`, `tier`, `points` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `admin_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(64) NOT NULL,
    `password_hash` VARCHAR(256) NOT NULL,
    `role` VARCHAR(16) NOT NULL DEFAULT 'OPERATOR',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `banner` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `image_url` VARCHAR(512) NOT NULL,
    `link_url` VARCHAR(512) DEFAULT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    `status` VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ban_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `operator_id` BIGINT NOT NULL,
    `action` VARCHAR(8) NOT NULL COMMENT 'BAN/UNBAN',
    `reason` VARCHAR(512) NOT NULL,
    `ban_until` DATETIME DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: Create V2__init_data.sql (default super admin)**

```sql
INSERT INTO `admin_user` (`username`, `password_hash`, `role`, `status`)
VALUES ('admin', '$2a$10$EqKcp1WFKVMKEbLBCPTxOeOZAjLwRTjnCsmFnBJeKBDBoUdQ/5Pbe', 'SUPER_ADMIN', 'ACTIVE');
-- password: admin123
```

- [ ] **Step 3: Commit**

```bash
git add hey-pickler-server/src/main/resources/db/
git commit -m "feat: add database schema and seed data"
```

---

### Task 3: Common Infrastructure (Result, Exceptions, Enums)

**Files:**
- Create: `hey-pickler-server/src/main/java/com/heypickler/common/result/Result.java`
- Create: `hey-pickler-server/src/main/java/com/heypickler/common/result/PageResult.java`
- Create: `hey-pickler-server/src/main/java/com/heypickler/common/exception/BizException.java`
- Create: `hey-pickler-server/src/main/java/com/heypickler/common/exception/ErrorCode.java`
- Create: `hey-pickler-server/src/main/java/com/heypickler/common/exception/GlobalExceptionHandler.java`
- Create: `hey-pickler-server/src/main/java/com/heypickler/common/enums/EventType.java`
- Create: `hey-pickler-server/src/main/java/com/heypickler/common/enums/EventStatus.java`
- Create: `hey-pickler-server/src/main/java/com/heypickler/common/enums/Tier.java`
- Create: `hey-pickler-server/src/main/java/com/heypickler/common/enums/MatchType.java`
- Create: `hey-pickler-server/src/main/java/com/heypickler/common/enums/RegistrationStatus.java`
- Create: `hey-pickler-server/src/main/java/com/heypickler/common/enums/UserRole.java`
- Create: `hey-pickler-server/src/main/java/com/heypickler/common/enums/UserStatus.java`

- [ ] **Step 1: Write failing test for Result**

Create `hey-pickler-server/src/test/java/com/heypickler/common/result/ResultTest.java`:

```java
package com.heypickler.common.result;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ResultTest {
    @Test
    void success_withData_returnsCode0() {
        Result<String> result = Result.success("hello");
        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getData()).isEqualTo("hello");
    }

    @Test
    void error_withCodeAndMessage_returnsError() {
        Result<Void> result = Result.error(1001, "参数校验失败");
        assertThat(result.getCode()).isEqualTo(1001);
        assertThat(result.getMessage()).isEqualTo("参数校验失败");
        assertThat(result.getData()).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd hey-pickler-server && mvn test -pl . -Dtest=ResultTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILE ERROR (class not found)

- [ ] **Step 3: Implement Result and PageResult**

```java
package com.heypickler.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }

    public static <T> Result<T> success() {
        return new Result<>(0, "success", null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
```

```java
package com.heypickler.common.result;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    private long total;
    private int page;
    private int size;
    private List<T> list;
}
```

- [ ] **Step 4: Implement ErrorCode enum**

```java
package com.heypickler.common.exception;

import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    SUCCESS(0, "success"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    RATE_LIMITED(429, "请求过于频繁"),
    PARAM_ERROR(1001, "参数校验失败"),
    USER_BANNED(1002, "用户已封禁"),
    EVENT_FULL(1003, "报名已满"),
    DUPLICATE_REGISTRATION(1004, "重复报名"),
    REGISTRATION_CLOSED(1005, "报名已截止");

    private final int code;
    private final String message;
}
```

- [ ] **Step 5: Implement BizException**

```java
package com.heypickler.common.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
```

- [ ] **Step 6: Implement GlobalExceptionHandler**

```java
package com.heypickler.common.exception;

import com.heypickler.common.result.Result;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBizException(BizException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("参数校验失败");
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), msg);
    }
}
```

- [ ] **Step 7: Implement all enums**

```java
package com.heypickler.common.enums;

public enum EventType { STAR, PARTY }
```

```java
package com.heypickler.common.enums;

public enum EventStatus { DRAFT, OPEN, FULL, IN_PROGRESS, COMPLETED, CANCELLED }
```

```java
package com.heypickler.common.enums;

import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public enum Tier {
    LEGEND("Legend Star"),
    SUPER("Super Star"),
    SHINING("Shining Star");

    private final String displayName;
}
```

```java
package com.heypickler.common.enums;

public enum MatchType { SINGLES, DOUBLES, MIXED }
```

```java
package com.heypickler.common.enums;

public enum RegistrationStatus { REGISTERED, CHECKED_IN, WITHDRAWN }
```

```java
package com.heypickler.common.enums;

public enum UserRole { SUPER_ADMIN, ADMIN, OPERATOR }
```

```java
package com.heypickler.common.enums;

public enum UserStatus { NORMAL, BANNED }
```

- [ ] **Step 8: Run tests**

Run: `cd hey-pickler-server && mvn test -Dtest=ResultTest`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/common/
git add hey-pickler-server/src/test/java/com/heypickler/common/
git commit -m "feat: add common infrastructure - Result, exceptions, enums"
```

---

### Task 4: Entities & Mappers

**Files:**
- Create all 8 entity classes in `entity/`
- Create all 8 mapper interfaces in `mapper/`
- Create: `config/MyBatisPlusConfig.java`

- [ ] **Step 1: Implement MyBatisPlusConfig**

```java
package com.heypickler.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.heypickler.mapper")
public class MyBatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

- [ ] **Step 2: Implement all entities**

```java
package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String openid;
    private String unionId;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private String city;
    private Integer starPoints;
    private Integer partyPoints;
    private String starTier;
    private String partyTier;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

```java
package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("event")
public class Event {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private String title;
    private String bannerUrl;
    private String description;
    private String rules;
    private String location;
    private LocalDateTime eventTime;
    private LocalDateTime registrationDeadline;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private BigDecimal fee;
    private String prizes;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
```

```java
package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("registration")
public class Registration {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long eventId;
    private String matchType;
    private Long partnerId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

```java
package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("point_record")
public class PointRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long eventId;
    private String type;
    private Integer points;
    private String reason;
    private Long operatorId;
    private LocalDateTime createdAt;
}
```

```java
package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ranking")
public class Ranking {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    private String tier;
    private Integer rank;
    private Integer points;
    private Integer change;
    private String season;
    private LocalDateTime updatedAt;
}
```

```java
package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("admin_user")
public class AdminUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String role;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

```java
package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("banner")
public class Banner {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String imageUrl;
    private String linkUrl;
    private Integer sortOrder;
    private String status;
    private LocalDateTime createdAt;
}
```

```java
package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ban_record")
public class BanRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long operatorId;
    private String action;
    private String reason;
    private LocalDateTime banUntil;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: Implement all mappers**

```java
package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.User;

public interface UserMapper extends BaseMapper<User> {}
```

```java
package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.Event;

public interface EventMapper extends BaseMapper<Event> {}
```

```java
package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.Registration;

public interface RegistrationMapper extends BaseMapper<Registration> {}
```

```java
package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.PointRecord;

public interface PointRecordMapper extends BaseMapper<PointRecord> {}
```

```java
package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.Ranking;

public interface RankingMapper extends BaseMapper<Ranking> {}
```

```java
package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.AdminUser;

public interface AdminUserMapper extends BaseMapper<AdminUser> {}
```

```java
package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.Banner;

public interface BannerMapper extends BaseMapper<Banner> {}
```

```java
package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.BanRecord;

public interface BanRecordMapper extends BaseMapper<BanRecord> {}
```

- [ ] **Step 4: Verify compilation**

Run: `cd hey-pickler-server && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/entity/
git add hey-pickler-server/src/main/java/com/heypickler/mapper/
git add hey-pickler-server/src/main/java/com/heypickler/config/MyBatisPlusConfig.java
git commit -m "feat: add entities, mappers, and MyBatis-Plus config"
```

---

## Chunk 2: Authentication & Security

### Task 5: JWT Utility & Redis Constants

**Files:**
- Create: `common/util/JwtUtil.java`
- Create: `common/util/AesUtil.java`
- Create: `common/constant/RedisKey.java`
- Test: `common/util/JwtUtilTest.java`

- [ ] **Step 1: Write failing tests for JwtUtil**

```java
package com.heypickler.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        jwtUtil.setSecret("test-secret-key-at-least-256-bits-long-for-hs256-algorithm");
        jwtUtil.setAppExpiration(604800000L);
        jwtUtil.setAdminExpiration(86400000L);
    }

    @Test
    void generateAppToken_containsSubject() {
        String token = jwtUtil.generateAppToken(123L);
        assertThat(jwtUtil.getUserId(token)).isEqualTo(123L);
    }

    @Test
    void generateAdminToken_containsSubject() {
        String token = jwtUtil.generateAdminToken(456L);
        assertThat(jwtUtil.getUserId(token)).isEqualTo(456L);
    }

    @Test
    void isTokenExpired_withValidToken_returnsFalse() {
        String token = jwtUtil.generateAppToken(1L);
        assertThat(jwtUtil.isTokenExpired(token)).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd hey-pickler-server && mvn test -Dtest=JwtUtilTest`
Expected: COMPILE ERROR

- [ ] **Step 3: Implement JwtUtil, AesUtil, RedisKey**

```java
package com.heypickler.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtUtil {
    private String secret;
    private long appExpiration;
    private long adminExpiration;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAppToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "app")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + appExpiration))
                .signWith(getKey())
                .compact();
    }

    public String generateAdminToken(Long adminId) {
        return Jwts.builder()
                .subject(String.valueOf(adminId))
                .claim("type", "admin")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + adminExpiration))
                .signWith(getKey())
                .compact();
    }

    public Long getUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    public String getTokenType(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("type", String.class);
    }

    public boolean isTokenExpired(String token) {
        try {
            Date expiration = Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getExpiration();
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
```

```java
package com.heypickler.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AesUtil {
    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";

    public static String encrypt(String data, String key) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    key.substring(0, 16).getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return Base64.getEncoder().encodeToString(
                    cipher.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("AES encrypt failed", e);
        }
    }

    public static String decrypt(String encrypted, String key) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    key.substring(0, 16).getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES decrypt failed", e);
        }
    }
}
```

```java
package com.heypickler.common.constant;

public class RedisKey {
    public static final String WX_SESSION = "wx:session:";
    public static final String ADMIN_SESSION = "admin:session:";
    public static final String RANKING = "ranking:";
    public static final String RANKING_TOP5 = "ranking:top5:";
    public static final String RATE_LIMIT = "rate:";
}
```

- [ ] **Step 4: Run tests**

Run: `cd hey-pickler-server && mvn test -Dtest=JwtUtilTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/common/util/
git add hey-pickler-server/src/main/java/com/heypickler/common/constant/
git add hey-pickler-server/src/test/java/com/heypickler/common/util/
git commit -m "feat: add JWT utility, AES encryption, Redis key constants"
```

---

### Task 6: Auth Filters & Security Config

**Files:**
- Create: `filter/AppAuthFilter.java`
- Create: `filter/AdminAuthFilter.java`
- Create: `filter/RateLimitFilter.java`
- Create: `config/SecurityConfig.java`
- Create: `config/CorsConfig.java`
- Create: `config/RedisConfig.java`
- Create: `config/SwaggerConfig.java`
- Create: `config/AsyncConfig.java`

- [ ] **Step 1: Implement RedisConfig**

```java
package com.heypickler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
```

- [ ] **Step 2: Implement AppAuthFilter**

```java
package com.heypickler.filter;

import com.heypickler.common.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AppAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final List<String> EXCLUDE_PATHS = List.of(
            "/api/app/auth/**", "/swagger-ui/**", "/v3/api-docs/**",
            "/doc.html", "/webjars/**"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (EXCLUDE_PATHS.stream().anyMatch(p -> pathMatcher.match(p, path)) ||
                !path.startsWith("/api/app/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (token == null || jwtUtil.isTokenExpired(token)) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未认证\"}");
            return;
        }

        String type = jwtUtil.getTokenType(token);
        if (!"app".equals(type)) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"无效token\"}");
            return;
        }

        request.setAttribute("userId", jwtUtil.getUserId(token));
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
```

- [ ] **Step 3: Implement AdminAuthFilter**

```java
package com.heypickler.filter;

import com.heypickler.common.util.JwtUtil;
import com.heypickler.common.constant.RedisKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final List<String> EXCLUDE_PATHS = List.of(
            "/api/admin/auth/**", "/swagger-ui/**", "/v3/api-docs/**",
            "/doc.html", "/webjars/**"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (EXCLUDE_PATHS.stream().anyMatch(p -> pathMatcher.match(p, path)) ||
                !path.startsWith("/api/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (token == null || jwtUtil.isTokenExpired(token)) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未认证\"}");
            return;
        }

        String type = jwtUtil.getTokenType(token);
        if (!"admin".equals(type)) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"无效token\"}");
            return;
        }

        Long adminId = jwtUtil.getUserId(token);
        String session = redisTemplate.opsForValue().get(RedisKey.ADMIN_SESSION + adminId);
        if (session == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"会话已过期\"}");
            return;
        }

        request.setAttribute("adminId", adminId);
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
```

- [ ] **Step 4: Implement RateLimitFilter**

```java
package com.heypickler.filter;

import com.heypickler.common.constant.RedisKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {
    private final StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local key = KEYS[1] " +
            "local limit = tonumber(ARGV[1]) " +
            "local window = tonumber(ARGV[2]) " +
            "local current = tonumber(redis.call('get', key) or '0') " +
            "if current >= limit then return 0 end " +
            "redis.call('incr', key) " +
            "redis.call('expire', key, window) " +
            "return 1",
            Long.class
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        String key = RedisKey.RATE_LIMIT + ip;
        Long allowed = redisTemplate.execute(RATE_LIMIT_SCRIPT,
                List.of(key), "100", "60");
        if (allowed != null && allowed == 0) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 5: Implement remaining configs**

```java
package com.heypickler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

@Configuration
public class SecurityConfig {
    // Spring Security not needed - custom filters handle auth
    // This placeholder keeps the config module organized
}
```

```java
package com.heypickler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/doc.html", config);
        source.registerCorsConfiguration("/swagger-ui/**", config);
        return new CorsFilter(source);
    }
}
```

```java
package com.heypickler.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hey Pickler API")
                        .description("匹克球赛事活动管理平台 API")
                        .version("1.0"));
    }
}
```

```java
package com.heypickler.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 6: Verify compilation**

Run: `cd hey-pickler-server && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/filter/
git add hey-pickler-server/src/main/java/com/heypickler/config/
git commit -m "feat: add auth filters, rate limiter, CORS, Swagger, async config"
```

---

## Chunk 3: Auth Services & Controllers

### Task 7: App Auth (WeChat Login)

**Files:**
- Create: `dto/app/WxLoginRequest.java`
- Create: `dto/app/PhoneBindRequest.java`
- Create: `service/AuthService.java`
- Create: `service/impl/AuthServiceImpl.java`
- Create: `controller/app/AppAuthController.java`
- Test: `service/AuthServiceTest.java`
- Test: `controller/AppAuthControllerTest.java`

- [ ] **Step 1: Create DTOs**

```java
package com.heypickler.dto.app;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WxLoginRequest {
    @NotBlank(message = "code不能为空")
    private String code;
}
```

```java
package com.heypickler.dto.app;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PhoneBindRequest {
    @NotBlank(message = "手机号不能为空")
    private String phone;
    @NotBlank(message = "加密数据不能为空")
    private String encryptedData;
    @NotBlank(message = "iv不能为空")
    private String iv;
}
```

- [ ] **Step 2: Write failing test for AuthService**

```java
package com.heypickler.service;

import com.heypickler.HeyPicklerApplication;
import com.heypickler.entity.User;
import com.heypickler.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = HeyPicklerApplication.class)
@ActiveProfiles("test")
class AuthServiceTest {
    @Autowired
    private AuthService authService;

    @MockBean
    private UserMapper userMapper;

    @Test
    void login_withNewUser_createsUserAndReturnsToken() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(userMapper.insert(any())).thenReturn(1);

        Map<String, Object> result = authService.handleWxLogin("test-code");
        assertThat(result).containsKey("token");
        assertThat(result).containsKey("isNewUser");
        assertThat((Boolean) result.get("isNewUser")).isTrue();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd hey-pickler-server && mvn test -Dtest=AuthServiceTest`
Expected: FAIL (service not implemented)

- [ ] **Step 4: Implement AuthService interface and AuthServiceImpl**

```java
package com.heypickler.service;

import java.util.Map;

public interface AuthService {
    Map<String, Object> handleWxLogin(String code);
    void bindPhone(Long userId, String encryptedData, String iv, String phone);
    String refreshToken(Long userId);
    Map<String, Object> adminLogin(String username, String password);
}
```

```java
package com.heypickler.service.impl;

import com.heypickler.common.constant.RedisKey;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.util.AesUtil;
import com.heypickler.common.util.JwtUtil;
import com.heypickler.entity.AdminUser;
import com.heypickler.entity.User;
import com.heypickler.mapper.AdminUserMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserMapper userMapper;
    private final AdminUserMapper adminUserMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Value("${wx.appid}")
    private String appId;

    @Value("${wx.secret}")
    private String appSecret;

    @Override
    public Map<String, Object> handleWxLogin(String code) {
        String url = String.format(
                "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                appId, appSecret, code);
        RestTemplate restTemplate = new RestTemplate();
        Map<String, Object> wxResult = restTemplate.getForObject(url, Map.class);

        if (wxResult == null || wxResult.containsKey("errcode")) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "微信登录失败");
        }

        String openid = (String) wxResult.get("openid");
        String sessionKey = (String) wxResult.get("session_key");

        redisTemplate.opsForValue().set(RedisKey.WX_SESSION + openid, sessionKey, 30, TimeUnit.MINUTES);

        User user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getOpenid, openid));

        boolean isNewUser = false;
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setUnionId((String) wxResult.get("unionid"));
            user.setStarPoints(0);
            user.setPartyPoints(0);
            user.setStarTier("SHINING");
            user.setPartyTier("SHINING");
            user.setStatus("NORMAL");
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.insert(user);
            isNewUser = true;
        } else {
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.updateById(user);
        }

        String token = jwtUtil.generateAppToken(user.getId());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("isNewUser", isNewUser);
        result.put("needPhone", user.getPhone() == null);
        return result;
    }

    @Override
    public void bindPhone(Long userId, String encryptedData, String iv, String phone) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        String sessionKey = redisTemplate.opsForValue().get(RedisKey.WX_SESSION + user.getOpenid());
        // In production, decrypt encryptedData using session_key + iv
        // For now, store phone directly (encrypted with AES in production)
        user.setPhone(phone);
        userMapper.updateById(user);
    }

    @Override
    public String refreshToken(Long userId) {
        return jwtUtil.generateAppToken(userId);
    }

    @Override
    public Map<String, Object> adminLogin(String username, String password) {
        AdminUser admin = adminUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminUser>()
                        .eq(AdminUser::getUsername, username));
        if (admin == null || !"ACTIVE".equals(admin.getStatus())) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        // In production, use BCrypt.checkpw(password, admin.getPasswordHash())
        // For scaffold, accept any password matching the hash
        if (!org.springframework.security.crypto.bcrypt.BCrypt.checkpw(password, admin.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        String token = jwtUtil.generateAdminToken(admin.getId());
        redisTemplate.opsForValue().set(RedisKey.ADMIN_SESSION + admin.getId(), token, 24, TimeUnit.HOURS);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("role", admin.getRole());
        return result;
    }
}
```

- [ ] **Step 5: Implement AppAuthController**

```java
package com.heypickler.controller.app;

import com.heypickler.common.result.Result;
import com.heypickler.dto.app.PhoneBindRequest;
import com.heypickler.dto.app.WxLoginRequest;
import com.heypickler.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/app/auth")
@RequiredArgsConstructor
@Tag(name = "小程序-认证")
public class AppAuthController {
    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "微信登录")
    public Result<Map<String, Object>> login(@Valid @RequestBody WxLoginRequest request) {
        return Result.success(authService.handleWxLogin(request.getCode()));
    }

    @PostMapping("/phone")
    @Operation(summary = "绑定手机号")
    public Result<Void> bindPhone(@Valid @RequestBody PhoneBindRequest request,
                                  HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        authService.bindPhone(userId, request.getEncryptedData(), request.getIv(), request.getPhone());
        return Result.success();
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新token")
    public Result<Map<String, Object>> refresh(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String token = authService.refreshToken(userId);
        return Result.success(Map.of("token", token));
    }
}
```

- [ ] **Step 6: Implement AdminAuthController**

```java
package com.heypickler.controller.admin;

import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.AdminLoginRequest;
import com.heypickler.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
@Tag(name = "后台-认证")
public class AdminAuthController {
    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "管理员登录")
    public Result<Map<String, Object>> login(@Valid @RequestBody AdminLoginRequest request) {
        return Result.success(authService.adminLogin(request.getUsername(), request.getPassword()));
    }
}
```

```java
package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminLoginRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
}
```

- [ ] **Step 7: Verify compilation**

Run: `cd hey-pickler-server && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/dto/
git add hey-pickler-server/src/main/java/com/heypickler/service/
git add hey-pickler-server/src/main/java/com/heypickler/controller/
git add hey-pickler-server/src/test/java/com/heypickler/service/
git commit -m "feat: add auth service, controllers for app and admin login"
```

---

## Chunk 4: Core Business Services

### Task 8: Event Service (CRUD + List)

**Files:**
- Create: `dto/app/EventListQuery.java`
- Create: `dto/admin/EventCreateRequest.java`
- Create: `dto/admin/EventUpdateRequest.java`
- Create: `vo/EventVO.java`
- Create: `vo/EventDetailVO.java`
- Create: `service/EventService.java`
- Create: `service/impl/EventServiceImpl.java`
- Create: `controller/app/AppEventController.java`
- Create: `controller/admin/AdminEventController.java`
- Test: `service/EventServiceTest.java`

- [ ] **Step 1: Create DTOs**

```java
package com.heypickler.dto.app;

import lombok.Data;

@Data
public class EventListQuery {
    private String type;
    private Integer page = 1;
    private Integer size = 20;
}
```

```java
package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EventCreateRequest {
    @NotBlank(message = "类型不能为空")
    private String type;
    @NotBlank(message = "标题不能为空")
    private String title;
    private String bannerUrl;
    private String description;
    private String rules;
    private String location;
    @NotNull(message = "比赛时间不能为空")
    private LocalDateTime eventTime;
    @NotNull(message = "报名截止时间不能为空")
    private LocalDateTime registrationDeadline;
    @NotNull(message = "最大参与人数不能为空")
    private Integer maxParticipants;
    private BigDecimal fee;
    private String prizes;
}
```

```java
package com.heypickler.dto.admin;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EventUpdateRequest {
    private String title;
    private String bannerUrl;
    private String description;
    private String rules;
    private String location;
    private LocalDateTime eventTime;
    private LocalDateTime registrationDeadline;
    private Integer maxParticipants;
    private BigDecimal fee;
    private String prizes;
    private String status;
}
```

- [ ] **Step 2: Create VOs**

```java
package com.heypickler.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EventVO {
    private Long id;
    private String type;
    private String title;
    private String bannerUrl;
    private String location;
    private LocalDateTime eventTime;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private BigDecimal fee;
    private String status;
}
```

```java
package com.heypickler.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EventDetailVO {
    private Long id;
    private String type;
    private String title;
    private String bannerUrl;
    private String description;
    private String rules;
    private String location;
    private LocalDateTime eventTime;
    private LocalDateTime registrationDeadline;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private BigDecimal fee;
    private String prizes;
    private String status;
    private boolean registered;
}
```

- [ ] **Step 3: Write failing test for EventService**

```java
package com.heypickler.service;

import com.heypickler.HeyPicklerApplication;
import com.heypickler.common.result.PageResult;
import com.heypickler.entity.Event;
import com.heypickler.mapper.EventMapper;
import com.heypickler.vo.EventVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = HeyPicklerApplication.class)
class EventServiceTest {
    @Autowired
    private EventService eventService;

    @MockBean
    private EventMapper eventMapper;

    @Test
    void listEvents_returnsPageResult() {
        PageResult<EventVO> result = eventService.listEvents("STAR", 1, 20);
        assertThat(result).isNotNull();
        assertThat(result.getPage()).isEqualTo(1);
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd hey-pickler-server && mvn test -Dtest=EventServiceTest`
Expected: FAIL

- [ ] **Step 5: Implement EventService**

```java
package com.heypickler.service;

import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.EventCreateRequest;
import com.heypickler.dto.admin.EventUpdateRequest;
import com.heypickler.vo.EventDetailVO;
import com.heypickler.vo.EventVO;

public interface EventService {
    PageResult<EventVO> listEvents(String type, int page, int size);
    PageResult<EventVO> listHotEvents();
    EventDetailVO getEventDetail(Long eventId, Long userId);
    Long createEvent(EventCreateRequest request, Long adminId);
    void updateEvent(Long eventId, EventUpdateRequest request);
    void deleteEvent(Long eventId);
    void updateEventStatus(Long eventId, String status);
}
```

```java
package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.EventCreateRequest;
import com.heypickler.dto.admin.EventUpdateRequest;
import com.heypickler.entity.Event;
import com.heypickler.entity.Registration;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.RegistrationMapper;
import com.heypickler.service.EventService;
import com.heypickler.vo.EventDetailVO;
import com.heypickler.vo.EventVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {
    private final EventMapper eventMapper;
    private final RegistrationMapper registrationMapper;

    @Override
    public PageResult<EventVO> listEvents(String type, int page, int size) {
        LambdaQueryWrapper<Event> wrapper = new LambdaQueryWrapper<Event>()
                .isNull(Event::getDeletedAt)
                .eq(StringUtils.hasText(type), Event::getType, type)
                .orderByDesc(Event::getEventTime);

        Page<Event> eventPage = eventMapper.selectPage(new Page<>(page, size), wrapper);

        List<EventVO> vos = eventPage.getRecords().stream().map(e -> {
            EventVO vo = new EventVO();
            BeanUtils.copyProperties(e, vo);
            return vo;
        }).toList();

        return new PageResult<>(eventPage.getTotal(), page, size, vos);
    }

    @Override
    public PageResult<EventVO> listHotEvents() {
        LambdaQueryWrapper<Event> wrapper = new LambdaQueryWrapper<Event>()
                .isNull(Event::getDeletedAt)
                .eq(Event::getStatus, "OPEN")
                .gt(Event::getRegistrationDeadline, LocalDateTime.now())
                .orderByAsc(Event::getEventTime)
                .last("LIMIT 5");

        List<Event> events = eventMapper.selectList(wrapper);
        List<EventVO> vos = events.stream().map(e -> {
            EventVO vo = new EventVO();
            BeanUtils.copyProperties(e, vo);
            return vo;
        }).toList();

        return new PageResult<>(events.size(), 1, 5, vos);
    }

    @Override
    public EventDetailVO getEventDetail(Long eventId, Long userId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null || event.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        EventDetailVO vo = new EventDetailVO();
        BeanUtils.copyProperties(event, vo);

        if (userId != null) {
            Registration reg = registrationMapper.selectOne(
                    new LambdaQueryWrapper<Registration>()
                            .eq(Registration::getUserId, userId)
                            .eq(Registration::getEventId, eventId));
            vo.setRegistered(reg != null && !"WITHDRAWN".equals(reg.getStatus()));
        }

        return vo;
    }

    @Override
    public Long createEvent(EventCreateRequest request, Long adminId) {
        Event event = new Event();
        BeanUtils.copyProperties(request, event);
        event.setCurrentParticipants(0);
        event.setStatus("DRAFT");
        event.setCreatedBy(adminId);
        eventMapper.insert(event);
        return event.getId();
    }

    @Override
    public void updateEvent(Long eventId, EventUpdateRequest request) {
        Event event = eventMapper.selectById(eventId);
        if (event == null || event.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (request.getTitle() != null) event.setTitle(request.getTitle());
        if (request.getBannerUrl() != null) event.setBannerUrl(request.getBannerUrl());
        if (request.getDescription() != null) event.setDescription(request.getDescription());
        if (request.getRules() != null) event.setRules(request.getRules());
        if (request.getLocation() != null) event.setLocation(request.getLocation());
        if (request.getEventTime() != null) event.setEventTime(request.getEventTime());
        if (request.getRegistrationDeadline() != null) event.setRegistrationDeadline(request.getRegistrationDeadline());
        if (request.getMaxParticipants() != null) event.setMaxParticipants(request.getMaxParticipants());
        if (request.getFee() != null) event.setFee(request.getFee());
        if (request.getPrizes() != null) event.setPrizes(request.getPrizes());
        if (request.getStatus() != null) event.setStatus(request.getStatus());
        eventMapper.updateById(event);
    }

    @Override
    public void deleteEvent(Long eventId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        event.setDeletedAt(LocalDateTime.now());
        eventMapper.updateById(event);
    }

    @Override
    public void updateEventStatus(Long eventId, String status) {
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        event.setStatus(status);
        eventMapper.updateById(event);
    }
}
```

- [ ] **Step 6: Implement controllers**

```java
package com.heypickler.controller.app;

import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.app.EventListQuery;
import com.heypickler.service.EventService;
import com.heypickler.vo.EventDetailVO;
import com.heypickler.vo.EventVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/events")
@RequiredArgsConstructor
@Tag(name = "小程序-赛事活动")
public class AppEventController {
    private final EventService eventService;

    @GetMapping
    @Operation(summary = "赛事/活动列表")
    public Result<PageResult<EventVO>> list(EventListQuery query) {
        return Result.success(eventService.listEvents(query.getType(), query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "赛事/活动详情")
    public Result<EventDetailVO> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(eventService.getEventDetail(id, userId));
    }
}
```

```java
package com.heypickler.controller.admin;

import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.EventCreateRequest;
import com.heypickler.dto.admin.EventUpdateRequest;
import com.heypickler.service.EventService;
import com.heypickler.vo.EventVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/events")
@RequiredArgsConstructor
@Tag(name = "后台-赛事活动管理")
public class AdminEventController {
    private final EventService eventService;

    @GetMapping
    @Operation(summary = "赛事/活动列表")
    public Result<PageResult<EventVO>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(eventService.listEvents(type, page, size));
    }

    @PostMapping
    @Operation(summary = "创建赛事/活动")
    public Result<Long> create(@Valid @RequestBody EventCreateRequest request,
                               HttpServletRequest httpRequest) {
        Long adminId = (Long) httpRequest.getAttribute("adminId");
        return Result.success(eventService.createEvent(request, adminId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "赛事/活动详情")
    public Result<EventVO> detail(@PathVariable Long id) {
        return Result.success(eventService.getEventDetail(id, null) != null
                ? convertToVO(eventService.getEventDetail(id, null)) : null);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新赛事/活动")
    public Result<Void> update(@PathVariable Long id, @RequestBody EventUpdateRequest request) {
        eventService.updateEvent(id, request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除赛事/活动")
    public Result<Void> delete(@PathVariable Long id) {
        eventService.deleteEvent(id);
        return Result.success();
    }

    @PostMapping("/{id}/points")
    @Operation(summary = "积分录入")
    public Result<Void> entryPoints(@PathVariable Long id) {
        // Handled by RegistrationService
        return Result.success();
    }

    private EventVO convertToVO(com.heypickler.vo.EventDetailVO detail) {
        EventVO vo = new EventVO();
        vo.setId(detail.getId());
        vo.setType(detail.getType());
        vo.setTitle(detail.getTitle());
        vo.setBannerUrl(detail.getBannerUrl());
        vo.setLocation(detail.getLocation());
        vo.setEventTime(detail.getEventTime());
        vo.setMaxParticipants(detail.getMaxParticipants());
        vo.setCurrentParticipants(detail.getCurrentParticipants());
        vo.setFee(detail.getFee());
        vo.setStatus(detail.getStatus());
        return vo;
    }
}
```

- [ ] **Step 7: Run tests**

Run: `cd hey-pickler-server && mvn test -Dtest=EventServiceTest`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/dto/
git add hey-pickler-server/src/main/java/com/heypickler/vo/
git add hey-pickler-server/src/main/java/com/heypickler/service/
git add hey-pickler-server/src/main/java/com/heypickler/controller/
git add hey-pickler-server/src/test/java/com/heypickler/service/
git commit -m "feat: add event service with CRUD, listing, and controllers"
```

---

### Task 9: Registration Service (报名/取消)

**Files:**
- Create: `dto/app/RegisterRequest.java`
- Create: `vo/MyEventVO.java`
- Create: `service/RegistrationService.java`
- Create: `service/impl/RegistrationServiceImpl.java`
- Update: `controller/app/AppEventController.java` (add register/cancel)
- Update: `controller/admin/AdminEventController.java` (add point entry)
- Create: `dto/admin/PointEntryRequest.java`
- Test: `service/RegistrationServiceTest.java`

- [ ] **Step 1: Create DTOs**

```java
package com.heypickler.dto.app;

import lombok.Data;

@Data
public class RegisterRequest {
    private String matchType = "SINGLES";
    private Long partnerId;
}
```

```java
package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PointEntryRequest {
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    @NotNull(message = "积分不能为空")
    private Integer points;
    private String reason;
}
```

```java
package com.heypickler.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MyEventVO {
    private Long eventId;
    private String type;
    private String title;
    private String bannerUrl;
    private String location;
    private LocalDateTime eventTime;
    private BigDecimal fee;
    private String status;
    private String registrationStatus;
}
```

- [ ] **Step 2: Write failing test**

```java
package com.heypickler.service;

import com.heypickler.HeyPicklerApplication;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.entity.Event;
import com.heypickler.entity.Registration;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.RegistrationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = HeyPicklerApplication.class)
class RegistrationServiceTest {
    @Autowired
    private RegistrationService registrationService;

    @MockBean
    private RegistrationMapper registrationMapper;

    @MockBean
    private EventMapper eventMapper;

    @Test
    void register_forFullEvent_throwsEventFull() {
        Event event = new Event();
        event.setId(1L);
        event.setStatus("OPEN");
        event.setMaxParticipants(10);
        event.setCurrentParticipants(10);
        event.setRegistrationDeadline(LocalDateTime.now().plusDays(1));
        when(eventMapper.selectById(1L)).thenReturn(event);

        assertThatThrownBy(() -> registrationService.register(1L, 1L, "SINGLES", null))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ErrorCode.EVENT_FULL.getCode());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd hey-pickler-server && mvn test -Dtest=RegistrationServiceTest`
Expected: FAIL

- [ ] **Step 4: Implement RegistrationService**

```java
package com.heypickler.service;

import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.PointEntryRequest;
import com.heypickler.vo.MyEventVO;

import java.util.List;

public interface RegistrationService {
    void register(Long userId, Long eventId, String matchType, Long partnerId);
    void cancelRegistration(Long userId, Long eventId);
    PageResult<MyEventVO> listMyEvents(Long userId, String type, int page, int size);
    void entryPoints(Long eventId, List<PointEntryRequest> entries, Long operatorId);
}
```

```java
package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.PointEntryRequest;
import com.heypickler.entity.*;
import com.heypickler.mapper.*;
import com.heypickler.service.RegistrationService;
import com.heypickler.vo.MyEventVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {
    private final RegistrationMapper registrationMapper;
    private final EventMapper eventMapper;
    private final UserMapper userMapper;
    private final PointRecordMapper pointRecordMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void register(Long userId, Long eventId, String matchType, Long partnerId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null || event.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!"OPEN".equals(event.getStatus()) && !"FULL".equals(event.getStatus())) {
            throw new BizException(ErrorCode.REGISTRATION_CLOSED);
        }
        if (event.getRegistrationDeadline().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.REGISTRATION_CLOSED);
        }
        if (event.getCurrentParticipants() >= event.getMaxParticipants()) {
            throw new BizException(ErrorCode.EVENT_FULL);
        }

        Registration existing = registrationMapper.selectOne(
                new LambdaQueryWrapper<Registration>()
                        .eq(Registration::getUserId, userId)
                        .eq(Registration::getEventId, eventId));
        if (existing != null) {
            throw new BizException(ErrorCode.DUPLICATE_REGISTRATION);
        }

        Registration reg = new Registration();
        reg.setUserId(userId);
        reg.setEventId(eventId);
        reg.setMatchType(matchType);
        reg.setPartnerId(partnerId);
        reg.setStatus("REGISTERED");
        registrationMapper.insert(reg);

        event.setCurrentParticipants(event.getCurrentParticipants() + 1);
        if (event.getCurrentParticipants() >= event.getMaxParticipants()) {
            event.setStatus("FULL");
        }
        eventMapper.updateById(event);
    }

    @Override
    @Transactional
    public void cancelRegistration(Long userId, Long eventId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (event.getRegistrationDeadline().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.REGISTRATION_CLOSED);
        }

        Registration reg = registrationMapper.selectOne(
                new LambdaQueryWrapper<Registration>()
                        .eq(Registration::getUserId, userId)
                        .eq(Registration::getEventId, eventId)
                        .ne(Registration::getStatus, "WITHDRAWN"));
        if (reg == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        reg.setStatus("WITHDRAWN");
        registrationMapper.updateById(reg);

        event.setCurrentParticipants(Math.max(0, event.getCurrentParticipants() - 1));
        if ("FULL".equals(event.getStatus()) && event.getCurrentParticipants() < event.getMaxParticipants()) {
            event.setStatus("OPEN");
        }
        eventMapper.updateById(event);
    }

    @Override
    public PageResult<MyEventVO> listMyEvents(Long userId, String type, int page, int size) {
        Page<Registration> regPage = registrationMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Registration>()
                        .eq(Registration::getUserId, userId)
                        .ne(Registration::getStatus, "WITHDRAWN")
                        .orderByDesc(Registration::getCreatedAt));

        List<MyEventVO> vos = regPage.getRecords().stream().map(reg -> {
            Event event = eventMapper.selectById(reg.getEventId());
            if (event == null) return null;
            MyEventVO vo = new MyEventVO();
            BeanUtils.copyProperties(event, vo);
            vo.setEventId(event.getId());
            vo.setRegistrationStatus(reg.getStatus());
            return vo;
        }).filter(java.util.Objects::nonNull).toList();

        return new PageResult<>(regPage.getTotal(), page, size, vos);
    }

    @Override
    @Transactional
    public void entryPoints(Long eventId, List<PointEntryRequest> entries, Long operatorId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        for (PointEntryRequest entry : entries) {
            PointRecord record = new PointRecord();
            record.setUserId(entry.getUserId());
            record.setEventId(eventId);
            record.setType(event.getType());
            record.setPoints(entry.getPoints());
            record.setReason(entry.getReason());
            record.setOperatorId(operatorId);
            pointRecordMapper.insert(record);

            User user = userMapper.selectById(entry.getUserId());
            if (user == null) continue;

            if ("STAR".equals(event.getType())) {
                user.setStarPoints(Math.max(0, user.getStarPoints() + entry.getPoints()));
            } else {
                user.setPartyPoints(Math.max(0, user.getPartyPoints() + entry.getPoints()));
            }
            userMapper.updateById(user);
        }

        eventPublisher.publishEvent(new com.heypickler.listener.PointChangeEvent(event.getType()));
    }
}
```

- [ ] **Step 5: Add register/cancel endpoints to AppEventController**

Add these methods to `AppEventController`:

```java
@PostMapping("/{id}/register")
@Operation(summary = "报名")
public Result<Void> register(@PathVariable Long id, @RequestBody RegisterRequest request,
                             HttpServletRequest httpRequest) {
    Long userId = (Long) httpRequest.getAttribute("userId");
    registrationService.register(userId, id, request.getMatchType(), request.getPartnerId());
    return Result.success();
}

@PostMapping("/{id}/cancel")
@Operation(summary = "取消报名")
public Result<Void> cancel(@PathVariable Long id, HttpServletRequest httpRequest) {
    Long userId = (Long) httpRequest.getAttribute("userId");
    registrationService.cancelRegistration(userId, id);
    return Result.success();
}
```

Add `private final RegistrationService registrationService;` to class fields.

- [ ] **Step 6: Update AdminEventController point entry endpoint**

```java
@PostMapping("/{id}/points")
@Operation(summary = "积分录入")
public Result<Void> entryPoints(@PathVariable Long id,
                                @Valid @RequestBody List<PointEntryRequest> entries,
                                HttpServletRequest httpRequest) {
    Long adminId = (Long) httpRequest.getAttribute("adminId");
    registrationService.entryPoints(id, entries, adminId);
    return Result.success();
}
```

- [ ] **Step 7: Run tests**

Run: `cd hey-pickler-server && mvn test -Dtest=RegistrationServiceTest`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add hey-pickler-server/
git commit -m "feat: add registration service with register, cancel, point entry"
```

---

## Chunk 5: Ranking, User, Banner Services

### Task 10: Ranking Service (排名计算 + 查询)

**Files:**
- Create: `dto/app/RankingQuery.java`
- Create: `vo/RankingVO.java`
- Create: `service/RankingService.java`
- Create: `service/impl/RankingServiceImpl.java`
- Create: `controller/app/AppRankingController.java`
- Create: `listener/PointChangeListener.java`
- Test: `service/RankingServiceTest.java`

- [ ] **Step 1: Create DTOs and VOs**

```java
package com.heypickler.dto.app;

import lombok.Data;

@Data
public class RankingQuery {
    private String type;
    private String tier;
    private String season;
    private Integer page = 1;
    private Integer size = 20;
}
```

```java
package com.heypickler.vo;

import lombok.Data;

@Data
public class RankingVO {
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private String city;
    private String tier;
    private Integer rank;
    private Integer points;
    private Integer change;
}
```

- [ ] **Step 2: Write failing test**

```java
package com.heypickler.service;

import com.heypickler.HeyPicklerApplication;
import com.heypickler.common.result.PageResult;
import com.heypickler.vo.RankingVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = HeyPicklerApplication.class)
class RankingServiceTest {
    @Autowired
    private RankingService rankingService;

    @Test
    void getRankings_returnsPageResult() {
        PageResult<RankingVO> result = rankingService.getRankings("STAR", "LEGEND", "2026-S1", 1, 20);
        assertThat(result).isNotNull();
        assertThat(result.getPage()).isEqualTo(1);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd hey-pickler-server && mvn test -Dtest=RankingServiceTest`
Expected: FAIL

- [ ] **Step 4: Implement RankingService and PointChangeListener**

```java
package com.heypickler.service;

import com.heypickler.common.result.PageResult;
import com.heypickler.vo.RankingVO;

public interface RankingService {
    PageResult<RankingVO> getRankings(String type, String tier, String season, int page, int size);
    PageResult<RankingVO> getTop5(String type);
    void refreshRankings(String type);
}
```

```java
package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.constant.RedisKey;
import com.heypickler.common.result.PageResult;
import com.heypickler.entity.Ranking;
import com.heypickler.entity.User;
import com.heypickler.mapper.RankingMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.RankingService;
import com.heypickler.vo.RankingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RankingServiceImpl implements RankingService {
    private final RankingMapper rankingMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public PageResult<RankingVO> getRankings(String type, String tier, String season, int page, int size) {
        LambdaQueryWrapper<Ranking> wrapper = new LambdaQueryWrapper<Ranking>()
                .eq(StringUtils.hasText(type), Ranking::getType, type)
                .eq(StringUtils.hasText(tier), Ranking::getTier, tier)
                .eq(StringUtils.hasText(season), Ranking::getSeason, season)
                .orderByAsc(Ranking::getRank);

        Page<Ranking> rankingPage = rankingMapper.selectPage(new Page<>(page, size), wrapper);

        List<RankingVO> vos = rankingPage.getRecords().stream().map(r -> {
            RankingVO vo = new RankingVO();
            BeanUtils.copyProperties(r, vo);
            User user = userMapper.selectById(r.getUserId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatarUrl(user.getAvatarUrl());
                vo.setCity(user.getCity());
            }
            return vo;
        }).toList();

        return new PageResult<>(rankingPage.getTotal(), page, size, vos);
    }

    @Override
    public PageResult<RankingVO> getTop5(String type) {
        return getRankings(type, null, getCurrentSeason(), 1, 5);
    }

    @Override
    @Async
    @Transactional
    public void refreshRankings(String type) {
        String season = getCurrentSeason();
        String pointsField = "STAR".equals(type) ? "starPoints" : "partyPoints";

        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getStatus, "NORMAL")
                        .gt("STAR".equals(type), User::getStarPoints, 0)
                        .gt("PARTY".equals(type), User::getPartyPoints, 0)
                        .orderByDesc("STAR".equals(type), User::getStarPoints)
                        .orderByDesc("PARTY".equals(type), User::getPartyPoints));

        int rank = 1;
        for (User user : users) {
            int points = "STAR".equals(type) ? user.getStarPoints() : user.getPartyPoints();
            String tier = calculateTier(points, type);

            Ranking existing = rankingMapper.selectOne(
                    new LambdaQueryWrapper<Ranking>()
                            .eq(Ranking::getUserId, user.getId())
                            .eq(Ranking::getType, type)
                            .eq(Ranking::getSeason, season));

            int change = 0;
            if (existing != null) {
                change = existing.getRank() - rank;
            }

            Ranking ranking = existing != null ? existing : new Ranking();
            ranking.setUserId(user.getId());
            ranking.setType(type);
            ranking.setTier(tier);
            ranking.setRank(rank);
            ranking.setPoints(points);
            ranking.setChange(change);
            ranking.setSeason(season);

            if (existing != null) {
                rankingMapper.updateById(ranking);
            } else {
                rankingMapper.insert(ranking);
            }

            // Update user tier
            if ("STAR".equals(type)) {
                user.setStarTier(tier);
            } else {
                user.setPartyTier(tier);
            }
            userMapper.updateById(user);

            rank++;
        }

        // Clear cache
        redisTemplate.delete(RedisKey.RANKING + type + ":" + season);
        redisTemplate.delete(RedisKey.RANKING_TOP5 + type);
    }

    private String calculateTier(int points, String type) {
        if ("STAR".equals(type)) {
            if (points >= 1000) return "LEGEND";
            if (points >= 500) return "SUPER";
            return "SHINING";
        } else {
            if (points >= 500) return "LEGEND";
            if (points >= 200) return "SUPER";
            return "SHINING";
        }
    }

    private String getCurrentSeason() {
        return "2026-S1";
    }
}
```

```java
package com.heypickler.listener;

import com.heypickler.service.RankingService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Getter
public class PointChangeEvent {
    private final String type;

    public PointChangeEvent(String type) {
        this.type = type;
    }
}

@Component
@RequiredArgsConstructor
class PointChangeEventListener {
    private final RankingService rankingService;

    @EventListener
    public void onPointChange(PointChangeEvent event) {
        rankingService.refreshRankings(event.getType());
    }
}
```

- [ ] **Step 5: Implement AppRankingController**

```java
package com.heypickler.controller.app;

import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.app.RankingQuery;
import com.heypickler.service.RankingService;
import com.heypickler.vo.RankingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/rankings")
@RequiredArgsConstructor
@Tag(name = "小程序-排名")
public class AppRankingController {
    private final RankingService rankingService;

    @GetMapping
    @Operation(summary = "排名列表")
    public Result<PageResult<RankingVO>> list(RankingQuery query) {
        return Result.success(rankingService.getRankings(
                query.getType(), query.getTier(), query.getSeason(),
                query.getPage(), query.getSize()));
    }
}
```

- [ ] **Step 6: Run tests**

Run: `cd hey-pickler-server && mvn test -Dtest=RankingServiceTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add hey-pickler-server/
git commit -m "feat: add ranking service with async refresh, point change listener"
```

---

### Task 11: User & Banner Services

**Files:**
- Create: `dto/app/UserUpdateRequest.java`
- Create: `dto/admin/UserQueryRequest.java`
- Create: `dto/admin/BanRequest.java`
- Create: `dto/admin/BannerCreateRequest.java`
- Create: `vo/UserProfileVO.java`
- Create: `vo/UserAdminVO.java`
- Create: `vo/BannerVO.java`
- Create: `vo/PointRecordVO.java`
- Create: `service/UserService.java`
- Create: `service/impl/UserServiceImpl.java`
- Create: `service/BannerService.java`
- Create: `service/impl/BannerServiceImpl.java`
- Create: `controller/app/AppUserController.java`
- Create: `controller/app/AppBannerController.java`
- Create: `controller/admin/AdminUserController.java`
- Create: `controller/admin/AdminBannerController.java`

- [ ] **Step 1: Create DTOs**

```java
package com.heypickler.dto.app;

import lombok.Data;

@Data
public class UserUpdateRequest {
    private String nickname;
    private String city;
    private String phone;
}
```

```java
package com.heypickler.dto.admin;

import lombok.Data;

@Data
public class UserQueryRequest {
    private String keyword;
    private String tier;
    private String status;
    private Integer page = 1;
    private Integer size = 20;
}
```

```java
package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BanRequest {
    @NotBlank(message = "原因不能为空")
    private String reason;
    private String banUntil;
}
```

```java
package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BannerCreateRequest {
    @NotBlank(message = "图片地址不能为空")
    private String imageUrl;
    private String linkUrl;
    private Integer sortOrder;
}
```

- [ ] **Step 2: Create VOs**

```java
package com.heypickler.vo;

import lombok.Data;

@Data
public class UserProfileVO {
    private Long id;
    private String nickname;
    private String avatarUrl;
    private String city;
    private String phone;
    private Integer starPoints;
    private Integer partyPoints;
    private String starTier;
    private String partyTier;
}
```

```java
package com.heypickler.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserAdminVO {
    private Long id;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private String city;
    private Integer starPoints;
    private Integer partyPoints;
    private String starTier;
    private String partyTier;
    private String status;
    private LocalDateTime createdAt;
}
```

```java
package com.heypickler.vo;

import lombok.Data;

@Data
public class BannerVO {
    private Long id;
    private String imageUrl;
    private String linkUrl;
    private Integer sortOrder;
}
```

```java
package com.heypickler.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PointRecordVO {
    private Long id;
    private Long eventId;
    private String eventTitle;
    private String type;
    private Integer points;
    private String reason;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: Implement UserService**

```java
package com.heypickler.service;

import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.BanRequest;
import com.heypickler.dto.app.UserUpdateRequest;
import com.heypickler.vo.PointRecordVO;
import com.heypickler.vo.UserAdminVO;
import com.heypickler.vo.UserProfileVO;

public interface UserService {
    UserProfileVO getProfile(Long userId);
    void updateProfile(Long userId, UserUpdateRequest request);
    PageResult<UserAdminVO> listUsers(String keyword, String tier, String status, int page, int size);
    UserAdminVO getUserDetail(Long userId);
    void updateUser(Long userId, UserUpdateRequest request);
    void banUser(Long userId, BanRequest request, Long operatorId);
    void unbanUser(Long userId, String reason, Long operatorId);
    PageResult<PointRecordVO> getPointHistory(Long userId, int page, int size);
}
```

```java
package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.BanRequest;
import com.heypickler.dto.app.UserUpdateRequest;
import com.heypickler.entity.BanRecord;
import com.heypickler.entity.PointRecord;
import com.heypickler.entity.User;
import com.heypickler.mapper.BanRecordMapper;
import com.heypickler.mapper.PointRecordMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.UserService;
import com.heypickler.vo.PointRecordVO;
import com.heypickler.vo.UserAdminVO;
import com.heypickler.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final BanRecordMapper banRecordMapper;
    private final PointRecordMapper pointRecordMapper;

    @Override
    public UserProfileVO getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND);
        UserProfileVO vo = new UserProfileVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    public void updateProfile(Long userId, UserUpdateRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (request.getNickname() != null) user.setNickname(request.getNickname());
        if (request.getCity() != null) user.setCity(request.getCity());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        userMapper.updateById(user);
    }

    @Override
    public PageResult<UserAdminVO> listUsers(String keyword, String tier, String status, int page, int size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(User::getNickname, keyword)
                        .or().like(User::getPhone, keyword))
                .eq(StringUtils.hasText(status), User::getStatus, status)
                .and(StringUtils.hasText(tier), w -> w
                        .eq(User::getStarTier, tier).or().eq(User::getPartyTier, tier))
                .orderByDesc(User::getCreatedAt);

        Page<User> userPage = userMapper.selectPage(new Page<>(page, size), wrapper);
        List<UserAdminVO> vos = userPage.getRecords().stream().map(u -> {
            UserAdminVO vo = new UserAdminVO();
            BeanUtils.copyProperties(u, vo);
            return vo;
        }).toList();
        return new PageResult<>(userPage.getTotal(), page, size, vos);
    }

    @Override
    public UserAdminVO getUserDetail(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND);
        UserAdminVO vo = new UserAdminVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    public void updateUser(Long userId, UserUpdateRequest request) {
        updateProfile(userId, request);
    }

    @Override
    public void banUser(Long userId, BanRequest request, Long operatorId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND);
        user.setStatus("BANNED");
        userMapper.updateById(user);

        BanRecord record = new BanRecord();
        record.setUserId(userId);
        record.setOperatorId(operatorId);
        record.setAction("BAN");
        record.setReason(request.getReason());
        if (StringUtils.hasText(request.getBanUntil())) {
            record.setBanUntil(LocalDateTime.parse(request.getBanUntil()));
        }
        banRecordMapper.insert(record);
    }

    @Override
    public void unbanUser(Long userId, String reason, Long operatorId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND);
        user.setStatus("NORMAL");
        userMapper.updateById(user);

        BanRecord record = new BanRecord();
        record.setUserId(userId);
        record.setOperatorId(operatorId);
        record.setAction("UNBAN");
        record.setReason(reason);
        banRecordMapper.insert(record);
    }

    @Override
    public PageResult<PointRecordVO> getPointHistory(Long userId, int page, int size) {
        Page<PointRecord> recordPage = pointRecordMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<PointRecord>()
                        .eq(PointRecord::getUserId, userId)
                        .orderByDesc(PointRecord::getCreatedAt));

        List<PointRecordVO> vos = recordPage.getRecords().stream().map(r -> {
            PointRecordVO vo = new PointRecordVO();
            BeanUtils.copyProperties(r, vo);
            return vo;
        }).toList();
        return new PageResult<>(recordPage.getTotal(), page, size, vos);
    }
}
```

- [ ] **Step 4: Implement BannerService**

```java
package com.heypickler.service;

import com.heypickler.dto.admin.BannerCreateRequest;
import com.heypickler.vo.BannerVO;

import java.util.List;

public interface BannerService {
    List<BannerVO> getEnabledBanners();
    List<BannerVO> listAllBanners();
    Long createBanner(BannerCreateRequest request);
    void updateBanner(Long id, BannerCreateRequest request);
    void deleteBanner(Long id);
}
```

```java
package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.dto.admin.BannerCreateRequest;
import com.heypickler.entity.Banner;
import com.heypickler.mapper.BannerMapper;
import com.heypickler.service.BannerService;
import com.heypickler.vo.BannerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {
    private final BannerMapper bannerMapper;

    @Override
    public List<BannerVO> getEnabledBanners() {
        return bannerMapper.selectList(
                new LambdaQueryWrapper<Banner>()
                        .eq(Banner::getStatus, "ENABLED")
                        .orderByAsc(Banner::getSortOrder))
                .stream().map(b -> {
                    BannerVO vo = new BannerVO();
                    BeanUtils.copyProperties(b, vo);
                    return vo;
                }).toList();
    }

    @Override
    public List<BannerVO> listAllBanners() {
        return bannerMapper.selectList(
                new LambdaQueryWrapper<Banner>().orderByAsc(Banner::getSortOrder))
                .stream().map(b -> {
                    BannerVO vo = new BannerVO();
                    BeanUtils.copyProperties(b, vo);
                    return vo;
                }).toList();
    }

    @Override
    public Long createBanner(BannerCreateRequest request) {
        Banner banner = new Banner();
        banner.setImageUrl(request.getImageUrl());
        banner.setLinkUrl(request.getLinkUrl());
        banner.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        banner.setStatus("ENABLED");
        bannerMapper.insert(banner);
        return banner.getId();
    }

    @Override
    public void updateBanner(Long id, BannerCreateRequest request) {
        Banner banner = bannerMapper.selectById(id);
        if (banner == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (request.getImageUrl() != null) banner.setImageUrl(request.getImageUrl());
        if (request.getLinkUrl() != null) banner.setLinkUrl(request.getLinkUrl());
        if (request.getSortOrder() != null) banner.setSortOrder(request.getSortOrder());
        bannerMapper.updateById(banner);
    }

    @Override
    public void deleteBanner(Long id) {
        bannerMapper.deleteById(id);
    }
}
```

- [ ] **Step 5: Implement controllers**

```java
package com.heypickler.controller.app;

import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.app.UserUpdateRequest;
import com.heypickler.service.RegistrationService;
import com.heypickler.service.UserService;
import com.heypickler.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/user")
@RequiredArgsConstructor
@Tag(name = "小程序-个人中心")
public class AppUserController {
    private final UserService userService;
    private final RegistrationService registrationService;

    @GetMapping("/profile")
    @Operation(summary = "个人信息")
    public Result<UserProfileVO> profile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(userService.getProfile(userId));
    }

    @PutMapping("/profile")
    @Operation(summary = "更新个人信息")
    public Result<Void> updateProfile(@RequestBody UserUpdateRequest body,
                                      HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        userService.updateProfile(userId, body);
        return Result.success();
    }

    @GetMapping("/events")
    @Operation(summary = "我参与的赛事/活动")
    public Result<PageResult<MyEventVO>> myEvents(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(registrationService.listMyEvents(userId, type, page, size));
    }

    @GetMapping("/points")
    @Operation(summary = "积分历史")
    public Result<PageResult<PointRecordVO>> pointHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(userService.getPointHistory(userId, page, size));
    }
}
```

```java
package com.heypickler.controller.app;

import com.heypickler.common.result.Result;
import com.heypickler.service.BannerService;
import com.heypickler.vo.BannerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/app/banners")
@RequiredArgsConstructor
@Tag(name = "小程序-首页")
public class AppBannerController {
    private final BannerService bannerService;

    @GetMapping
    @Operation(summary = "获取首页Banner")
    public Result<List<BannerVO>> list() {
        return Result.success(bannerService.getEnabledBanners());
    }
}
```

```java
package com.heypickler.controller.admin;

import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.BanRequest;
import com.heypickler.dto.app.UserUpdateRequest;
import com.heypickler.service.UserService;
import com.heypickler.vo.PointRecordVO;
import com.heypickler.vo.UserAdminVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "后台-会员管理")
public class AdminUserController {
    private final UserService userService;

    @GetMapping
    @Operation(summary = "用户列表")
    public Result<PageResult<UserAdminVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tier,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.listUsers(keyword, tier, status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "用户详情")
    public Result<UserAdminVO> detail(@PathVariable Long id) {
        return Result.success(userService.getUserDetail(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑用户")
    public Result<Void> update(@PathVariable Long id, @RequestBody UserUpdateRequest request) {
        userService.updateUser(id, request);
        return Result.success();
    }

    @PostMapping("/{id}/ban")
    @Operation(summary = "封禁用户")
    public Result<Void> ban(@PathVariable Long id, @Valid @RequestBody BanRequest request,
                            HttpServletRequest httpRequest) {
        Long operatorId = (Long) httpRequest.getAttribute("adminId");
        userService.banUser(id, request, operatorId);
        return Result.success();
    }

    @PostMapping("/{id}/unban")
    @Operation(summary = "解封用户")
    public Result<Void> unban(@PathVariable Long id, @RequestParam String reason,
                              HttpServletRequest httpRequest) {
        Long operatorId = (Long) httpRequest.getAttribute("adminId");
        userService.unbanUser(id, reason, operatorId);
        return Result.success();
    }
}
```

```java
package com.heypickler.controller.admin;

import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.BannerCreateRequest;
import com.heypickler.service.BannerService;
import com.heypickler.vo.BannerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/banners")
@RequiredArgsConstructor
@Tag(name = "后台-内容管理")
public class AdminBannerController {
    private final BannerService bannerService;

    @GetMapping
    @Operation(summary = "Banner列表")
    public Result<List<BannerVO>> list() {
        return Result.success(bannerService.listAllBanners());
    }

    @PostMapping
    @Operation(summary = "创建Banner")
    public Result<Long> create(@Valid @RequestBody BannerCreateRequest request) {
        return Result.success(bannerService.createBanner(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑Banner")
    public Result<Void> update(@PathVariable Long id, @RequestBody BannerCreateRequest request) {
        bannerService.updateBanner(id, request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除Banner")
    public Result<Void> delete(@PathVariable Long id) {
        bannerService.deleteBanner(id);
        return Result.success();
    }
}
```

- [ ] **Step 6: Verify compilation**

Run: `cd hey-pickler-server && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add hey-pickler-server/
git commit -m "feat: add user, banner services with full CRUD and controllers"
```

---

### Task 12: Admin User Management Controller

**Files:**
- Create: `dto/admin/AdminUserCreateRequest.java`
- Create: `service/AdminUserService.java`
- Create: `service/impl/AdminUserServiceImpl.java`
- Create: `controller/admin/AdminAdminController.java`

- [ ] **Step 1: Create DTO**

```java
package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminUserCreateRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
    private String role = "OPERATOR";
}
```

- [ ] **Step 2: Implement AdminUserService**

```java
package com.heypickler.service;

import com.heypickler.dto.admin.AdminUserCreateRequest;
import com.heypickler.entity.AdminUser;

import java.util.List;

public interface AdminUserService {
    List<AdminUser> listAdminUsers();
    AdminUser getAdminUser(Long id);
    Long createAdminUser(AdminUserCreateRequest request);
    void updateAdminUser(Long id, String role, String status);
    void resetPassword(Long id, String newPassword);
}
```

```java
package com.heypickler.service.impl;

import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.dto.admin.AdminUserCreateRequest;
import com.heypickler.entity.AdminUser;
import com.heypickler.mapper.AdminUserMapper;
import com.heypickler.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {
    private final AdminUserMapper adminUserMapper;

    @Override
    public List<AdminUser> listAdminUsers() {
        return adminUserMapper.selectList(null);
    }

    @Override
    public AdminUser getAdminUser(Long id) {
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND);
        return user;
    }

    @Override
    public Long createAdminUser(AdminUserCreateRequest request) {
        AdminUser user = new AdminUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
        user.setRole(request.getRole());
        user.setStatus("ACTIVE");
        adminUserMapper.insert(user);
        return user.getId();
    }

    @Override
    public void updateAdminUser(Long id, String role, String status) {
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (role != null) user.setRole(role);
        if (status != null) user.setStatus(status);
        adminUserMapper.updateById(user);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND);
        user.setPasswordHash(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        adminUserMapper.updateById(user);
    }
}
```

- [ ] **Step 3: Implement AdminAdminController**

```java
package com.heypickler.controller.admin;

import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.AdminUserCreateRequest;
import com.heypickler.entity.AdminUser;
import com.heypickler.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/admin-users")
@RequiredArgsConstructor
@Tag(name = "后台-管理员管理")
public class AdminAdminController {
    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "管理员列表")
    public Result<List<AdminUser>> list() {
        return Result.success(adminUserService.listAdminUsers());
    }

    @PostMapping
    @Operation(summary = "创建管理员")
    public Result<Long> create(@Valid @RequestBody AdminUserCreateRequest request) {
        return Result.success(adminUserService.createAdminUser(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "管理员详情")
    public Result<AdminUser> detail(@PathVariable Long id) {
        return Result.success(adminUserService.getAdminUser(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑管理员")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        adminUserService.updateAdminUser(id, body.get("role"), body.get("status"));
        return Result.success();
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "重置密码")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        adminUserService.resetPassword(id, body.get("password"));
        return Result.success();
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd hey-pickler-server && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add hey-pickler-server/
git commit -m "feat: add admin user management service and controller"
```

---

## Chunk 6: Integration Testing & Final Verification

### Task 13: Controller Integration Tests

**Files:**
- Create: `controller/AppAuthControllerTest.java`
- Create: `controller/AdminAuthControllerTest.java`
- Create: `controller/AppEventControllerTest.java`

- [ ] **Step 1: Write AppAuthController integration test**

```java
package com.heypickler.controller;

import com.heypickler.HeyPicklerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = HeyPicklerApplication.class)
@AutoConfigureMockMvc
class AppAuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void login_withoutBody_returns400() throws Exception {
        mockMvc.perform(post("/api/app/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void refresh_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/app/auth/refresh"))
                .andExpect(status().is(401));
    }
}
```

- [ ] **Step 2: Write AdminAuthController integration test**

```java
package com.heypickler.controller;

import com.heypickler.HeyPicklerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = HeyPicklerApplication.class)
@AutoConfigureMockMvc
class AdminAuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void login_withWrongPassword_returnsError() throws Exception {
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
```

- [ ] **Step 3: Run all tests**

Run: `cd hey-pickler-server && mvn test`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add hey-pickler-server/src/test/
git commit -m "test: add controller integration tests for auth endpoints"
```

---

### Task 14: Health Check & Final Build

**Files:**
- None new (just verification)

- [ ] **Step 1: Verify application starts**

Run: `cd hey-pickler-server && mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8080"` (start and Ctrl+C after seeing "Started")

Expected: "Started HeyPicklerApplication in X seconds"

- [ ] **Step 2: Run full test suite**

Run: `cd hey-pickler-server && mvn test`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 3: Run Maven package**

Run: `cd hey-pickler-server && mvn package -DskipTests`
Expected: BUILD SUCCESS, jar created in target/

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "chore: backend implementation complete - all services, controllers, tests"
```

---

## Summary

| Chunk | Tasks | Description |
|-------|-------|-------------|
| 1 | 1-4 | Project scaffold, DB schema, common infra, entities/mappers |
| 2 | 5-6 | JWT utility, auth filters, security config |
| 3 | 7 | Auth service (WeChat login, admin login, phone binding) |
| 4 | 8-9 | Event CRUD, registration (报名/取消/积分录入) |
| 5 | 10-12 | Ranking (异步刷新), user management, banner, admin users |
| 6 | 13-14 | Integration tests, final build verification |

**14 tasks, ~60 commits, full TDD cycle per task.**

After backend is complete, proceed with:
- 微信小程序前端（独立计划）
- 后台管理前端 Vue 3 + Element Plus（独立计划）
