package com.studyroom.reservation.handler;

import com.studyroom.reservation.dto.StudyRoom;
import com.studyroom.reservation.dto.User;
import com.studyroom.reservation.enums.UserRole;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.service.AuthService;
import com.studyroom.reservation.service.ReservationCreateService;
import com.studyroom.reservation.service.StudyRoomService;
import com.studyroom.reservation.service.UserService;
import com.studyroom.reservation.session.LoginSession;
import com.studyroom.reservation.util.HttpRequestUtil;
import com.studyroom.reservation.util.HttpResponseUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * 일반 회원의 예약 신청 화면과 예약 생성 요청을 처리합니다.
 */
public final class ReservationCreateHandler
        implements HttpHandler {

    private static final String CREATE_PATH =
            "/reservations/create";

    private static final String LOGIN_PATH =
            "/login";

    private static final String SESSION_COOKIE_NAME =
            "SESSION_ID";

    private final AuthService authService;
    private final StudyRoomService studyRoomService;
    private final ReservationCreateService
            reservationCreateService;
    private final UserService userService;

    public ReservationCreateHandler(
            AuthService authService,
            StudyRoomService studyRoomService,
            ReservationCreateService
                    reservationCreateService,
            UserService userService
    ) {
        this.authService = authService;
        this.studyRoomService = studyRoomService;
        this.reservationCreateService =
                reservationCreateService;
        this.userService = userService;
    }

    @Override
    public void handle(HttpExchange exchange)
            throws IOException {
        String path =
                exchange.getRequestURI().getPath();

        if (!CREATE_PATH.equals(path)) {
            HttpResponseUtil.sendError(
                    exchange,
                    404,
                    "페이지를 찾을 수 없습니다.",
                    "요청한 페이지를 찾을 수 없습니다."
            );
            return;
        }

        LoginSession session;

        try {
            session = requireLogin(exchange);
        } catch (BusinessException e) {
            HttpResponseUtil.redirect(
                    exchange,
                    LOGIN_PATH
            );
            return;
        }

        if (session.getRole() != UserRole.USER) {
            HttpResponseUtil.sendError(
                    exchange,
                    403,
                    "요청을 처리할 수 없습니다.",
                    "일반 회원만 예약을 신청할 수 있습니다."
            );
            return;
        }

        String method = exchange.getRequestMethod();

        try {
            if ("GET".equalsIgnoreCase(method)) {
                handleForm(exchange);
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                handleCreate(exchange);
                return;
            }

            HttpResponseUtil.sendError(
                    exchange,
                    405,
                    "허용되지 않은 요청 방식입니다.",
                    "허용되지 않은 요청 방식입니다."
            );
        } catch (BusinessException e) {
            HttpResponseUtil.sendError(
                    exchange,
                    400,
                    "요청을 처리할 수 없습니다.",
                    e.getMessage()
            );
        } catch (SQLException e) {
            HttpResponseUtil.sendError(
                    exchange,
                    500,
                    "오류가 발생했습니다.",
                    "예약 처리 중 오류가 발생했습니다."
            );
        }
    }

    /**
     * 예약 신청 화면을 반환합니다.
     *
     * 예:
     * /reservations/create?roomId=1
     */
    private void handleForm(HttpExchange exchange)
            throws IOException, SQLException {
        String sessionId =
                HttpRequestUtil.findCookie(
                        exchange,
                        SESSION_COOKIE_NAME
                );

        Map<String, String> query =
                HttpRequestUtil.parseQuery(exchange);

        long roomId = parseRoomId(
                query.get("roomId")
        );

        StudyRoom room =
                studyRoomService.getRoom(roomId);

        String resultMessage = "";

        if ("success".equals(query.get("result"))) {
            resultMessage =
                    "예약이 완료되었습니다.";
        }

        sendReservationForm(
                exchange,
                sessionId,
                room,
                "",
                "",
                resultMessage
        );
    }

    /**
     * 예약 신청 Form을 읽고 예약 생성 Service를 호출합니다.
     */
    private void handleCreate(HttpExchange exchange)
            throws IOException, SQLException {

        String sessionId =
                HttpRequestUtil.findCookie(
                        exchange,
                        SESSION_COOKIE_NAME
                );

        Map<String, String> form =
                HttpRequestUtil.parseForm(exchange);

        long roomId = parseRoomId(
                form.get("roomId")
        );

        StudyRoom room =
                studyRoomService.getRoom(roomId);

        String startTimeValue =
                defaultString(form.get("startTime"));

        String endTimeValue =
                defaultString(form.get("endTime"));

        try {
            LocalDateTime startTime =
                    parseDateTime(
                            startTimeValue,
                            "예약 시작 일시"
                    );

            LocalDateTime endTime =
                    parseDateTime(
                            endTimeValue,
                            "예약 종료 일시"
                    );

            reservationCreateService.createReservation(
                    sessionId,
                    roomId,
                    startTime,
                    endTime
            );

            /*
             * 새로고침으로 예약이 중복 생성되는 것을 방지하기 위해
             * POST 처리 후 GET 화면으로 이동합니다.
             */
            HttpResponseUtil.redirect(
                    exchange,
                    CREATE_PATH
                            + "?roomId="
                            + roomId
                            + "&result=success"
            );
        } catch (BusinessException e) {
            sendReservationForm(
                    exchange,
                    sessionId,
                    room,
                    startTimeValue,
                    endTimeValue,
                    e.getMessage()
            );
        }
    }

    /**
     * 예약 신청 화면을 공통 레이아웃과 함께 반환합니다.
     * 현재 로그인한 일반 회원 정보를 헤더에 표시하고,
     * 예약 입력값과 처리 결과 메시지를 유지하여 다시 렌더링합니다.
     *
     * @param exchange 현재 HTTP 요청과 응답
     * @param sessionId 현재 로그인 세션 ID
     * @param room 예약 대상 스터디룸
     * @param startTime 예약 시작 일시 입력값
     * @param endTime 예약 종료 일시 입력값
     * @param resultMessage 처리 결과 메시지
     * @throws IOException 화면 응답 중 오류가 발생한 경우
     */
    private void sendReservationForm(
            HttpExchange exchange,
            String sessionId,
            StudyRoom room,
            String startTime,
            String endTime,
            String resultMessage
    ) throws IOException, SQLException {

        User currentUser;

        try {
            currentUser =
                    userService.getMyInfo(
                            sessionId
                    );
        } catch (BusinessException e) {
            HttpResponseUtil.redirect(
                    exchange,
                    LOGIN_PATH
            );
            return;
        }

        Map<String, String> values =
                new HashMap<>();

        // 공통 헤더 정보
        values.put(
                "userName",
                currentUser.getName()
        );
        values.put(
                "roleName",
                "일반 회원"
        );
        values.put(
                "roleClass",
                ""
        );

        // 현재 메뉴 표시
        values.put(
                "homeCurrent",
                ""
        );
        values.put(
                "roomsCurrent",
                "current"
        );
        values.put(
                "myInfoCurrent",
                ""
        );
        values.put(
                "myReservationsCurrent",
                ""
        );
        values.put(
                "myRefundsCurrent",
                ""
        );

        // 스터디룸 정보
        values.put(
                "roomId",
                String.valueOf(
                        room.getRoomId()
                )
        );
        values.put(
                "roomName",
                room.getName()
        );
        values.put(
                "capacity",
                String.valueOf(
                        room.getCapacity()
                )
        );
        values.put(
                "hourlyRate",
                room.getHourlyRate()
                        .toPlainString()
        );

        // 예약 입력값 및 결과 메시지
        values.put(
                "startTime",
                defaultString(startTime)
        );
        values.put(
                "endTime",
                defaultString(endTime)
        );
        values.put(
                "resultMessage",
                defaultString(resultMessage)
        );

        // ReservationCreateHandler는 USER 전용이므로 예약 영역을 항상 표시합니다.
        values.put(
                "reservationSectionDisplay",
                "block"
        );

        HttpResponseUtil.sendTemplateWithHtml(
                exchange,
                "room-detail.html",
                values,
                Map.of(
                        "header",
                        HttpResponseUtil.loadFragment(
                                "app-header.html"
                        ),
                        "navigation",
                        HttpResponseUtil.loadFragment(
                                "nav-user.html"
                        ),
                        "footer",
                        HttpResponseUtil.loadFragment(
                                "app-footer.html"
                        )
                )
        );
    }

    /**
     * 현재 요청의 로그인 세션을 확인합니다.
     */
    private LoginSession requireLogin(
            HttpExchange exchange
    ) {
        String sessionId =
                HttpRequestUtil.findCookie(
                        exchange,
                        SESSION_COOKIE_NAME
                );

        return authService.requireLogin(sessionId);
    }

    /**
     * 스터디룸 번호를 양의 정수로 변환합니다.
     */
    private long parseRoomId(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    "예약할 스터디룸을 선택해 주세요."
            );
        }

        try {
            long roomId = Long.parseLong(value);

            if (roomId <= 0) {
                throw new BusinessException(
                        "올바른 스터디룸을 선택해 주세요."
                );
            }

            return roomId;
        } catch (NumberFormatException e) {
            throw new BusinessException(
                    "올바른 스터디룸을 선택해 주세요."
            );
        }
    }

    /**
     * datetime-local 입력값을 LocalDateTime으로 변환합니다.
     */
    private LocalDateTime parseDateTime(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    fieldName + "를 입력해 주세요."
            );
        }

        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(
                    fieldName + " 형식이 올바르지 않습니다."
            );
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

}