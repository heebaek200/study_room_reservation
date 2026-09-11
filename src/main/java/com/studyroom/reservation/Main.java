package com.studyroom.reservation;

import com.studyroom.reservation.auth.AuthService;
import com.studyroom.reservation.auth.AuthSession;
import com.studyroom.reservation.auth.InMemoryAuthService;
import com.studyroom.reservation.auth.SessionUser;
import com.studyroom.reservation.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

//TIP 코드를 <b>실행</b>하려면 <shortcut actionId="Run"/>을(를) 누르거나
// 에디터 여백에 있는 <icon src="AllIcons.Actions.Execute"/> 아이콘을 클릭하세요.
public class Main {


    private static void printUser(SessionUser user) {
        System.out.println("회원 번호: " + user.getUserId());
        System.out.println("이메일: " + user.getEmail());
        System.out.println("이름: " + user.getName());
        System.out.println("권한: " + user.getRole());
    }


    public static void main(String[] args) {

        // 임시 로그인 테스트 -----------------------------------
        InMemoryAuthService temporaryAuthService = new InMemoryAuthService();

        // 실제 기능에서는 AuthService 인터페이스만 사용합니다.
        AuthService authService = temporaryAuthService;

        try {
            // 1. 회원가입
            SessionUser registeredUser = authService.signUp(
                    "user1@example.com",
                    "password123!",
                    "사용자1"
            );

            System.out.println("===== 회원가입 성공 =====");
            printUser(registeredUser);

            // 2. 로그인
            AuthSession session = authService.login(
                    "user1@example.com",
                    "password123!"
            );

            System.out.println("\n===== 로그인 성공 =====");
            System.out.println("세션 ID: " + session.getSessionId());
            printUser(session.getUser());

            // 3. 로그인 인증 확인
            SessionUser loginUser =
                    authService.requireAuthenticatedUser(
                            session.getSessionId()
                    );

            System.out.println("\n===== 인증 확인 성공 =====");
            printUser(loginUser);


            //



            // 4. 로그아웃
            authService.logout(session.getSessionId());

            System.out.println("\n===== 로그아웃 성공 =====");

            // 5. 로그아웃한 세션으로 다시 접근
            authService.requireAuthenticatedUser(
                    session.getSessionId()
            );
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("\n처리 결과: " + e.getMessage());
        }


        // DB 접속 테스트 ----------------------------
//        try (Connection connection = DatabaseUtil.getConnection()) {
//
//            PreparedStatement statement = connection.prepareStatement("""
//SELECT * FROM users LIMIT 30
//""");
//
//            ResultSet rs = statement.executeQuery();
//            while (rs.next()) {
//                System.out.println(rs.getString("name"));
//            }
//
//        } catch (SQLException e) {
//            throw new RuntimeException(e);
//        }

    }
}