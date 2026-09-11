package com.studyroom.reservation.auth;

/**
 * 로그인 이후 기능에서 사용하는 최소 회원 정보입니다.
 * 비밀번호 해시를 포함하지 않습니다.
 */
public final class SessionUser {

    private final long userId;
    private final String email;
    private final String name;
    private final UserRole role;

    public SessionUser(long userId, String email, String name, UserRole role) {
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.role = role;
    }

    public long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public UserRole getRole() {
        return role;
    }
}

