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
            sendMessage(
                    exchange,
                    404,
                    "요청한 페이지를 찾을 수 없습니다."
            );
            return;
        }

        if (!"GET".equalsIgnoreCase(method)) {
            sendMessage(
                    exchange,
                    405,
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
            sendMessage(
                    exchange,
                    500,
                    "회원 정보를 불러오지 못했습니다."
            );
        }
    }

    /**
     * 권한에 맞는 공통 홈 템플릿을 반환합니다.
     */
    private void sendHomeTemplate(
            HttpExchange exchange,
            LoginSession session,
            User user
    ) throws IOException {
        String templateName;
        String roleName;

        if (session.getRole() == UserRole.ADMIN) {
            templateName = "admin-home.html";
            roleName = "관리자";
        } else {
            templateName = "user-home.html";
            roleName = "일반 회원";
        }

        HttpResponseUtil.sendTemplate(
                exchange,
                templateName,
                Map.of(
                        "userName", user.getName(),
                        "roleName", roleName
                )
        );
    }

    /**
     * 오류 상태와 메시지를 단순 텍스트로 반환합니다.
     */
    private void sendMessage(
            HttpExchange exchange,
            int statusCode,
            String message
    ) throws IOException {
        byte[] responseBody = message.getBytes(
                StandardCharsets.UTF_8
        );

        try {
            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "text/plain; charset=UTF-8"
            );
            exchange.sendResponseHeaders(
                    statusCode,
                    responseBody.length
            );
            exchange.getResponseBody().write(responseBody);
        } finally {
            exchange.close();
        }
    }
}
