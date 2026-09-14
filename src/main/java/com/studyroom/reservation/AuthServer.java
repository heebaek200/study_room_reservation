package com.studyroom.reservation;

import com.studyroom.reservation.handler.AuthHandler;
import com.studyroom.reservation.service.AuthService;
import com.studyroom.reservation.service.JdbcAuthService;
import com.studyroom.reservation.util.DatabaseUtil;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.InputStream;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * 회원가입과 로그인 화면을 확인하기 위한 인증 전용 HTTP 서버입니다.
 * 공통 Main.java를 수정하지 않아 다른 Issue의 개발 작업과 충돌하지 않습니다.
 * 최종 통합 단계에서는 AuthHandler 등록 부분만 공통 서버로 옮길 수 있습니다.
 */
public final class AuthServer {

    private static final int PORT = 8080;

    // 실행 클래스가 객체로 생성되는 것을 방지합니다.
    private AuthServer() {
    }

    /**
     * JdbcAuthService와 AuthHandler를 생성하고 HTTP 서버를 시작합니다.
     * 로그인과 회원가입 주소에 동일한 AuthHandler를 등록합니다.
     * 애플리케이션 종료 시 서버와 데이터베이스 커넥션 풀을 정리합니다.
     *
     * @param args 실행 시 전달되는 명령행 인자
     * @throws IOException HTTP 서버를 생성하지 못한 경우
     */
    public static void main(String[] args) throws IOException {
        AuthService authService = new JdbcAuthService();
        AuthHandler authHandler = new AuthHandler(authService);

        // 인증 기능을 확인할 로컬 HTTP 서버를 생성합니다.
        HttpServer server = HttpServer.create(
                new InetSocketAddress(PORT),
                0
        );

        server.createContext("/login", authHandler);
        server.createContext("/signup", authHandler);
        server.createContext("/logout", authHandler);
        server.setExecutor(Executors.newFixedThreadPool(4));

        // 프로그램 종료 시 HTTP 서버와 커넥션 풀을 함께 정리합니다.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            DatabaseUtil.close();
        }));

        // 로그인과 회원가입 화면에서 공통으로 사용할 CSS를 제공합니다.
        server.createContext("/css/common.css", AuthServer::sendCommonCss);

        server.start();

        System.out.println(
                "인증 서버가 시작되었습니다: http://localhost:" + PORT
        );
    }

    /**
     * 클래스패스에 저장된 공통 CSS 파일을 브라우저에 전달합니다.
     * 인증 전용 서버에서 정확히 common.css 한 파일만 제공하도록 제한합니다.
     * 최종 서버 통합 시에는 공통 StaticFileHandler로 이전할 수 있습니다.
     *
     * @param exchange 현재 HTTP 요청과 응답
     * @throws IOException CSS 파일 또는 응답을 처리하지 못한 경우
     */
    private static void sendCommonCss(HttpExchange exchange)
            throws IOException {
        String resourcePath = "/static/css/common.css";

        // GET 이외의 요청은 허용하지 않습니다.
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        try (InputStream inputStream =
                     AuthServer.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            byte[] responseBody = inputStream.readAllBytes();

            // 브라우저가 응답을 CSS로 해석하도록 Content-Type을 지정합니다.
            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "text/css; charset=UTF-8"
            );
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
        } finally {
            exchange.close();
        }
    }
}