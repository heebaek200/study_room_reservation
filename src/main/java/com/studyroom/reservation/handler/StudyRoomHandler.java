package com.studyroom.reservation.handler;

import com.google.gson.Gson;
import com.studyroom.reservation.dto.StudyRoom;
import com.studyroom.reservation.dto.User;
import com.studyroom.reservation.util.HttpRequestUtil;
import com.studyroom.reservation.util.HttpResponseUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    // 생성자를 통해 의존성 주입
    public StudyRoomHandler(AuthService authService, StudyRoomService studyRoomService) {
        this.authService = authService;
        this.studyRoomService = studyRoomService;
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
                    // 1. Service를 통해 데이터베이스에서 스터디룸 목록 전체 조회
                    List<StudyRoom> roomList = studyRoomService.getRooms();

                    // 2. 리스트 객체를 자바스크립트가 읽을 수 있는 JSON 문자열로 변환
                    Gson gson = new Gson();
                    String roomsJsonData = gson.toJson(roomList);

                    // 3. 세션에서 사용자 이메일과 권한 정보를 안전하게 가져옴
                    String userName = (session != null)
                            ? (session.getRole() == UserRole.ADMIN ? "관리자" : "회원님") : "게스트";
                    // 뱃지에"비회원"으로 나옴
                    String roleName = (session != null && session.getRole() != null) ? session.getRole().name() : "비회원";

                    // 4. rooms.html 템플릿에 DB 데이터와 사용자 정보를 함께 전달
                    HttpResponseUtil.sendTemplate(exchange, "rooms.html", Map.of(
                            "roomsData", roomsJsonData,
                            "userName", userName,
                            "roleName", roleName
                    ));

                    // 응답을 보냈으니 핸들러를 종료합니다.
                    return;

                } else if ("/rooms/detail".equals(path)) {
                    // 스터디룸 상세 조회 로직 (Issue #24)
                    String query = exchange.getRequestURI().getQuery();
                    long roomId = parseRoomId(query);

                    if (roomId <= 0) {
                        throw new BusinessException("올바르지 않은 스터디룸 번호입니다.");
                    }

                    StudyRoom room = studyRoomService.getRoom(roomId);

                    // 1. 권한 판별 (로그인 여부 및 관리자 여부 체크)
                    boolean isLogin = (session != null);
                    boolean isAdmin = isLogin && (session.getRole() == UserRole.ADMIN);

                    // 2. 상단 뱃지에 표시할 사용자 권한 이름 설정
                    String roleName = "비회원";
                    if (isLogin) {
                        roleName = isAdmin ? "관리자" : "일반 회원";
                    }

                    // 3. 권한별 CSS display 스타일 결정
                    // 💡 [추가됨] 일반 회원이 아닐 때(비회원/관리자)만 상단 공통 메뉴 표시
                    String nonUserMenuDisplay = (isLogin && !isAdmin) ? "none" : "block";
                    String guestLinkDisplay = !isLogin ? "block" : "none";
                    String adminLinkDisplay = isAdmin ? "block" : "none";
                    // 💡 일반 회원에게만 하단 폼 전체 노출
                    String reservationSectionDisplay = (isLogin && !isAdmin) ? "block" : "none";

                    // 4. 템플릿에 전달할 맵 구성
                    Gson gson = new Gson();
                    Map<String, String> variables = new HashMap<>();
                    variables.put("roomId", String.valueOf(room.getRoomId()));
                    variables.put("roomName", room.getName() != null ? room.getName() : "");
                    variables.put("capacity", String.valueOf(room.getCapacity()));
                    variables.put("hourlyRate", room.getHourlyRate() != null ? room.getHourlyRate().toString() : "0");
                    variables.put("roleName", roleName);

                    // 💡 위에서 만든 스타일 제어 변수들을 맵에 담습니다.
                    variables.put("nonUserMenuDisplay", nonUserMenuDisplay);
                    variables.put("guestLinkDisplay", guestLinkDisplay);
                    variables.put("adminLinkDisplay", adminLinkDisplay);
                    variables.put("reservationSectionDisplay", reservationSectionDisplay);
                    variables.put("roomData", gson.toJson(room));

                    // 5. 템플릿 전송
                    HttpResponseUtil.sendTemplate(exchange, "room-detail.html", variables);
                    return;

                } else if ("/admin/rooms".equals(path)) {
                    // [1단계] 관리자 스터디룸 관리 화면 라우팅 로직

                    // 1. Service를 통해 DB에 등록된 전체 스터디룸 목록 조회
                    List<StudyRoom> roomList = studyRoomService.getRooms();

                    // 2. 프론트엔드(자바스크립트)에서 표(Table)를 쉽게 그리도록 JSON 문자열로 변환
                    Gson gson = new Gson();
                    String roomsJsonData = gson.toJson(roomList);

                    // 3. 템플릿에 전달할 변수 맵(Map) 구성
                    Map<String, String> variables = new HashMap<>();
                    variables.put("roomsData", roomsJsonData); // 스터디룸 전체 데이터
                    variables.put("roleName", "관리자");        // 상단 뱃지용

                    // 💡 [미리 준비] 4단계에서 잘못된 값 입력 시 에러 메시지를 띄우기 위한 빈 공간 세팅
                    variables.put("errorMessage", "");

                    // 4. admin-rooms.html 템플릿으로 응답 전송
                    HttpResponseUtil.sendTemplate(exchange, "admin-rooms.html", variables);
                    return;
                }

            } else if ("POST".equalsIgnoreCase(method)) {
                // [4단계] 관리자 스터디룸 등록/수정 POST 처리 로직
                if ("/admin/rooms/save".equals(path) || "/admin/rooms/update".equals(path)) {
                    try {
                        // 1. 폼 데이터 직접 읽기 및 파싱 (유틸리티 의존성 제거)
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        Map<String, String> formData = new java.util.HashMap<>();
                        if (body != null && !body.isBlank()) {
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
                        return;

                    } catch (BusinessException e) {
                        renderAdminRoomsWithError(exchange, e.getMessage());
                        return;
                    } catch (NumberFormatException e) {
                        renderAdminRoomsWithError(exchange, "수용 인원과 이용료는 올바른 숫자로 입력해주세요.");
                        return;
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
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
}