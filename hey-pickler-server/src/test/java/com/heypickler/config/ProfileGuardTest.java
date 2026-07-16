package com.heypickler.config;

import com.heypickler.common.util.ExitAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileGuardTest {

    private static final String DEV_JWT = "HeyPickler2026DevSecretK3y!MustChangeInProd!!";
    private static final String DEV_AES = "PicklerDevAesKey";
    private static final String UNIQUE_JWT = "a-unique-production-jwt-secret-32+chars-long!!";
    private static final String UNIQUE_AES = "0123456789abcdef";

    @Mock private Environment environment;
    @Mock private ExitAction exitAction;
    @Mock private ApplicationReadyEvent event;

    private ProfileGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ProfileGuard(environment, exitAction);
    }

    @Test
    void passesWhenProdProfileAndUniqueSecrets() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(environment.getProperty("hey-pickler.wechat.dev-mode")).thenReturn("false");
        when(environment.getProperty("hey-pickler.jwt.secret")).thenReturn(UNIQUE_JWT);
        when(environment.getProperty("hey-pickler.aes.key")).thenReturn(UNIQUE_AES);
        when(environment.getProperty("PROD_GUARD")).thenReturn(null);

        guard.onApplicationEvent(event);

        verify(exitAction, never()).exit(anyInt());
    }

    @Test
    void failsFastWhenProdProfileAndDevModeTrue() {
        // WX_DEV_MODE 在 prod 绝不可开（dev 登录可伪造任意 userId）
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(environment.getProperty("hey-pickler.wechat.dev-mode")).thenReturn("true");
        when(environment.getProperty("PROD_GUARD")).thenReturn(null);

        guard.onApplicationEvent(event);

        verify(exitAction).exit(2);
    }

    @Test
    void failsFastWhenProdProfileAndDevJwtSecret() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(environment.getProperty("hey-pickler.wechat.dev-mode")).thenReturn("false");
        when(environment.getProperty("hey-pickler.jwt.secret")).thenReturn(DEV_JWT);
        when(environment.getProperty("PROD_GUARD")).thenReturn(null);

        guard.onApplicationEvent(event);

        verify(exitAction).exit(2);
    }

    @Test
    void failsFastWhenProdProfileAndDevAesKey() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(environment.getProperty("hey-pickler.wechat.dev-mode")).thenReturn("false");
        when(environment.getProperty("hey-pickler.jwt.secret")).thenReturn(UNIQUE_JWT);
        when(environment.getProperty("hey-pickler.aes.key")).thenReturn(DEV_AES);
        when(environment.getProperty("PROD_GUARD")).thenReturn(null);

        guard.onApplicationEvent(event);

        verify(exitAction).exit(2);
    }

    @Test
    void failsFastWhenProdGuardTrueAndDevProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(environment.getProperty("PROD_GUARD")).thenReturn("true");

        guard.onApplicationEvent(event);

        verify(exitAction).exit(2);
    }

    @Test
    void passesWhenDevProfileWithoutProdGuard() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(environment.getProperty("PROD_GUARD")).thenReturn(null);

        guard.onApplicationEvent(event);

        verify(exitAction, never()).exit(anyInt());
    }

    @Test
    void passesWhenNoProfileAndNoProdGuard() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        when(environment.getProperty("PROD_GUARD")).thenReturn(null);

        guard.onApplicationEvent(event);

        verify(exitAction, never()).exit(anyInt());
    }

    @Test
    void passesWhenProdGuardTrueAndProdProfileWithUniqueSecrets() {
        // Both triggers active, secrets valid → pass (defense in depth still allows start)
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(environment.getProperty("hey-pickler.wechat.dev-mode")).thenReturn("false");
        when(environment.getProperty("hey-pickler.jwt.secret")).thenReturn(UNIQUE_JWT);
        when(environment.getProperty("hey-pickler.aes.key")).thenReturn(UNIQUE_AES);
        when(environment.getProperty("PROD_GUARD")).thenReturn("true");

        guard.onApplicationEvent(event);

        verify(exitAction, never()).exit(anyInt());
    }
}
