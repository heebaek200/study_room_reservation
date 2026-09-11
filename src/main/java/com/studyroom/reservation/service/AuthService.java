package com.studyroom.reservation.service;

import com.studyroom.reservation.session.LoginSession;

/**
 * 임시 메모리 구현과 정식 JDBC 구현이 공통으로 지켜야 할 인증 계약입니다.
 */
public interface AuthService {

    void signUp(String email, String rawPassword, String name);

    LoginSession login(String email, String rawPassword);

    void logout(String sessionId);

    LoginSession requireLogin(String sessionId);
}
