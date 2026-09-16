package com.studyroom.reservation.handler;

import com.studyroom.reservation.dto.Reservation;
import com.studyroom.reservation.dto.StudyRoom;
import com.studyroom.reservation.enums.ReservationStatus;
import com.studyroom.reservation.enums.UserRole;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.service.AuthService;
import com.studyroom.reservation.service.MemberReservationQueryService;
import com.studyroom.reservation.service.StudyRoomService;
import com.studyroom.reservation.session.LoginSession;
import com.studyroom.reservation.util.HttpRequestUtil;
import com.studyroom.reservation.util.HttpResponseUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 일반 회원의 내 예약 목록·상세 조회 화면을 처리합니다.
 * 본인의 예약만 조회할 수 있으며, 다른 회원의 예약 조회는 차단합니다.
 */
public final class ReservationQueryHandler implements HttpHandler {

    // 이 핸들러가 응답할 경로. Main.java에서 server.createContext("/my-reservations", ...)로
    // 등록될 예정이라, 여기서도 같은 문자열로 맞춰서 "이 경로가 맞는 요청인가"를 검사할 때 씁니다.
    // (Issue #29에서 확정한 라우팅: 목록 "/my-reservations", 상세 "/my-reservations/detail?reservationId=")
    private static final String QUERY_PATH = "/my-reservations";

    // 예약 상세 조회 경로. "/my-reservations" 컨텍스트 하위 경로라서 같은 context 등록으로 함께 들어옵니다.
    private static final String DETAIL_PATH = "/my-reservations/detail";

    // 로그인이 안 되어 있을 때 돌려보낼 경로.
    private static final String LOGIN_PATH = "/login";

    // 로그인 세션을 식별하는 쿠키 이름. ReservationCreateHandler 등 다른 핸들러와 동일한 값을 씁니다.
    private static final String SESSION_COOKIE_NAME = "SESSION_ID";

    // 날짜/시간을 "2026-09-16 14:30" 형태의 보기 좋은 문자열로 바꿀 때 사용.
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // 로그인 여부/세션 정보를 확인할 때 사용 (requireLogin(sessionId) 호출용)
    private final AuthService authService;

    // 예약에 연결된 스터디룸의 이름 등 상세 정보를 조회할 때 사용
    private final StudyRoomService studyRoomService;

    // 실제 "내 예약 목록 조회", "예약 상세 조회(+ 본인 소유 검증)" 로직을 담당.
    // DAO/쿼리 로직은 이미 이 서비스 안에 구현되어 있으므로, 핸들러는 이 서비스만 호출하면 됩니다.
    private final MemberReservationQueryService reservationQueryService;

    // 생성자로 위 세 가지 협력 객체를 주입받습니다.
    // (Main.java에서 new ReservationQueryHandler(authService, studyRoomService, reservationQueryService) 형태로 생성될 예정)
    public ReservationQueryHandler(
            AuthService authService,
            StudyRoomService studyRoomService,
            MemberReservationQueryService reservationQueryService
    ) {
        this.authService = authService;
        this.studyRoomService = studyRoomService;
        this.reservationQueryService = reservationQueryService;
    }

    // HttpHandler 인터페이스가 요구하는 유일한 메서드.
    // 서버가 "/my-reservations" 요청을 받으면 이 메서드가 호출됩니다.
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        // ① 경로 검사: 이 핸들러가 담당하는 경로(목록 또는 상세)가 아니면 404.
        // (return을 빼먹으면 아래 로직이 이어서 실행돼버리니 반드시 return으로 끝내야 함)
        boolean isListPath = QUERY_PATH.equals(path);
        boolean isDetailPath = DETAIL_PATH.equals(path);

        if (!isListPath && !isDetailPath) {
            sendMessage(exchange, 404, "요청한 페이지를 찾을 수 없습니다.");
            return;
        }

        // ② 메서드 검사: 이 Issue는 "조회"만 다루므로 GET만 허용.
        if (!"GET".equalsIgnoreCase(method)) {
            sendMessage(exchange, 405, "허용되지 않은 요청 방식입니다.");
            return;
        }

        // ③ 로그인 확인: 쿠키에서 세션 아이디를 꺼내 authService에 검증을 맡김.
        // 로그인이 안 되어 있으면 requireLogin이 BusinessException을 던지므로,
        // catch에서 로그인 페이지로 돌려보내고 메서드를 끝냅니다.
        String sessionId = HttpRequestUtil.findCookie(exchange, SESSION_COOKIE_NAME);
        LoginSession session;

        try {
            session = authService.requireLogin(sessionId);
        } catch (BusinessException e) {
            HttpResponseUtil.redirect(exchange, LOGIN_PATH);
            return;
        }

        // ④ 권한 검사: 이 화면은 일반 회원(USER) 전용. 관리자는 /admin/reservations 쪽에서 처리.
        if (session.getRole() != UserRole.USER) {
            sendMessage(exchange, 403, "일반 회원만 이용할 수 있는 화면입니다.");
            return;
        }

