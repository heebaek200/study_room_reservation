package com.studyroom.reservation.auth;

/**
 * 회원가입과 로그인 세션 처리를 정의합니다.
 * 정식 JDBC 구현과 임시 메모리 구현이 동일한 계약을 사용합니다.
 */
public interface AuthService {

    SessionUser signUp(String email, String rawPassword, String name);

    AuthSession login(String email, String rawPassword);

    void logout(String sessionId);

    SessionUser requireAuthenticatedUser(String sessionId);
}

