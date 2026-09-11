package com.studyroom.reservation.session;

import com.studyroom.reservation.enums.UserRole;

/**
 * 로그인 후 다른 기능에서 사용할 최소 인증 정보입니다.
 * 비밀번호와 비밀번호 해시는 세션에 저장하지 않습니다.
 */
public final class LoginSession {

    private final String sessionId;
    private final long userId;
    private final UserRole role;

    public LoginSession(String sessionId, long userId, UserRole role) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.role = role;
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getUserId() {
        return userId;
    }

    public UserRole getRole() {
        return role;
    }
}
