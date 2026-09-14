package com.studyroom.reservation;

import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.handler.AuthHandler;
import com.studyroom.reservation.service.AuthService;
import com.studyroom.reservation.service.JdbcAuthService;
import com.studyroom.reservation.util.DatabaseUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * 스터디룸 예약 관리 시스템의 HTTP 서버를 실행합니다.
 * 각 기능의 Handler는 이 클래스에서 하나의 서버에 등록합니다.
 */
public final class Main {

    private static final int PORT = 8080;
    private static final String SESSION_COOKIE_NAME = "SESSION_ID";

    private Main() {
    }

    /**
     * 공통 인증 서비스를 생성하고 HTTP 서버를 시작합니다.
     *
     * @param args 실행 시 전달되는 명령행 인자
     * @throws IOException HTTP 서버를 생성하지 못한 경우
     */
    public static void main(String[] args) throws IOException {
        AuthService authService = new JdbcAuthService();
        AuthHandler authHandler = new AuthHandler(authService);

        HttpServer server = HttpServer.create(
                new InetSocketAddress(PORT),
                0
        );

        // 로그인
        server.createContext("/login", authHandler);

        // 회원가입
        server.createContext("/signup", authHandler);

        // 로그아웃
        server.createContext("/logout", authHandler);

        // 스터디룸 조회
        server.createContext(
                "/rooms",
                exchange -> handleRooms(exchange, authService)
        );

        server.createContext(
                "/css/common.css",
                Main::sendCommonCss
        );

        /*
         * "/" 컨텍스트는 등록되지 않은 모든 주소도 받을 수 있으므로
         * 구체적인 경로를 등록한 다음 마지막에 등록합니다.
         */
        server.createContext(
                "/",
                exchange -> handleRoot(exchange, authService)
        );

        server.setExecutor(Executors.newFixedThreadPool(4));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            DatabaseUtil.close();
        }));

        server.start();

        System.out.println(
                "서버가 시작되었습니다: http://localhost:" + PORT
        );
    }

    /**
     * 루트 URL에서 로그인 상태에 따라 기본 화면으로 이동합니다.
     */
    private static void handleRoot(
            HttpExchange exchange,
            AuthService authService
    ) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if (!"/".equals(path)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String sessionId = findCookie(
                exchange,
                SESSION_COOKIE_NAME
        );

        try {
            authService.requireLogin(sessionId);
            redirect(exchange, "/rooms");
        } catch (BusinessException e) {
            redirect(exchange, "/login");
        }
    }

    /**
     * 로그인한 사용자에게 스터디룸 목록 화면을 제공합니다.
     */
    private static void handleRooms(
            HttpExchange exchange,
            AuthService authService
    ) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if (!"/rooms".equals(path)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String sessionId = findCookie(
                exchange,
                SESSION_COOKIE_NAME
        );

        try {
            authService.requireLogin(sessionId);
            sendTemplate(exchange, "rooms.html");
        } catch (BusinessException e) {
            redirect(exchange, "/login");
        }
    }

    /**
     * 요청의 Cookie 헤더에서 지정한 쿠키를 찾습니다.
     */
    private static String findCookie(
            HttpExchange exchange,
            String cookieName
    ) {
        List<String> cookieHeaders =
                exchange.getRequestHeaders().get("Cookie");

        if (cookieHeaders == null) {
            return null;
        }

        for (String cookieHeader : cookieHeaders) {
            for (String cookie : cookieHeader.split(";")) {
                String[] pair = cookie.trim().split("=", 2);

                if (pair.length == 2
                        && cookieName.equals(pair[0])) {
                    return pair[1];
                }
            }
        }

        return null;
    }

    /**
     * 브라우저를 지정한 경로로 이동시킵니다.
     */
    private static void redirect(
            HttpExchange exchange,
            String location
    ) throws IOException {
        exchange.getResponseHeaders().set(
                "Location",
                location
        );
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    /**
     * templates 디렉터리의 HTML 파일을 반환합니다.
     */
    private static void sendTemplate(
            HttpExchange exchange,
            String fileName
    ) throws IOException {
        String resourcePath = "/templates/" + fileName;

        try (InputStream inputStream =
                     Main.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            byte[] responseBody = inputStream.readAllBytes();

            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "text/html; charset=UTF-8"
            );
            exchange.sendResponseHeaders(
                    200,
                    responseBody.length
            );
            exchange.getResponseBody().write(responseBody);
        } finally {
            exchange.close();
        }
    }

    /**
     * 공통 CSS 파일을 반환합니다.
     */
    private static void sendCommonCss(
            HttpExchange exchange
    ) throws IOException {
        String resourcePath = "/static/css/common.css";

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        try (InputStream inputStream =
                     Main.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            byte[] responseBody = inputStream.readAllBytes();

            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "text/css; charset=UTF-8"
            );
            exchange.sendResponseHeaders(
                    200,
                    responseBody.length
            );
            exchange.getResponseBody().write(responseBody);
        } finally {
            exchange.close();
        }
    }
}