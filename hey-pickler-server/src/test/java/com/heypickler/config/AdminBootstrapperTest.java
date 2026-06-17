package com.heypickler.config;

import com.heypickler.common.util.ExitAction;
import com.heypickler.entity.AdminUser;
import com.heypickler.mapper.AdminUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapperTest {

    @Mock private AdminUserMapper adminUserMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ExitAction exitAction;
    @Mock private Environment environment;

    private AdminBootstrapper bootstrapper;

    @BeforeEach
    void setUp() {
        bootstrapper = new AdminBootstrapper(adminUserMapper, passwordEncoder, exitAction, environment);
    }

    @Test
    void skipsBootstrapWhenTableNotEmpty() {
        when(adminUserMapper.selectCount(null)).thenReturn(5L);

        bootstrapper.run(new DefaultApplicationArguments());

        verify(exitAction, never()).exit(anyInt());
        verify(adminUserMapper, never()).insert(any());
        // No env lookup should happen when table is non-empty
        verifyNoInteractions(environment);
    }

    @Test
    void createsAdminWhenTableEmptyAndEnvSet() {
        when(adminUserMapper.selectCount(null)).thenReturn(0L);
        when(environment.getProperty("INITIAL_ADMIN_USERNAME")).thenReturn("bootstrapadmin");
        when(environment.getProperty("INITIAL_ADMIN_PASSWORD")).thenReturn("strong-password-12");
        when(passwordEncoder.encode("strong-password-12")).thenReturn("$2a$10$hashed");

        bootstrapper.run(new DefaultApplicationArguments());

        ArgumentCaptor<AdminUser> captor = ArgumentCaptor.forClass(AdminUser.class);
        verify(adminUserMapper).insert(captor.capture());
        AdminUser inserted = captor.getValue();
        assertEquals("bootstrapadmin", inserted.getUsername());
        assertEquals("$2a$10$hashed", inserted.getPasswordHash());
        assertEquals("SUPER_ADMIN", inserted.getRole());
        assertEquals("ACTIVE", inserted.getStatus());
        verify(exitAction, never()).exit(anyInt());
    }

    @Test
    void failsFastWhenTableEmptyAndPasswordMissing() {
        when(adminUserMapper.selectCount(null)).thenReturn(0L);
        when(environment.getProperty("INITIAL_ADMIN_USERNAME")).thenReturn(null);
        when(environment.getProperty("INITIAL_ADMIN_PASSWORD")).thenReturn(null);

        bootstrapper.run(new DefaultApplicationArguments());

        verify(exitAction).exit(1);
        verify(adminUserMapper, never()).insert(any());
    }

    @Test
    void failsFastWhenTableEmptyAndPasswordEmpty() {
        when(adminUserMapper.selectCount(null)).thenReturn(0L);
        when(environment.getProperty("INITIAL_ADMIN_USERNAME")).thenReturn(null);
        when(environment.getProperty("INITIAL_ADMIN_PASSWORD")).thenReturn("");

        bootstrapper.run(new DefaultApplicationArguments());

        verify(exitAction).exit(1);
        verify(adminUserMapper, never()).insert(any());
    }

    @Test
    void defaultsUsernameToAdminWhenEnvMissing() {
        when(adminUserMapper.selectCount(null)).thenReturn(0L);
        when(environment.getProperty("INITIAL_ADMIN_USERNAME")).thenReturn(null);
        when(environment.getProperty("INITIAL_ADMIN_PASSWORD")).thenReturn("strong-password-12");
        when(passwordEncoder.encode("strong-password-12")).thenReturn("$2a$10$hashed");

        bootstrapper.run(new DefaultApplicationArguments());

        ArgumentCaptor<AdminUser> captor = ArgumentCaptor.forClass(AdminUser.class);
        verify(adminUserMapper).insert(captor.capture());
        assertEquals("admin", captor.getValue().getUsername());
    }

    @Test
    void createsAdminEvenWithShortPassword() {
        when(adminUserMapper.selectCount(null)).thenReturn(0L);
        when(environment.getProperty("INITIAL_ADMIN_USERNAME")).thenReturn(null);
        when(environment.getProperty("INITIAL_ADMIN_PASSWORD")).thenReturn("short");
        when(passwordEncoder.encode("short")).thenReturn("$2a$10$hashed");

        bootstrapper.run(new DefaultApplicationArguments());

        verify(adminUserMapper).insert(any());
        verify(exitAction, never()).exit(anyInt());
    }
}
