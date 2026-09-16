package com.studyroom.reservation.handler;

import com.studyroom.reservation.dao.UserDAO;
import com.studyroom.reservation.dto.User;
import com.studyroom.reservation.enums.UserRole;
import com.studyroom.reservation.enums.UserStatus;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.service.AuthService;
import com.studyroom.reservation.session.LoginSession;
import com.studyroom.reservation.util.HttpRequestUtil;
import com.studyroom.reservation.util.HttpResponseUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 로그인 이후 공통 홈 화면을 제공합니다.
 * 사용자 권한에 따라 일반 회원 또는 관리자 화면을 반환합니다.
 */
public final class HomeHandler implements HttpHandler {

    private static final String HOME_PATH = "/home";
    private static final String LOGIN_PATH = "/login";
    private static final String SESSION_COOKIE_NAME = "SESSION_ID";

    private final AuthService authService;
    private final UserDAO userDAO;

    public HomeHandler(
            AuthService authService,
            UserDAO userDAO
    ) {
        this.authService = authService;
        this.userDAO = userDAO;
    }

    @Override
    public void handle(HttpExchange exchange)
            throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (!HOME_PATH.equals(path)) {
            HttpResponseUtil.sendError(
                    exchange,
                    404,
                    "페이지를 찾을 수 없습니다.",
                    "요청한 페이지를 찾을 수 없습니다."
            );
            return;
        }

        if (!"GET".equalsIgnoreCase(method)) {
            HttpResponseUtil.sendError(
                    exchange,
                    405,
                    "요청 방식을 찾을 수 없습니다.",
                    "허용되지 않은 요청 방식입니다."
            );
            return;
        }

        String sessionId = HttpRequestUtil.findCookie(
                exchange,
                SESSION_COOKIE_NAME
        );

        try {
            LoginSession session =
                    authService.requireLogin(sessionId);

            User user = userDAO.findById(
                    session.getUserId()
            );

            if (user == null
                    || user.getStatus()
                    != UserStatus.ACTIVE) {
                authService.logout(sessionId);
                HttpResponseUtil.redirect(
                        exchange,
                        LOGIN_PATH
                );
                return;
            }

            sendHomeTemplate(
                    exchange,
                    session,
                    user
            );
        } catch (BusinessException e) {
            HttpResponseUtil.redirect(
                    exchange,
                    LOGIN_PATH
            );
        } catch (SQLException e) {
            HttpResponseUtil.sendError(
                    exchange,
                    500,
                    "오류가 발생했습니다.",
                    "회원 정보를 불러오지 못했습니다."
            );
        }
    }

    /**
     * 로그인 사용자의 권한에 맞는 홈 화면을 반환합니다.
     * 공통 헤더와 푸터를 불러오고 권한별 내비게이션을 선택하여
     * 홈 템플릿의 공통 레이아웃 영역에 삽입합니다.
     */
    private void sendHomeTemplate(
            HttpExchange exchange,
            LoginSession session,
            User user
    ) throws IOException {

        String templateName;
        String navigationName;
        String roleName;
        String roleClass;

        if (session.getRole() == UserRole.ADMIN) {
            templateName = "admin-home.html";
            navigationName = "nav-admin.html";
            roleName = "관리자";
            roleClass = "admin";
        } else {
            templateName = "user-home.html";
            navigationName = "nav-user.html";
            roleName = "일반 회원";
            roleClass = "";
        }

        // 모든 로그인 화면에서 공통으로 사용하는 HTML 조각을 읽습니다.
        String header =
                HttpResponseUtil.loadFragment(
                        "app-header.html"
                );

        String navigation =
                HttpResponseUtil.loadFragment(
                        navigationName
                );

        String footer =
                HttpResponseUtil.loadFragment(
                        "app-footer.html"
                );

        // 공통 레이아웃에서 사용하는 템플릿 값을 설정합니다.
        Map<String, String> values = new HashMap<>();

        values.put("userName", user.getName());
        values.put("roleName", roleName);
        values.put("roleClass", roleClass);

        // 홈 화면이므로 홈 메뉴만 현재 위치로 표시합니다.
        values.put("homeCurrent", "current");

        // 일반 회원 메뉴의 비활성 항목
        values.put("roomsCurrent", "");
        values.put("myInfoCurrent", "");
        values.put("myReservationsCurrent", "");
        values.put("myRefundsCurrent", "");

        // 관리자 메뉴의 비활성 항목
        values.put("adminRoomsCurrent", "");
        values.put("adminUsersCurrent", "");
        values.put("adminReservationsCurrent", "");
        values.put("adminRefundsCurrent", "");

        HttpResponseUtil.sendTemplateWithHtml(
                exchange,
                templateName,
                values,
                Map.of(
                        "header", header,
                        "navigation", navigation,
                        "footer", footer
                )
        );
    }

}
