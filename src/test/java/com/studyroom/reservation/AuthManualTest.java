package com.studyroom.reservation;

import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.service.AuthService;
import com.studyroom.reservation.service.JdbcAuthService;
import com.studyroom.reservation.session.LoginSession;

/**
 * JDBC 회원가입과 로그인 기능을 순서대로 확인하는 임시 실행 클래스입니다.
 * 기존 Main.java를 수정하지 않아 다른 Issue의 작업과 충돌하지 않도록 합니다.
 * 테스트 완료 후 이 클래스는 삭제하거나 정식 JUnit 테스트로 교체합니다.
 */
public class AuthManualTest {

    /**
     * 회원가입 성공, 중복 가입 실패, 로그인 성공과 실패를 차례로 확인합니다.
     * 매번 다른 이메일을 사용하므로 이전 테스트 데이터와 충돌하지 않습니다.
     * 평문 비밀번호와 비밀번호 해시는 콘솔에 출력하지 않습니다.
     *
     * @param args 실행 시 전달되는 명령행 인자
     */
    public static void main(String[] args) {
        AuthService authService = new JdbcAuthService();

        // 실행 시각을 이용하여 중복되지 않는 테스트 이메일을 만듭니다.
        String email = "auth-test-" + System.currentTimeMillis()
                + "@studyroom.test";
        String password = "password123";
        String name = "인증 테스트";

        testSignUp(authService, email, password, name);
        testDuplicateEmail(authService, email, password, name);
//        testLoginSuccess(authService, email, password);
        testLoginAndLogout(authService, email, password);
        testWrongPassword(authService, email);
        testUnknownEmail(authService);
    }

    /**
     * 정상적인 회원 정보를 전달하여 회원가입 성공 여부를 확인합니다.
     * 예외가 발생하지 않으면 데이터베이스 INSERT가 완료된 것으로 판단합니다.
     * 결과 확인에 사용할 이메일만 콘솔에 출력합니다.
     */
    private static void testSignUp(
            AuthService authService,
            String email,
            String password,
            String name
    ) {
        try {
            authService.signUp(email, password, name);

            System.out.println("[성공] 회원가입");
            System.out.println("테스트 이메일: " + email);
        } catch (BusinessException e) {
            System.out.println("[실패] 회원가입: " + e.getMessage());
        }
    }

    /**
     * 앞서 가입한 이메일로 다시 회원가입을 시도합니다.
     * 동일한 이메일이 이미 존재하므로 BusinessException이 발생해야 합니다.
     * 예외 메시지로 이메일 중복 처리가 작동하는지 확인합니다.
     */
    private static void testDuplicateEmail(
            AuthService authService,
            String email,
            String password,
            String name
    ) {
        try {
            authService.signUp(email, password, name);

            System.out.println("[실패] 중복 이메일 가입이 허용되었습니다.");
        } catch (BusinessException e) {
            System.out.println("[성공] 중복 이메일 차단: " + e.getMessage());
        }
    }

    /**
     * 앞서 가입한 회원의 이메일과 올바른 비밀번호로 로그인합니다.
     * 로그인에 성공하면 LoginSession에 회원 ID와 권한이 저장되어야 합니다.
     * 세션에는 비밀번호나 비밀번호 해시가 포함되지 않습니다.
     */
    private static void testLoginSuccess(
            AuthService authService,
            String email,
            String password
    ) {
        try {
            LoginSession session = authService.login(email, password);

            System.out.println("[성공] 정상 로그인");
            System.out.println("회원 ID: " + session.getUserId());
            System.out.println("권한: " + session.getRole());
            System.out.println("세션 생성 여부: "
                    + !session.getSessionId().isBlank());
        } catch (BusinessException e) {
            System.out.println("[실패] 정상 로그인: " + e.getMessage());
        }
    }

    /**
     * 가입한 회원의 이메일과 잘못된 비밀번호로 로그인을 시도합니다.
     * 인증에 실패하여 BusinessException이 발생해야 합니다.
     * 실패 메시지가 통일된 로그인 메시지인지 함께 확인합니다.
     */
    private static void testWrongPassword(
            AuthService authService,
            String email
    ) {
        try {
            authService.login(email, "wrong-password");

            System.out.println("[실패] 잘못된 비밀번호 로그인이 허용되었습니다.");
        } catch (BusinessException e) {
            System.out.println("[성공] 잘못된 비밀번호 차단: "
                    + e.getMessage());
        }
    }

    /**
     * 데이터베이스에 존재하지 않는 이메일로 로그인을 시도합니다.
     * 잘못된 비밀번호와 동일한 실패 메시지가 반환되어야 합니다.
     * 가입 여부가 로그인 결과를 통해 노출되지 않는지 확인합니다.
     */
    private static void testUnknownEmail(AuthService authService) {
        try {
            authService.login(
                    "unknown-user@studyroom.test",
                    "password123"
            );

            System.out.println("[실패] 존재하지 않는 회원 로그인이 허용되었습니다.");
        } catch (BusinessException e) {
            System.out.println("[성공] 존재하지 않는 회원 차단: "
                    + e.getMessage());
        }
    }

    /**
     * 정상 로그인 후 세션 확인과 로그아웃을 차례로 실행합니다.
     * 로그아웃 전에는 세션을 조회할 수 있어야 합니다.
     * 로그아웃 후 같은 세션 ID를 사용하면 접근이 거부되어야 합니다.
     */
    private static void testLoginAndLogout(
            AuthService authService,
            String email,
            String password
    ) {
        try {
            // 정상 로그인으로 새로운 세션을 생성합니다.
            LoginSession loginSession = authService.login(email, password);
            String sessionId = loginSession.getSessionId();

            System.out.println("[성공] 정상 로그인");

            // 로그아웃 전에는 같은 세션 ID로 로그인 정보를 조회할 수 있습니다.
            LoginSession verifiedSession = authService.requireLogin(sessionId);

            System.out.println("[성공] 로그인 세션 확인");
            System.out.println("회원 ID: " + verifiedSession.getUserId());
            System.out.println("권한: " + verifiedSession.getRole());

            // 현재 로그인 세션을 제거합니다.
            authService.logout(sessionId);

            try {
                authService.requireLogin(sessionId);

                System.out.println("[실패] 로그아웃한 세션으로 접근했습니다.");
            } catch (BusinessException e) {
                System.out.println("[성공] 로그아웃 후 접근 차단: "
                        + e.getMessage());
            }
        } catch (BusinessException e) {
            System.out.println("[실패] 로그인·로그아웃 테스트: "
                    + e.getMessage());
        }
    }
}