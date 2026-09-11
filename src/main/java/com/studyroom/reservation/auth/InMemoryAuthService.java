package com.studyroom.reservation.auth;

import org.mindrot.jbcrypt.BCrypt;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 후속 기능 개발을 위한 임시 인증 구현체입니다.
 * 회원과 세션을 메모리에 저장하므로 애플리케이션 종료 시 모두 사라집니다.
 */
public final class InMemoryAuthService implements AuthService {

    private final AtomicLong userIdSequence = new AtomicLong(1L);
    private final Map<String, StoredUser> usersByEmail = new ConcurrentHashMap<>();
    private final Map<String, SessionUser> sessionsById = new ConcurrentHashMap<>();

    @Override
    public SessionUser signUp(String email, String rawPassword, String name) {
        return addUser(email, rawPassword, name, UserRole.USER);
    }

    /**
     * 관리자 화면 테스트 전용 메서드입니다.
     * 정식 구현에서는 일반 회원가입으로 관리자 계정을 만들 수 없게 유지합니다.
     */
    public SessionUser addAdminForDevelopment(
            String email,
            String rawPassword,
            String name
    ) {
        return addUser(email, rawPassword, name, UserRole.ADMIN);
    }

    @Override
    public AuthSession login(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        requireNotBlank(rawPassword, "비밀번호");

        StoredUser storedUser = usersByEmail.get(normalizedEmail);

        if (storedUser == null
                || !BCrypt.checkpw(rawPassword, storedUser.passwordHash)) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        SessionUser sessionUser = storedUser.toSessionUser();
        String sessionId = UUID.randomUUID().toString();

        sessionsById.put(sessionId, sessionUser);

        return new AuthSession(sessionId, sessionUser);
    }

    @Override
    public void logout(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        sessionsById.remove(sessionId);
    }

    @Override
    public SessionUser requireAuthenticatedUser(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        SessionUser sessionUser = sessionsById.get(sessionId);

        if (sessionUser == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        return sessionUser;
    }

    private SessionUser addUser(
            String email,
            String rawPassword,
            String name,
            UserRole role
    ) {
        String normalizedEmail = normalizeEmail(email);
        requireNotBlank(rawPassword, "비밀번호");
        requireNotBlank(name, "이름");

        long userId = userIdSequence.getAndIncrement();
        String passwordHash = BCrypt.hashpw(rawPassword, BCrypt.gensalt());
        StoredUser newUser = new StoredUser(
                userId,
                normalizedEmail,
                passwordHash,
                name.strip(),
                role
        );

        StoredUser existingUser = usersByEmail.putIfAbsent(
                normalizedEmail,
                newUser
        );

        if (existingUser != null) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        return newUser.toSessionUser();
    }

    private String normalizeEmail(String email) {
        requireNotBlank(email, "이메일");
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private void requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "을(를) 입력해 주세요.");
        }
    }

    private static final class StoredUser {

        private final long userId;
        private final String email;
        private final String passwordHash;
        private final String name;
        private final UserRole role;

        private StoredUser(
                long userId,
                String email,
                String passwordHash,
                String name,
                UserRole role
        ) {
            this.userId = userId;
            this.email = email;
            this.passwordHash = passwordHash;
            this.name = name;
            this.role = role;
        }

        private SessionUser toSessionUser() {
            return new SessionUser(userId, email, name, role);
        }
    }
}
