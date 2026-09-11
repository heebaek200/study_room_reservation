package com.studyroom.reservation;

import com.studyroom.reservation.service.AuthService;
import com.studyroom.reservation.service.InMemoryAuthService;
import com.studyroom.reservation.session.LoginSession;

/**
 * Issue #5와 #6 개발자가 임시 로그인 정보를 확인할 때 실행합니다.
 */
public class AuthTestMain {

    public static void main(String[] args) {
        AuthService authService = new InMemoryAuthService();

        LoginSession userSession = authService.login(
                "user004@studyroom.test",
                "password"
        );
        printSession("회원", userSession);

        LoginSession adminSession = authService.login(
                "admin01@studyroom.test",
                "password"
        );
        printSession("관리자", adminSession);

        authService.logout(userSession.getSessionId());

        System.out.println("회원 로그아웃 완료");
    }

    private static void printSession(String label, LoginSession session) {
        System.out.println("===== " + label + " 로그인 =====");
        System.out.println("sessionId: " + session.getSessionId());
        System.out.println("userId: " + session.getUserId());
        System.out.println("role: " + session.getRole());
    }
}
