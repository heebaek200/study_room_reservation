package com.studyroom.reservation;

import com.studyroom.reservation.dao.UserDAO;
import com.studyroom.reservation.dto.User;
import com.studyroom.reservation.enums.UserStatus;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.service.AuthService;
import com.studyroom.reservation.service.InMemoryAuthService;
import com.studyroom.reservation.service.UserService;
import com.studyroom.reservation.session.LoginSession;

import java.sql.SQLException;
import java.util.List;

/**
 * Issue #5(회원 정보 조회/수정/탈퇴) 직접 실행 테스트입니다.
 * 각 확인 항목은 [OK]/[FAIL]로 출력되어 PR 리뷰어가 콘솔 결과만으로 확인할 수 있습니다.
 * DB에는 CONFIRMED 예약이 없는 테스트 전용 회원(userId=121)이 미리 준비되어 있어야 합니다.
 */
public class UserTestMain {

    public static void main(String[] args) throws SQLException {
        AuthService authService = new InMemoryAuthService();
        UserDAO userDAO = new UserDAO();
        UserService userService = new UserService(authService, userDAO);

        section("1. 본인 정보 조회");
        LoginSession userSession = authService.login("user004@studyroom.test", "password");
        String sessionId = userSession.getSessionId();

        User me = userService.getMyInfo(sessionId);
        System.out.println("userId=" + me.getUserId() + ", email=" + me.getEmail()
                + ", name=" + me.getName() + ", role=" + me.getRole() + ", status=" + me.getStatus());
        check("userId = 4 회원이 조회됨", me.getUserId() == 4L);
        // User DTO에 비밀번호 해시 필드가 없어 위 출력에도 구조적으로 포함될 수 없음

        section("2. 이름 수정");
        userService.updateMyName(sessionId, "변경회원004");
        User afterNameUpdate = userService.getMyInfo(sessionId);
        check("이름이 변경됨", "변경회원004".equals(afterNameUpdate.getName()));
        check("이메일은 그대로임", "user004@studyroom.test".equals(afterNameUpdate.getEmail()));
        check("null 이름이 거절됨", rejects(() -> userService.updateMyName(sessionId, null)));
        check("공백 이름이 거절됨", rejects(() -> userService.updateMyName(sessionId, "   ")));

        section("3. 확정 예약이 있는 회원 탈퇴 (userId=4)");
        check("확정 예약이 있으면 탈퇴 시 예외 발생", rejects(() -> userService.withdraw(sessionId)));
        User stillActive = userService.getMyInfo(sessionId);
        check("회원 상태가 여전히 ACTIVE", stillActive.getStatus() == UserStatus.ACTIVE);

        section("4. 예약이 없는 회원 탈퇴 (userId=121, 테스트 전용 계정)");
        LoginSession noReservationSession = authService.login("user121@studyroom.test", "password");
        String noReservationSessionId = noReservationSession.getSessionId();
        // UPDATE users SET status='ACTIVE' WHERE user_id=121;

        userService.withdraw(noReservationSessionId);
        User afterWithdraw = userDAO.findById(121L); // 탈퇴 후 세션이 끊겨서 getMyInfo 대신 DAO로 직접 확인
        check("DB 상태가 WITHDRAWN으로 변경됨", afterWithdraw.getStatus() == UserStatus.WITHDRAWN);
        check("탈퇴 후 기존 세션 재사용 시 로그인 오류 발생",
                rejects(() -> userService.getMyInfo(noReservationSessionId)));

        section("5. 관리자 전체 회원 조회");
        LoginSession adminSession = authService.login("admin01@studyroom.test", "password");
        List<User> allUsers = userService.getAllUsers(adminSession.getSessionId());
        boolean hasActive = allUsers.stream().anyMatch(u -> u.getStatus() == UserStatus.ACTIVE);
        boolean hasWithdrawn = allUsers.stream().anyMatch(u -> u.getStatus() == UserStatus.WITHDRAWN);
        check("전체 회원 " + allUsers.size() + "명 중 ACTIVE 포함됨", hasActive);
        check("전체 회원 중 WITHDRAWN 포함됨", hasWithdrawn);
        check("일반 회원이 전체 조회 시 권한 오류 발생", rejects(() -> userService.getAllUsers(sessionId)));

        // UserService에는 getMyInfo/updateMyName/withdraw/getAllUsers 4개 메서드만 존재
        // -> 관리자 강제 탈퇴, 회원 복구용 메서드 자체가 없음 (코드 확인 완료)
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("===== " + title + " =====");
    }

    private static void check(String label, boolean passed) {
        System.out.println((passed ? "[OK] " : "[FAIL] ") + label);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws SQLException;
    }

    private static boolean rejects(ThrowingRunnable action) throws SQLException {
        try {
            action.run();
            return false;
        } catch (BusinessException e) {
            System.out.println("    -> BusinessException: " + e.getMessage());
            return true;
        }
    }
}
