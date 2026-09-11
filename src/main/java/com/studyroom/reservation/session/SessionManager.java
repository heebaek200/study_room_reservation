package com.studyroom.reservation.session;

import com.studyroom.reservation.enums.UserRole;
import com.studyroom.reservation.exception.BusinessException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로그인 세션을 메모리에서 관리합니다.
 */
public final class SessionManager {

    private final Map<String, LoginSession> sessions = new ConcurrentHashMap<>();

    public LoginSession createSession(long userId, UserRole role) {
        String sessionId = UUID.randomUUID().toString();
        LoginSession session = new LoginSession(sessionId, userId, role);

        sessions.put(sessionId, session);

        return session;
    }

    public LoginSession requireSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException("로그인이 필요합니다.");
        }

        LoginSession session = sessions.get(sessionId);

        if (session == null) {
            throw new BusinessException("로그인이 필요합니다.");
        }

        return session;
    }

    public void removeSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        sessions.remove(sessionId);
    }
}
