package com.studyroom.reservation.handler;

import com.google.gson.Gson;
import com.studyroom.reservation.dto.StudyRoom;
import com.studyroom.reservation.dto.User;
import com.studyroom.reservation.service.UserService;
import com.studyroom.reservation.util.HttpRequestUtil;
import com.studyroom.reservation.util.HttpResponseUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.studyroom.reservation.service.AuthService;
import com.studyroom.reservation.service.StudyRoomService;
import com.studyroom.reservation.session.LoginSession;

import com.studyroom.reservation.enums.UserRole;
import com.studyroom.reservation.exception.BusinessException;

import java.util.HashMap;
import java.math.BigDecimal;

public class StudyRoomHandler implements HttpHandler {

    private static final String SESSION_COOKIE_NAME = "SESSION_ID";
    private static final String LOGIN_PATH = "/login";

    private final AuthService authService;
    private final StudyRoomService studyRoomService;
    private final UserService userService;

    // 생성자를 통해 의존성 주입
    public StudyRoomHandler(AuthService authService, StudyRoomService studyRoomService, UserService userService) {
        this.authService = authService;
        this.studyRoomService = studyRoomService;
        this.userService = userService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            // 1. 현재 접속자의 세션 확인 (비회원 접근도 허용해야 하므로 예외 처리)
            String sessionId = HttpRequestUtil.findCookie(exchange, SESSION_COOKIE_NAME);
            LoginSession session = null;

            try {
                // sessionId가 유효하면 세션 반환, 없으면 BusinessException 발생
                session = authService.requireLogin(sessionId);
            } catch (BusinessException e) {
                // 예외가 발생하면 비회원으로 간주 (session = null 유지)
            }

            // 2. 관리자 전용 경로(/admin/rooms) 접근 통제
            if (path.startsWith("/admin/rooms")) {
                // 로그인을 안 한 상태라면 로그인 페이지로 튕겨냄
                if (session == null) {
                    HttpResponseUtil.redirect(exchange, LOGIN_PATH);
                    return;
                }

                // 로그인은 했지만 관리자(ADMIN)가 아니라면 홈으로 튕겨냄
                if (session.getRole() != UserRole.ADMIN) {
                    HttpResponseUtil.redirect(exchange, "/home");
                    return;
                }
            }

            // 권한 검사를 무사히 통과한 후 실행되는 라우팅 분기 영역입니다.
            if ("GET".equalsIgnoreCase(method)) {

                if ("/rooms".equals(path)) {
                    List<StudyRoom> roomList =
                            studyRoomService.getRooms();

                    Gson gson = new Gson();

                    String roomsJsonData =
                            gson.toJson(
                                    roomList
                            );

                    Map<String, String> values =
                            new HashMap<>();

                    Map<String, String> fragments =
                            new HashMap<>();

                    values.put(
                            "roomsData",
                            roomsJsonData
                    );

                    applyCommonLayout(
                            session,
                            sessionId,
                            values,
                            fragments
                    );

                    HttpResponseUtil.sendTemplateWithHtml(
                            exchange,
                            "rooms.html",
                            values,
                            fragments
                    );
                } else if ("/rooms/detail".equals(path)) {
                    String query =
                            exchange.getRequestURI().getQuery();

                    long roomId =
                            parseRoomId(
                                    query
                            );

                    if (roomId <= 0) {
                        throw new BusinessException(
                                "올바르지 않은 스터디룸 번호입니다."
                        );
                    }

                    StudyRoom room =
                            studyRoomService.getRoom(
                                    roomId
                            );

                    Map<String, String> values =
                            new HashMap<>();

                    Map<String, String> fragments =
                            new HashMap<>();

                    values.put(
                            "roomId",
                            String.valueOf(
                                    room.getRoomId()
                            )
                    );

                    values.put(
                            "roomName",
                            room.getName() == null
                                    ? ""
                                    : room.getName()
                    );

                    values.put(
                            "capacity",
                            String.valueOf(
                                    room.getCapacity()
                            )
                    );

                    values.put(
                            "hourlyRate",
                            room.getHourlyRate() == null
                                    ? "0"
                                    : room.getHourlyRate().toString()
                    );

                    // 예약 신청 영역은 일반 회원에게만 표시합니다.
                    values.put(
                            "reservationSectionDisplay",
                            session != null
                                    && session.getRole() == UserRole.USER
                                    ? "block"
                                    : "none"
                    );

                    Gson gson = new Gson();

                    values.put(
                            "roomData",
                            gson.toJson(
                                    room
                            )
                    );

                    applyCommonLayout(
                            session,
                            sessionId,
                            values,
                            fragments
                    );

                    values.put("startTime", "");
                    values.put("endTime", "");
                    values.put("resultMessage", "");

                    HttpResponseUtil.sendTemplateWithHtml(
                            exchange,
                            "room-detail.html",
                            values,
                            fragments
                    );
                } else if ("/admin/rooms".equals(path)) {
                    List<StudyRoom> roomList =
                            studyRoomService.getRooms();

                    Gson gson = new Gson();

                    Map<String, String> values =
                            new HashMap<>();

                    Map<String, String> fragments =
                            new HashMap<>();

                    values.put(
                            "roomsData",
                            gson.toJson(
                                    roomList
                            )
                    );

                    values.put(
                            "errorMessage",
                            ""
                    );

                    applyCommonLayout(
                            session,
                            sessionId,
                            values,
                            fragments
                    );

                    // 관리자 스터디룸 관리 화면에서는 관리 메뉴를 현재 위치로 표시합니다.
                    values.put(
                            "roomsCurrent",
                            ""
                    );

                    values.put(
                            "adminRoomsCurrent",
                            "current"
                    );

                    HttpResponseUtil.sendTemplateWithHtml(
                            exchange,
                            "admin-rooms.html",
                            values,
                            fragments
                    );
                }

            } else if ("POST".equalsIgnoreCase(method)) {
                // [4단계] 관리자 스터디룸 등록/수정 POST 처리 로직
                if ("/admin/rooms/create".equals(path) || "/admin/rooms/update".equals(path)) {
                    try {
                        // 1. 폼 데이터 직접 읽기 및 파싱 (유틸리티 의존성 제거)
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        Map<String, String> formData = new java.util.HashMap<>();
                        if (!body.isBlank()) {
                            for (String param : body.split("&")) {
                                String[] keyValue = param.split("=");
                                if (keyValue.length == 2) {
                                    formData.put(
                                            java.net.URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8),
                                            java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8)
                                    );
                                }
                            }
                        }

                        // 2. 현재 접속자의 세션 ID 가져오기
                        sessionId = HttpRequestUtil.findCookie(exchange, SESSION_COOKIE_NAME);

                        String roomIdStr = formData.get("roomId");
                        String name = formData.get("name");
                        int capacity = Integer.parseInt(formData.get("capacity"));
                        BigDecimal hourlyRate = new BigDecimal(formData.get("hourlyRate"));

                        // 3. 서비스 호출
                        if (roomIdStr == null || roomIdStr.isBlank()) {
                            studyRoomService.createRoom(sessionId, name, capacity, hourlyRate);
                        } else {
                            long roomId = Long.parseLong(roomIdStr);
                            studyRoomService.updateRoom(sessionId, roomId, name, capacity, hourlyRate);
                        }

                        // 4. 성공 시 리다이렉트
                        HttpResponseUtil.redirect(exchange, "/admin/rooms");

                    } catch (BusinessException e) {
                        renderAdminRoomsWithError(exchange, e.getMessage());
                    } catch (NumberFormatException e) {
                        renderAdminRoomsWithError(exchange, "수용 인원과 이용료는 올바른 숫자로 입력해주세요.");
                    }
                }
            }

        } catch (Exception e) {
            // 1. 관리자 스터디룸 등록/수정 중 발생한 예외인 경우 에러 메시지와 함께 관리자 페이지 재렌더링
            if ("POST".equalsIgnoreCase(method) &&
                    ("/admin/rooms/create".equals(path) || "/admin/rooms/update".equals(path))) {

                String errorMessage = e.getMessage() != null && !e.getMessage().isBlank()
                        ? e.getMessage()
                        : "요청을 처리하는 중 오류가 발생했습니다.";

                renderAdminRoomsWithError(exchange, errorMessage);
                return;
            }

            // 2. 그 외 일반적인 예외인 경우 기존 방식대로 처리
            exchange.close();
            HttpResponseUtil.sendError(
                    exchange,
                    500,
                    "오류가 발생했습니다.",
                    "요청을 처리하는 중 오류가 발생했습니다."
            );
        }
    }

    private long parseRoomId(String query) {
        if (query == null || query.isBlank()) {
            return -1;
        }
        for (String param : query.split("&")) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2 && "roomId".equals(keyValue[0])) {
                try {
                    return Long.parseLong(keyValue[1]);
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private void renderAdminRoomsWithError(HttpExchange exchange, String errorMessage) throws IOException {
        List<StudyRoom> roomList = studyRoomService.getRooms();
        Gson gson = new Gson();
        String roomsJsonData = gson.toJson(roomList);

        Map<String, String> variables = new HashMap<>();
        variables.put("roomsData", roomsJsonData);
        variables.put("roleName", "관리자");
        variables.put("errorMessage", errorMessage);

        HttpResponseUtil.sendTemplate(exchange, "admin-rooms.html", variables);
    }


    /**
     * 현재 로그인 상태와 권한에 맞는 공통 레이아웃 정보를 설정합니다.
     * 비회원은 게스트 전용 헤더와 내비게이션을 사용합니다.
     * 로그인 사용자는 일반 회원 또는 관리자용 내비게이션을 사용합니다.
     *
     * @param session 현재 로그인 세션, 비회원이면 null
     * @param sessionId 현재 세션 ID
     * @param values 템플릿 일반 문자열 값
     * @param fragments 공통 HTML fragment 값
     * @throws IOException fragment 파일을 읽지 못한 경우
     */
    private void applyCommonLayout(
            LoginSession session,
            String sessionId,
            Map<String, String> values,
            Map<String, String> fragments
    ) throws IOException, SQLException {

        // 비회원은 사용자 정보 조회 없이 게스트 전용 레이아웃을 사용합니다.
        if (session == null) {
            fragments.put(
                    "header",
                    HttpResponseUtil.loadFragment(
                            "app-header-guest.html"
                    )
            );

            fragments.put(
                    "navigation",
                    HttpResponseUtil.loadFragment(
                            "nav-guest.html"
                    )
            );

            fragments.put(
                    "footer",
                    HttpResponseUtil.loadFragment(
                            "app-footer.html"
                    )
            );

            values.put(
                    "roomsCurrent",
                    "current"
            );

            return;
        }

        User currentUser =
                userService.getMyInfo(
                        sessionId
                );

        // 로그인 사용자 공통 헤더 정보
        values.put(
                "userName",
                currentUser.getName()
        );

        values.put(
                "roomsCurrent",
                "current"
        );

        values.put(
                "homeCurrent",
                ""
        );

        fragments.put(
                "header",
                HttpResponseUtil.loadFragment(
                        "app-header.html"
                )
        );

        fragments.put(
                "footer",
                HttpResponseUtil.loadFragment(
                        "app-footer.html"
                )
        );

        if (session.getRole() == UserRole.ADMIN) {
            values.put(
                    "roleName",
                    "관리자"
            );

            values.put(
                    "roleClass",
                    "admin"
            );

            values.put(
                    "adminRoomsCurrent",
                    ""
            );

            values.put(
                    "adminUsersCurrent",
                    ""
            );

            values.put(
                    "adminReservationsCurrent",
                    ""
            );

            values.put(
                    "adminRefundsCurrent",
                    ""
            );

            fragments.put(
                    "navigation",
                    HttpResponseUtil.loadFragment(
                            "nav-admin.html"
                    )
            );

            return;
        }

        values.put(
                "roleName",
                "일반 회원"
        );

        values.put(
                "roleClass",
                ""
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

        fragments.put(
                "navigation",
                HttpResponseUtil.loadFragment(
                        "nav-user.html"
                )
        );
    }
}