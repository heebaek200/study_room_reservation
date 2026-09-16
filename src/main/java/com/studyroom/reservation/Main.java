package com.studyroom.reservation;

import com.studyroom.reservation.dao.*;
import com.studyroom.reservation.dto.Refund;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.handler.*;
import com.studyroom.reservation.service.*;
import com.studyroom.reservation.util.DatabaseUtil;
import com.studyroom.reservation.util.HttpRequestUtil;
import com.studyroom.reservation.util.HttpResponseUtil;
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

        AdminReservationQueryDAO adminReservationQueryDAO =
                new AdminReservationQueryDAO();

        MemberRefundQueryDAO memberRefundQueryDAO =
                new MemberRefundQueryDAO();

        AdminRefundDAO adminRefundDAO =
                new AdminRefundDAO();

        MemberReservationQueryDAO memberReservationQueryDAO =
                new MemberReservationQueryDAO();

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

        AdminReservationQueryService adminReservationQueryService =
                new AdminReservationQueryService(
                        authService,
                        adminReservationQueryDAO
                );

        MemberRefundQueryService memberRefundQueryService =
                new MemberRefundQueryService(
                        authService,
                        memberRefundQueryDAO
                );

        ReservationCancelDAO reservationCancelDAO =
                new ReservationCancelDAO();

        MemberReservationQueryService memberReservationQueryService =
                new MemberReservationQueryService(
                        memberReservationQueryDAO
                );

        ReservationCancelService reservationCancelService =
                new ReservationCancelService(
                        authService,
                        reservationCancelDAO
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
                        reservationCreateService,
                        userService
                );

        StudyRoomHandler studyRoomHandler =
                new StudyRoomHandler(
                        authService,
                        studyRoomService,
                        userService
                );

        ReservationQueryHandler reservationQueryHandler =
                new ReservationQueryHandler(
                        authService,
                        studyRoomService,
                        memberReservationQueryService,
                        userService
                );

        ReservationCancelHandler reservationCancelHandler =
                new ReservationCancelHandler(
                        reservationCancelService,
                        memberRefundQueryService,
                        authService,
                        userService
                );

        AdminRefundService adminRefundService =
                new AdminRefundService(
                        authService,
                        adminRefundDAO
                );

        RefundHandler refundHandler =
                new RefundHandler(
                        memberRefundQueryService,
                        adminReservationQueryService,
                        adminRefundService,
                        userService
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

         // 스터디룸
        server.createContext("/rooms", studyRoomHandler);
        server.createContext("/rooms/detail", studyRoomHandler);
        server.createContext("/admin/rooms", studyRoomHandler);

        // 예약 조회 및 취소
        server.createContext("/my-reservations", reservationQueryHandler);
        server.createContext("/reservations/cancel", reservationCancelHandler);

        // 환불 및 관리자 조회
        server.createContext("/my-refunds", refundHandler);
        server.createContext("/admin/reservations", refundHandler);
        server.createContext("/admin/refunds", refundHandler);

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
     * 루트 경로 요청을 처리합니다.
     * 정확한 "/" 경로만 허용하며, 그 외 경로는 공통 404 화면으로 응답합니다.
     * 정상적인 GET 요청은 로그인 상태에 따라 홈 또는 로그인 화면으로 이동시킵니다.
     */
    private static void handleRoot(
            HttpExchange exchange,
            AuthService authService
    ) throws IOException {
        String path = exchange.getRequestURI().getPath();

        // 등록되지 않은 주소는 공통 404 오류 화면으로 처리합니다.
        if (!"/".equals(path)) {
            HttpResponseUtil.sendError(
                    exchange,
                    404,
                    "페이지를 찾을 수 없습니다.",
                    "요청한 주소가 존재하지 않습니다."
            );
            return;
        }

        // 루트 경로에서는 GET 요청만 허용합니다.
        if (!"GET".equalsIgnoreCase(
                exchange.getRequestMethod()
        )) {
            HttpResponseUtil.sendError(
                    exchange,
                    405,
                    "지원하지 않는 요청입니다.",
                    "현재 주소에서는 사용할 수 없는 요청 방식입니다."
            );
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
