package com.studyroom.reservation.service;

import com.studyroom.reservation.enums.UserRole;
import com.studyroom.reservation.enums.UserStatus;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.session.LoginSession;
import com.studyroom.reservation.session.SessionManager;
import com.studyroom.reservation.util.PasswordUtil;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Issue #5와 #6을 먼저 개발하기 위한 임시 인증 구현체입니다.
 * 회원 데이터는 메모리에 저장되며 애플리케이션 종료 시 사라집니다.
 */
public final class InMemoryAuthService implements AuthService {

    private static final String LOGIN_FAILURE_MESSAGE =
            "이메일 또는 비밀번호가 올바르지 않습니다.";

    private final AtomicLong userIdSequence = new AtomicLong(121L);
    private final Map<String, TemporaryUser> usersByEmail =
            new ConcurrentHashMap<>();
    private final SessionManager sessionManager = new SessionManager();

    public InMemoryAuthService() {
        addDevelopmentUser(
                1L,
                "admin01@studyroom.test",
                "password",
                UserRole.ADMIN,
                UserStatus.ACTIVE
        );
        addDevelopmentUser(
                4L,
                "user004@studyroom.test",
                "password",
                UserRole.USER,
                UserStatus.ACTIVE
        );
        addDevelopmentUser(
                111L,
                "user111@studyroom.test",
                "password",
                UserRole.USER,
                UserStatus.WITHDRAWN
        );
        addDevelopmentUser(
                121L,
                "user121@studyroom.test",
                "password",
                UserRole.USER,
                UserStatus.ACTIVE
        );
    }

    @Override
    public void signUp(String email, String rawPassword, String name) {
        String normalizedEmail = normalizeEmail(email);
        requireNotBlank(rawPassword, "비밀번호");
        requireNotBlank(name, "이름");

        TemporaryUser newUser = new TemporaryUser(
                userIdSequence.getAndIncrement(),
                normalizedEmail,
                PasswordUtil.hash(rawPassword),
                UserRole.USER,
                UserStatus.ACTIVE
        );

        TemporaryUser existingUser = usersByEmail.putIfAbsent(
                normalizedEmail,
                newUser
        );

        if (existingUser != null) {
            throw new BusinessException("이미 사용 중인 이메일입니다.");
        }
    }

    @Override
    public LoginSession login(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        requireNotBlank(rawPassword, "비밀번호");

        TemporaryUser user = usersByEmail.get(normalizedEmail);

        if (user == null
                || user.status != UserStatus.ACTIVE
                || !PasswordUtil.matches(rawPassword, user.passwordHash)) {
            throw new BusinessException(LOGIN_FAILURE_MESSAGE);
        }

        return sessionManager.createSession(user.userId, user.role);
    }

    @Override
    public void logout(String sessionId) {
        sessionManager.removeSession(sessionId);
    }

    @Override
    public LoginSession requireLogin(String sessionId) {
        return sessionManager.requireSession(sessionId);
    }

    private void addDevelopmentUser(
            long userId,
            String email,
            String rawPassword,
            UserRole role,
            UserStatus status
    ) {
        String normalizedEmail = normalizeEmail(email);
        TemporaryUser user = new TemporaryUser(
                userId,
                normalizedEmail,
                PasswordUtil.hash(rawPassword),
                role,
                status
        );

        usersByEmail.put(normalizedEmail, user);
    }

    private String normalizeEmail(String email) {
        requireNotBlank(email, "이메일");
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private void requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(fieldName + "을(를) 입력해 주세요.");
        }
    }

    private static final class TemporaryUser {

        private final long userId;
        private final String email;
        private final String passwordHash;
        private final UserRole role;
        private final UserStatus status;

        private TemporaryUser(
                long userId,
                String email,
                String passwordHash,
                UserRole role,
                UserStatus status
        ) {
            this.userId = userId;
            this.email = email;
            this.passwordHash = passwordHash;
            this.role = role;
            this.status = status;
        }
    }
}
