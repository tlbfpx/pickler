package com.heypickler.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${hey-pickler.jwt.secret}")
    private String secret;

    @Value("${hey-pickler.jwt.app-expiration}")
    private long appExpiration;

    @Value("${hey-pickler.jwt.admin-expiration}")
    private long adminExpiration;

    private volatile SecretKey cachedKey;

    private SecretKey getKey() {
        if (cachedKey == null) {
            cachedKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        return cachedKey;
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

    public String generateAdminToken(Long adminId, String role) {
        return Jwts.builder()
                .subject(String.valueOf(adminId))
                .claim("type", "admin")
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + adminExpiration))
                .signWith(getKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validate(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    public String getType(String token) {
        return parseToken(token).get("type", String.class);
    }

    public String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    public boolean isExpired(String token) {
        try {
            parseToken(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
