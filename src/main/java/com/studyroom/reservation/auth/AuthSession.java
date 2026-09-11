package com.studyroom.reservation.auth;

/**
 * 로그인 성공 결과입니다.
 */
public final class AuthSession {

    private final String sessionId;
    private final SessionUser user;

    public AuthSession(String sessionId, SessionUser user) {
        this.sessionId = sessionId;
        this.user = user;
    }

    public String getSessionId() {
        return sessionId;
    }

    public SessionUser getUser() {
        return user;
    }
}