        // ⑤ 목록/상세 분기: 경로 자체로 구분합니다.
        // 예) /my-reservations                              -> 목록
        //     /my-reservations/detail?reservationId=5        -> 5번 예약 상세
        // parseReservationId(등)에서 BusinessException이 날 수 있으므로 여기서 한 번에 감싸서
        // 400 안내 화면으로 응답합니다. (ReservationCreateHandler.handle()과 동일한 패턴)
        try {
            if (isListPath) {
                handleList(exchange, session);
            } else {
                Map<String, String> query = HttpRequestUtil.parseQuery(exchange);
                String reservationIdValue = query.get("reservationId");

                handleDetail(exchange, session, reservationIdValue);
            }
        } catch (BusinessException e) {
            sendMessage(exchange, 400, e.getMessage());
        }
    }

    /**
     * 로그인한 회원 본인의 예약 목록을 조회해서 화면에 표시합니다.
     */
    private void handleList(HttpExchange exchange, LoginSession session) throws IOException {
        // service.getMyReservations는 이미 구현되어 있음: 이 회원의 예약을 최신순으로 반환.
        List<Reservation> reservations =
                reservationQueryService.getMyReservations(session.getUserId());

        // 목록 데이터를 <table> HTML 문자열로 만든 뒤,
        String content = buildListContent(reservations);

        // my-reservations.html 템플릿의 %s 자리에 끼워서 응답.
        sendPage(exchange, content);
    }

    /**
     * 예약 번호(reservationId)로 상세 정보를 조회해서 화면에 표시합니다.
     * 본인의 예약이 아니면(다른 회원 것이거나 존재하지 않으면) 접근을 차단합니다.
     */
    private void handleDetail(HttpExchange exchange, LoginSession session, String reservationIdValue) throws IOException {
        // 쿼리스트링으로 들어온 문자열(reservationId)을 숫자로 변환. 없거나 잘못된 값이면 400 안내.
        long reservationId = parseReservationId(reservationIdValue);

        // getReservationDetail(reservationId, 로그인한 회원 id, role)
        // -> 서비스 내부에서 role이 USER면 findByIdAndUserId로 조회하므로,
        //    reservationId가 존재해도 "내 소유가 아니면" 자동으로 Optional.empty()가 됩니다.
        //    => 이게 바로 "다른 회원 예약 접근 차단" 요구사항이 지켜지는 지점.
        Optional<Reservation> reservation = reservationQueryService.getReservationDetail(
                reservationId,
                session.getUserId(),
                session.getRole().name()
        );

        if (reservation.isEmpty()) {
            sendMessage(exchange, 404, "예약을 찾을 수 없거나 접근 권한이 없습니다.");
            return;
        }

        String content = buildDetailContent(reservation.get());

        sendPage(exchange, content);
    }

    /**
     * 예약 목록을 <table> 문자열로 만듭니다. 예약이 하나도 없으면 안내 문구만 반환.
     */
    private String buildListContent(List<Reservation> reservations) {
        if (reservations.isEmpty()) {
            return "<p>아직 예약 내역이 없습니다.</p>";
        }

        // 예약 개수만큼 <tr> 한 줄씩 만들어서 이어붙입니다. (UserHandler.handleAdminUsers와 같은 방식)
        StringBuilder rows = new StringBuilder();

        for (Reservation reservation : reservations) {
            String roomName = findRoomName(reservation.getRoomId());

            rows.append("""
                    <tr>
                        <td>%d</td>
                        <td>%s</td>
                        <td>%s</td>
                        <td>%s</td>
                        <td>%s</td>
                        <td>%s</td>
                        <td>%s</td>
                    </tr>
                    """.formatted(
                    reservation.getReservationId(),
                    escapeHtml(roomName),
                    formatDateTime(reservation.getStartTime()),
                    formatDateTime(reservation.getEndTime()),
                    escapeHtml(formatPrice(reservation.getTotalPrice())),
                    escapeHtml(statusLabel(reservation)),
                    buildActionCell(reservation)
            ));
        }

        return """
                <table>
                    <thead>
                        <tr>
                            <th>예약번호</th>
                            <th>스터디룸</th>
                            <th>시작 일시</th>
                            <th>종료 일시</th>
                            <th>이용 금액</th>
                            <th>상태</th>
                            <th>관리</th>
                        </tr>
                    </thead>
                    <tbody>
                        %s
                    </tbody>
                </table>
                """.formatted(rows.toString());
    }

    /**
     * 목록의 "관리" 칸을 만듭니다. 상세보기 링크는 항상 표시하고,
     * CONFIRMED 상태일 때만 예약 취소 버튼(Issue #30)을 추가로 보여줍니다.
     */
    private String buildActionCell(Reservation reservation) {
        String detailLink = """
                <a href="/my-reservations/detail?reservationId=%d">상세보기</a>
                """.formatted(reservation.getReservationId());

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            return detailLink;
        }

        String cancelForm = """
                <form action="/reservations/cancel" method="post">
                    <input type="hidden" name="reservationId" value="%d">
                    <button type="submit">예약 취소</button>
                </form>
                """.formatted(reservation.getReservationId());

        return detailLink + cancelForm;
    }

    /**
     * 예약 한 건의 상세 정보를 보여줄 HTML 조각을 만듭니다.
     */
    private String buildDetailContent(Reservation reservation) {
        String roomName = findRoomName(reservation.getRoomId());

        return """
                <article>
                    <dl>
                        <div><dt>예약번호</dt><dd>%d</dd></div>
                        <div><dt>스터디룸</dt><dd>%s</dd></div>
                        <div><dt>시작 일시</dt><dd>%s</dd></div>
                        <div><dt>종료 일시</dt><dd>%s</dd></div>
                        <div><dt>이용 금액</dt><dd>%s</dd></div>
                        <div><dt>예약 상태</dt><dd>%s</dd></div>
                    </dl>
                    <p><a href="/my-reservations">목록으로</a></p>
                </article>
                """.formatted(
                reservation.getReservationId(),
                escapeHtml(roomName),
                formatDateTime(reservation.getStartTime()),
                formatDateTime(reservation.getEndTime()),
                escapeHtml(formatPrice(reservation.getTotalPrice())),
                escapeHtml(statusLabel(reservation))
        );
    }

    /**
     * 예약에 연결된 스터디룸 이름을 조회합니다.
     * 방이 삭제되었거나 조회 실패해도 화면이 깨지지 않도록 방어적으로 처리.
     */
    private String findRoomName(Long roomId) {
        try {
            StudyRoom room = studyRoomService.getRoom(roomId);
            return room.getName();
        } catch (BusinessException e) {
            return "스터디룸 #" + roomId;
        }
    }

    // LocalDateTime -> "yyyy-MM-dd HH:mm" 문자열. 값이 없으면 "-".
    private String formatDateTime(java.time.LocalDateTime value) {
        if (value == null) {
            return "-";
        }

        return value.format(DATE_TIME_FORMATTER);
    }

    // 금액 표시. totalPrice가 null인 예외 상황에도 화면이 깨지지 않도록 "-"로 방어.
    private String formatPrice(BigDecimal price) {
        if (price == null) {
            return "-";
        }

        return price.toPlainString() + "원";
    }

    // ReservationStatus enum 값을 한글 라벨로 변환.
    private String statusLabel(Reservation reservation) {
        if (reservation.getStatus() == null) {
            return "-";
        }

        return switch (reservation.getStatus()) {
            case CONFIRMED -> "예약 확정";
            case CANCELLED -> "예약 취소";
        };
    }

    // 쿼리스트링의 reservationId 값을 양의 정수로 변환.
    // 값이 없거나 형식이 잘못되면 BusinessException을 던지고, handle()의 try/catch에서 400으로 응답합니다.
    private long parseReservationId(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("예약 번호를 입력해 주세요.");
        }

        try {
            long reservationId = Long.parseLong(value);

            if (reservationId <= 0) {
                throw new BusinessException("올바른 예약 번호를 입력해 주세요.");
            }

            return reservationId;
        } catch (NumberFormatException e) {
            throw new BusinessException("올바른 예약 번호를 입력해 주세요.");
        }
    }

    /**
     * my-reservations.html 템플릿을 읽어서 %s 자리에 content를 채운 뒤 응답합니다.
     * (UserHandler.sendUsersPage와 같은 방식)
     */
    private void sendPage(HttpExchange exchange, String content) throws IOException {
        String resourcePath = "/templates/my-reservations.html";

        try (InputStream inputStream = ReservationQueryHandler.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                sendMessage(exchange, 404, "화면 파일을 찾을 수 없습니다.");
                return;
            }

            String template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            String html = template.formatted(content);

            byte[] responseBody = html.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
        } finally {
            exchange.close();
        }
    }

    /**
     * 화면을 반환할 수 없는 상황(404/403/405 등)을 간단한 안내 페이지로 응답합니다.
     * UserHandler.sendMessage(...)와 같은 역할을 하는 헬퍼입니다.
     */
    private void sendMessage(HttpExchange exchange, int statusCode, String message) throws IOException {
        String html = """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <title>처리 결과</title>
                </head>
                <body>
                    <h1>처리 결과</h1>
                    <p>%s</p>
                    <p><a href="/my-reservations">내 예약으로</a></p>
                </body>
                </html>
                """.formatted(escapeHtml(message));

        byte[] responseBody = html.getBytes(StandardCharsets.UTF_8);

        try {
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, responseBody.length);
            exchange.getResponseBody().write(responseBody);
        } finally {
            exchange.close();
        }
    }

    // 메시지 안에 <, > 같은 HTML 특수문자가 있어도 안전하게 보이도록 이스케이프 처리.
    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
