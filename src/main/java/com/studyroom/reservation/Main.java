package com.studyroom.reservation;

import com.studyroom.reservation.dao.UserDAO;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.handler.AuthHandler;
import com.studyroom.reservation.handler.HomeHandler;
import com.studyroom.reservation.handler.UserHandler;
import com.studyroom.reservation.handler.StaticFileHandler;
import com.studyroom.reservation.service.AuthService;
import com.studyroom.reservation.service.JdbcAuthService;
import com.studyroom.reservation.service.ReservationCreateService;
import com.studyroom.reservation.service.StudyRoomService;
import com.studyroom.reservation.service.UserService;
import com.studyroom.reservation.util.DatabaseUtil;
import com.studyroom.reservation.util.HttpRequestUtil;
import com.studyroom.reservation.util.HttpResponseUtil;
import com.studyroom.reservation.dao.ReservationCreateDAO;
import com.studyroom.reservation.dao.StudyRoomDAO;
import com.studyroom.reservation.handler.ReservationCreateHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
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

    public static void main(String[] args) throws IOException {
        AuthService authService = new JdbcAuthService();
        UserDAO userDAO = new UserDAO();

        ReservationCreateDAO reservationCreateDAO =
                new ReservationCreateDAO();

        StudyRoomDAO studyRoomDAO =
                new StudyRoomDAO();

        StudyRoomService studyRoomService =
                new StudyRoomService(
                        studyRoomDAO,
                        authService
                );

        ReservationCreateService reservationCreateService =
                new ReservationCreateService(
                        authService,
                        userDAO,
                        reservationCreateDAO
                );

        AuthHandler authHandler =
                new AuthHandler(authService);

        HomeHandler homeHandler =
                new HomeHandler(
                        authService,
                        userDAO
                );

        UserService userService =
                new UserService(
                        authService,
                        userDAO
                );

        UserHandler userHandler =
                new UserHandler(userService);

        ReservationCreateHandler reservationCreateHandler =
                new ReservationCreateHandler(
                        authService,
                        studyRoomService,
                        reservationCreateService
                );

        StaticFileHandler staticFileHandler =
                new StaticFileHandler();

        HttpServer server = HttpServer.create(
                new InetSocketAddress(PORT),
                0
        );

        // 로그인
        server.createContext(
                "/login",
                authHandler
        );

        // 회원가입
        server.createContext(
                "/signup",
                authHandler
        );

        // 로그아웃
        server.createContext(
                "/logout",
                authHandler
        );

        // 로그인 이후 공통 홈
        server.createContext(
                "/home",
                homeHandler
        );

        // 회원
        server.createContext("/my-info", userHandler);
        server.createContext("/admin/users", userHandler);

        // 일반 회원 예약 신청
        server.createContext(
                "/reservations/create",
                reservationCreateHandler
        );

        /*
         * #24가 병합되기 전까지 사용하는 임시 스터디룸 화면 ~
         */
        server.createContext(
                "/rooms",
                exchange -> handleRooms(
                        exchange,
                        authService
                )
        );
        // ~ 이상 #24가 병합되면 삭제

        /*
         * 아래 라우터는 담당 GUI Issue가 main에 병합된 뒤 활성화합니다.
         *
         * // 스터디룸
         * server.createContext("/rooms", studyRoomHandler);
         * server.createContext("/admin/rooms", studyRoomHandler);
         *
         * // 예약 조회 및 취소
         * server.createContext("/my-reservations", reservationQueryHandler);
         * server.createContext("/reservations/cancel", reservationCancelHandler);
         *
         * // 환불 및 관리자 조회
         * server.createContext("/my-refunds", refundHandler);
         * server.createContext("/admin/reservations", refundHandler);
         * server.createContext("/admin/refunds", refundHandler);
         */

        // 정적 파일
        server.createContext(
                "/css/",
                staticFileHandler
        );

        /*
         * "/" 컨텍스트는 등록되지 않은 모든 주소도 받을 수 있으므로
         * 구체적인 경로를 먼저 등록하고 마지막에 등록합니다.
         */
        server.createContext(
                "/",
                exchange -> handleRoot(
                        exchange,
                        authService
                )
        );

        server.setExecutor(
                Executors.newFixedThreadPool(4)
        );

        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    server.stop(0);
                    DatabaseUtil.close();
                })
        );

        server.start();

        System.out.println(
                "서버가 시작되었습니다: http://localhost:"
                        + PORT
        );
    }

    /**
     * 로그인 상태에 따라 로그인 또는 공통 홈으로 이동합니다.
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

        if (!"GET".equalsIgnoreCase(
                exchange.getRequestMethod()
        )) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String sessionId = HttpRequestUtil.findCookie(
                exchange,
                SESSION_COOKIE_NAME
        );

        try {
            authService.requireLogin(sessionId);

            HttpResponseUtil.redirect(
                    exchange,
                    "/home"
            );
        } catch (BusinessException e) {
            HttpResponseUtil.redirect(
                    exchange,
                    "/login"
            );
        }
    }

    /**
     * 로그인한 사용자에게 임시 스터디룸 화면을 제공합니다.
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

        if (!"GET".equalsIgnoreCase(
                exchange.getRequestMethod()
        )) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String sessionId = HttpRequestUtil.findCookie(
                exchange,
                SESSION_COOKIE_NAME
        );

        try {
            authService.requireLogin(sessionId);

            HttpResponseUtil.sendTemplate(
                    exchange,
                    "rooms.html"
            );
        } catch (BusinessException e) {
            HttpResponseUtil.redirect(
                    exchange,
                    "/login"
            );
        }
    }
}
