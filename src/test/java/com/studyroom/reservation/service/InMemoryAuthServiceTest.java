package com.studyroom.reservation.service;

import com.studyroom.reservation.enums.UserRole;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.session.LoginSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryAuthServiceTest {

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new InMemoryAuthService();
    }

    @Test
    void activeUserCanLogin() {
        LoginSession session = authService.login(
                "user004@studyroom.test",
                "password"
        );

        assertEquals(4L, session.getUserId());
        assertEquals(UserRole.USER, session.getRole());
    }

    @Test
    void adminCanLogin() {
        LoginSession session = authService.login(
                "admin01@studyroom.test",
                "password"
        );

        assertEquals(1L, session.getUserId());
        assertEquals(UserRole.ADMIN, session.getRole());
    }

    @Test
    void withdrawnUserCannotLogin() {
        assertThrows(BusinessException.class, () -> authService.login(
                "user111@studyroom.test",
                "password"
        ));
    }

    @Test
    void logoutInvalidatesSession() {
        LoginSession session = authService.login(
                "user004@studyroom.test",
                "password"
        );

        authService.logout(session.getSessionId());

        assertThrows(
                BusinessException.class,
                () -> authService.requireLogin(session.getSessionId())
        );
    }
}
