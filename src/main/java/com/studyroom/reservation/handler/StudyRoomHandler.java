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
                    String roleName = (session != null && session.getRole() != null) ? session.getRole().name() : "일반 회원";

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

                    // 3. 권한별 하단 버튼 및 예약 영역 동적 제어
                    String actionButton = "";
                    String reservationSection = "";

                    if (!isLogin) {
                        // [비회원] 목록으로, 홈으로, 로그인 안내
                        actionButton = "<li><a href=\"/login\">로그인 안내</a></li>";
                        reservationSection = "<article style=\"text-align: center; padding: 2rem;\"><p>예약은 로그인 후 이용 가능합니다. <a href=\"/login\"><strong>로그인하기</strong></a></p></article>";
                    } else if (isAdmin) {
                        // [관리자] 목록으로, 홈으로, 수정 화면 이동 (예약 신청 기능 미표시)
                        actionButton = "<li><a href=\"/admin/rooms/edit?roomId=" + room.getRoomId() + "\">수정 화면 이동</a></li>";
                        reservationSection = "<!-- 관리자에게는 예약 신청 영역이 표시되지 않습니다. -->";
                    } else {
                        // [일반 회원] 목록으로, 홈으로, 예약 신청 폼 표시
                        actionButton = ""; // 기본 목록/홈 버튼 사용
                        reservationSection =
                                "<article aria-labelledby=\"reservation-heading\">" +
                                        "    <header>" +
                                        "        <h2 id=\"reservation-heading\">예약 시간 입력</h2>" +
                                        "        <p>종료 일시는 시작 일시보다 늦어야 합니다.</p>" +
                                        "    </header>" +
                                        "    <form action=\"/reservations/create\" method=\"post\">" +
                                        "        <input type=\"hidden\" name=\"roomId\" value=\"" + room.getRoomId() + "\">" +
                                        "        <div>" +
                                        "            <label for=\"startTime\">시작 일시</label>" +
                                        "            <input id=\"startTime\" name=\"startTime\" type=\"datetime-local\" step=\"60\" required>" +
                                        "        </div>" +
                                        "        <div>" +
                                        "            <label for=\"endTime\">종료 일시</label>" +
                                        "            <input id=\"endTime\" name=\"endTime\" type=\"datetime-local\" step=\"60\" required>" +
                                        "        </div>" +
                                        "        <output role=\"status\" aria-live=\"polite\">{{resultMessage}}</output>" +
                                        "        <menu>" +
                                        "            <li><a href=\"/rooms\">목록으로</a></li>" +
                                        "            <li><a href=\"/home\">홈으로</a></li>" +
                                        "            <li><button type=\"submit\">예약 신청</button></li>" +
                                        "        </menu>" +
                                        "    </form>" +
                                        "</article>";
                    }

                    // 4. 템플릿에 전달할 맵 구성
                    Gson gson = new Gson();
                    Map<String, String> variables = new HashMap<>();
                    variables.put("roomId", String.valueOf(room.getRoomId()));
                    variables.put("roomName", room.getName() != null ? room.getName() : "");
                    variables.put("capacity", String.valueOf(room.getCapacity()));
                    variables.put("hourlyRate", room.getHourlyRate() != null ? room.getHourlyRate().toString() : "0");
                    variables.put("roleName", roleName);
                    variables.put("actionButton", actionButton);
                    variables.put("reservationSection", reservationSection);
                    variables.put("roomData", gson.toJson(room));

                    // 5. 템플릿 전송
                    HttpResponseUtil.sendTemplate(exchange, "room-detail.html", variables);
                    return;

                } else if ("/admin/rooms".equals(path)) {
                    // 스터디룸 관리 화면 로직이 들어갈 자리
                }

            } else if ("POST".equalsIgnoreCase(method)) {
                // ... (POST 관련 라우팅 로직) ...
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
}