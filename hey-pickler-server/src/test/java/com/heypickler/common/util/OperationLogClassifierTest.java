package com.heypickler.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperationLogClassifierTest {

    @Test
    void postUsers_create() {
        OperationLogClassifier.Classification c = OperationLogClassifier.classify("POST", "/api/admin/users");
        assertEquals("USER", c.module);
        assertEquals("CREATE", c.action);
        assertEquals("User", c.targetType);
        assertNull(c.targetId);
    }

    @Test
    void postUserBan_ban() {
        OperationLogClassifier.Classification c = OperationLogClassifier.classify("POST", "/api/admin/users/123/ban");
        assertEquals("USER", c.module);
        assertEquals("BAN", c.action);
        assertEquals("User", c.targetType);
        assertEquals("123", c.targetId);
    }

    @Test
    void postUserUnban_unban() {
        OperationLogClassifier.Classification c = OperationLogClassifier.classify("POST", "/api/admin/users/123/unban");
        assertEquals("USER", c.module);
        assertEquals("UNBAN", c.action);
        assertEquals("123", c.targetId);
    }

    @Test
    void putUser_update() {
        OperationLogClassifier.Classification c = OperationLogClassifier.classify("PUT", "/api/admin/users/42");
        assertEquals("USER", c.module);
        assertEquals("UPDATE", c.action);
        assertEquals("42", c.targetId);
    }

    @Test
    void deleteBanner_delete() {
        OperationLogClassifier.Classification c = OperationLogClassifier.classify("DELETE", "/api/admin/banners/7");
        assertEquals("BANNER", c.module);
        assertEquals("DELETE", c.action);
        assertEquals("7", c.targetId);
    }

    @Test
    void postBanners_create() {
        OperationLogClassifier.Classification c = OperationLogClassifier.classify("POST", "/api/admin/banners");
        assertEquals("BANNER", c.module);
        assertEquals("CREATE", c.action);
    }

    @Test
    void allModuleMappings() {
        assertEquals("USER", OperationLogClassifier.classify("POST", "/api/admin/users").module);
        assertEquals("EVENT", OperationLogClassifier.classify("POST", "/api/admin/events").module);
        assertEquals("BANNER", OperationLogClassifier.classify("POST", "/api/admin/banners").module);
        assertEquals("ADMIN", OperationLogClassifier.classify("POST", "/api/admin/admins").module);
        assertEquals("RANKING", OperationLogClassifier.classify("POST", "/api/admin/rankings").module);
        assertEquals("BAN_RECORD", OperationLogClassifier.classify("POST", "/api/admin/ban-records").module);
        assertEquals("AUTH", OperationLogClassifier.classify("POST", "/api/admin/auth/login").module);
    }

    @Test
    void loginEndpoint_actionLogin() {
        OperationLogClassifier.Classification c = OperationLogClassifier.classify("POST", "/api/admin/auth/login");
        assertEquals("AUTH", c.module);
        assertEquals("LOGIN", c.action);
    }

    @Test
    void unknownPath_fallsBackToRaw() {
        OperationLogClassifier.Classification c = OperationLogClassifier.classify("POST", "/api/admin/unknown-resource");
        assertEquals("RAW", c.module);
        assertEquals("RAW", c.action);
    }

    @Test
    void queryString_stripped() {
        OperationLogClassifier.Classification c = OperationLogClassifier.classify("POST", "/api/admin/users?foo=bar");
        assertEquals("USER", c.module);
        assertEquals("CREATE", c.action);
    }
}
