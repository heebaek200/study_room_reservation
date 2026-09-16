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

    /**
     * 스터디룸 조회·상세 조회와 관리자 스터디룸 관리 요청을 처리합니다.
     * 비회원도 일반 스터디룸 조회 화면에는 접근할 수 있으며,
     * 관리자 경로는 로그인 여부와 ADMIN 권한을 확인한 뒤 처리합니다.
     *
     * @param exchange 현재 HTTP 요청과 응답
     * @throws IOException 요청 또는 응답 처리 중 오류가 발생한 경우
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method =
                exchange.getRequestMethod();

        String path =
                exchange.getRequestURI().getPath();

        try {
            /*
             * 이 Handler가 실제로 처리하는 경로인지 먼저 확인합니다.
             * HttpServer의 Context는 하위 경로까지 받을 수 있으므로
             * /rooms/test 같은 잘못된 주소를 Handler 내부에서 404로 걸러야 합니다.
             */
            if ("GET".equalsIgnoreCase(method)) {
                boolean validGetPath =
                        "/rooms".equals(path)
                                || "/rooms/detail".equals(path)
                                || "/admin/rooms".equals(path);

                if (!validGetPath) {
                    HttpResponseUtil.sendError(
                            exchange,
                            404,
                            "페이지를 찾을 수 없습니다.",
                            "요청한 페이지를 찾을 수 없습니다."
                    );
                    return;
                }

            } else if ("POST".equalsIgnoreCase(method)) {
                boolean validPostPath =
                        "/admin/rooms/create".equals(path)
                                || "/admin/rooms/update".equals(path);

                if (!validPostPath) {
                    HttpResponseUtil.sendError(
                            exchange,
                            404,
                            "페이지를 찾을 수 없습니다.",
                            "요청한 페이지를 찾을 수 없습니다."
                    );
                    return;
                }

            } else {
                /*
                 * 현재 StudyRoomHandler에서는 GET과 POST만 지원합니다.
                 * PUT, DELETE, PATCH 등의 요청은 405로 처리합니다.
                 */
                HttpResponseUtil.sendError(
                        exchange,
                        405,
                        "지원하지 않는 요청입니다.",
                        "허용되지 않은 요청 방식입니다."
                );
                return;
            }

            /*
             * 일반 스터디룸 조회는 비회원도 허용하므로
             * 로그인 실패를 오류로 처리하지 않고 session을 null로 유지합니다.
             */
            String sessionId =
                    HttpRequestUtil.findCookie(
                            exchange,
                            SESSION_COOKIE_NAME
                    );

            LoginSession session = null;

            try {
                session =
                        authService.requireLogin(
                                sessionId
                        );
            } catch (BusinessException e) {
                // 로그인되지 않은 요청은 비회원으로 처리합니다.
            }

            /*
             * 관리자 스터디룸 관련 경로는 ADMIN만 접근할 수 있습니다.
             * 비회원은 로그인 화면으로, 일반 회원은 홈 화면으로 이동합니다.
             */
            if (path.startsWith("/admin/rooms")) {
                if (session == null) {
                    HttpResponseUtil.redirect(
                            exchange,
                            LOGIN_PATH
                    );
                    return;
                }

                if (session.getRole() != UserRole.ADMIN) {
                    HttpResponseUtil.redirect(
                            exchange,
                            "/home"
                    );
                    return;
                }
            }

            /*
             * GET 요청 처리
             */
            if ("GET".equalsIgnoreCase(method)) {

                /*
                 * GET /rooms
                 * 비회원·일반 회원·관리자 모두 스터디룸 목록을 조회할 수 있습니다.
                 */
                if ("/rooms".equals(path)) {
                    List<StudyRoom> roomList =
                            studyRoomService.getRooms();

                    Gson gson =
                            new Gson();

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
                    return;
                }

                /*
                 * GET /rooms/detail
                 * 선택한 스터디룸의 상세 정보를 표시합니다.
                 */
                if ("/rooms/detail".equals(path)) {
                    String query =
                            exchange.getRequestURI()
                                    .getQuery();

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
                                    : room.getHourlyRate()
                                    .toString()
                    );

                    /*
                     * 예약 신청 영역은 일반 회원에게만 표시합니다.
                     * 게스트와 관리자는 스터디룸 정보만 확인할 수 있습니다.
                     */
                    values.put(
                            "reservationSectionDisplay",
                            session != null
                                    && session.getRole()
                                    == UserRole.USER
                                    ? "block"
                                    : "none"
                    );

                    Gson gson =
                            new Gson();

                    values.put(
                            "roomData",
                            gson.toJson(
                                    room
                            )
                    );

                    /*
                     * StudyRoomHandler에서 처음 상세 화면을 표시할 때에는
                     * 예약 입력값과 결과 메시지가 존재하지 않습니다.
                     */
                    values.put(
                            "startTime",
                            ""
                    );

                    values.put(
                            "endTime",
                            ""
                    );

                    values.put(
                            "resultMessage",
                            ""
                    );

                    applyCommonLayout(
                            session,
                            sessionId,
                            values,
                            fragments
                    );

                    HttpResponseUtil.sendTemplateWithHtml(
                            exchange,
                            "room-detail.html",
                            values,
                            fragments
                    );
                    return;
                }

                /*
                 * GET /admin/rooms
                 * 관리자에게 스터디룸 관리 화면을 표시합니다.
                 */
                if ("/admin/rooms".equals(path)) {
                    List<StudyRoom> roomList =
                            studyRoomService.getRooms();

                    Gson gson =
                            new Gson();

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

                    /*
                     * 공통 레이아웃에서는 기본적으로 스터디룸 메뉴가 활성화되므로
                     * 관리자 관리 화면에서는 스터디룸 관리 메뉴로 변경합니다.
                     */
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
                    return;
                }
            }

            /*
             * POST 요청 처리
             * 앞쪽 경로 검사에서 create/update만 통과하므로
             * 여기까지 왔다면 관리자 스터디룸 등록 또는 수정 요청입니다.
             */
            if ("POST".equalsIgnoreCase(method)) {
                try {
                    String body =
                            new String(
                                    exchange.getRequestBody()
                                            .readAllBytes(),
                                    StandardCharsets.UTF_8
                            );

                    Map<String, String> formData =
                            new HashMap<>();

                    /*
                     * application/x-www-form-urlencoded 요청 본문을
                     * 스터디룸 등록·수정에 필요한 값으로 변환합니다.
                     */
                    if (!body.isBlank()) {
                        for (String param : body.split("&")) {
                            String[] keyValue =
                                    param.split("=");

                            if (keyValue.length == 2) {
                                formData.put(
                                        java.net.URLDecoder.decode(
                                                keyValue[0],
                                                StandardCharsets.UTF_8
                                        ),
                                        java.net.URLDecoder.decode(
                                                keyValue[1],
                                                StandardCharsets.UTF_8
                                        )
                                );
                            }
                        }
                    }

                    String roomIdStr =
                            formData.get(
                                    "roomId"
                            );

                    String name =
                            formData.get(
                                    "name"
                            );

                    int capacity =
                            Integer.parseInt(
                                    formData.get(
                                            "capacity"
                                    )
                            );

                    BigDecimal hourlyRate =
                            new BigDecimal(
                                    formData.get(
                                            "hourlyRate"
                                    )
                            );

                    /*
                     * roomId가 없으면 신규 등록,
                     * roomId가 있으면 기존 스터디룸 수정으로 처리합니다.
                     */
                    if (roomIdStr == null
                            || roomIdStr.isBlank()) {

                        studyRoomService.createRoom(
                                sessionId,
                                name,
                                capacity,
                                hourlyRate
                        );

                    } else {
                        long roomId =
                                Long.parseLong(
                                        roomIdStr
                                );

                        studyRoomService.updateRoom(
                                sessionId,
                                roomId,
                                name,
                                capacity,
                                hourlyRate
                        );
                    }

                    /*
                     * POST 성공 후 GET 화면으로 이동해
                     * 새로고침에 의한 중복 요청을 방지합니다.
                     */
                    HttpResponseUtil.redirect(
                            exchange,
                            "/admin/rooms"
                    );
                    return;

                } catch (BusinessException e) {
                    /*
                     * 업무 규칙 위반은 관리자 화면을 유지하면서
                     * 오류 메시지를 함께 표시합니다.
                     */
                    renderAdminRoomsWithError(
                            exchange,
                            session,
                            sessionId,
                            e.getMessage()
                    );
                    return;

                } catch (NumberFormatException e) {
                    /*
                     * 수용 인원 또는 이용료 숫자 변환 실패도
                     * 동일한 관리자 화면에서 안내합니다.
                     */
                    renderAdminRoomsWithError(
                            exchange,
                            session,
                            sessionId,
                            "수용 인원과 이용료는 올바른 숫자로 입력해주세요."
                    );
                    return;
                }
            }

        } catch (BusinessException e) {
            /*
             * 상세 조회의 잘못된 roomId 등
             * 사용자 요청 자체의 문제가 발생한 경우입니다.
             */
            HttpResponseUtil.sendError(
                    exchange,
                    400,
                    "요청을 처리할 수 없습니다.",
                    e.getMessage()
            );

        } catch (SQLException e) {
            /*
             * 사용자 정보 또는 데이터베이스 조회 실패는
             * 사용자 입력 오류가 아니므로 500으로 처리합니다.
             */
            HttpResponseUtil.sendError(
                    exchange,
                    500,
                    "오류가 발생했습니다.",
                    "요청을 처리하는 중 오류가 발생했습니다."
            );

        } catch (Exception e) {
            /*
             * 그 밖의 예상하지 못한 오류도
             * 공통 500 오류 화면으로 처리합니다.
             */
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

    /**
     * 관리자 스터디룸 등록 또는 수정 실패 시
     * 입력 오류 메시지를 포함한 관리자 스터디룸 관리 화면을 다시 렌더링합니다.
     * 정상 조회 화면과 동일한 공통 헤더·내비게이션·푸터를 적용합니다.
     *
     * @param exchange 현재 HTTP 요청과 응답
     * @param session 현재 로그인 관리자 세션
     * @param sessionId 현재 세션 ID
     * @param errorMessage 화면에 표시할 오류 메시지
     * @throws IOException 화면 응답 중 오류가 발생한 경우
     * @throws SQLException 사용자 정보 조회 중 오류가 발생한 경우
     */
    private void renderAdminRoomsWithError(
            HttpExchange exchange,
            LoginSession session,
            String sessionId,
            String errorMessage
    ) throws IOException, SQLException {

        List<StudyRoom> roomList =
                studyRoomService.getRooms();

        Gson gson =
                new Gson();

        Map<String, String> values =
                new HashMap<>();

        Map<String, String> fragments =
                new HashMap<>();

        // 현재 스터디룸 목록과 오류 메시지를 다시 전달합니다.
        values.put(
                "roomsData",
                gson.toJson(
                        roomList
                )
        );

        values.put(
                "errorMessage",
                errorMessage
        );

        // 정상 조회 화면과 동일한 공통 레이아웃을 적용합니다.
        applyCommonLayout(
                session,
                sessionId,
                values,
                fragments
        );

        // 현재 화면은 관리자 스터디룸 관리 메뉴입니다.
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