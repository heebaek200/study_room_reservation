package com.studyroom.reservation.handler;

import com.studyroom.reservation.dto.Refund;
import com.studyroom.reservation.dto.User;
import com.studyroom.reservation.enums.RefundStatus;
import com.studyroom.reservation.enums.UserRole;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.service.AuthService;
import com.studyroom.reservation.service.MemberRefundQueryService;
import com.studyroom.reservation.service.ReservationCancelService;
import com.studyroom.reservation.service.UserService;
import com.studyroom.reservation.session.LoginSession;
import com.studyroom.reservation.util.HttpRequestUtil;
import com.studyroom.reservation.util.HttpResponseUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReservationCancelHandler implements HttpHandler {

    // 이 핸들러가 처리할 경로. POST로만 요청을 받습니다 (예약 취소는 상태를 바꾸는 동작이라 GET이 아니라 POST).
    private static final String CANCEL_PATH = "/reservations/cancel";

    // 로그인이 안 되어 있을 때 돌려보낼 경로. ReservationQueryHandler와 동일한 값.
    private static final String LOGIN_PATH = "/login";

    // 로그인 세션을 식별하는 쿠키 이름. 다른 핸들러들과 동일한 값을 씁니다.
    private static final String SESSION_COOKIE_NAME = "SESSION_ID";

    private final ReservationCancelService reservationCancelService;
    private final MemberRefundQueryService memberRefundQueryService;
    private final AuthService authService;
    private final UserService userService;


    public ReservationCancelHandler(ReservationCancelService reservationCancelService, MemberRefundQueryService memberRefundQueryService, AuthService authService, UserService userService) {
        this.reservationCancelService = reservationCancelService;
        this.memberRefundQueryService = memberRefundQueryService;
        this.authService = authService;
        this.userService = userService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (!path.equals(CANCEL_PATH)) {
            HttpResponseUtil.sendError(
                    exchange,
                    404,
                    "페이지를 찾을 수 없습니다.",
                    "요청한 페이지를 찾을 수 없습니다."
            );
            return;
        }
        if (!"POST".equalsIgnoreCase(method)) {
            HttpResponseUtil.sendError(
                    exchange,
                    405,
                    "지원하지 않는 요청입니다.",
                    "허용되지 않은 요청 방식입니다."
            );
            return;
        }
        String sessionId = HttpRequestUtil.findCookie(exchange, SESSION_COOKIE_NAME);
        LoginSession session;

        try {
            session = authService.requireLogin(sessionId);
        } catch (BusinessException e) {
            HttpResponseUtil.redirect(exchange, LOGIN_PATH);
            return;
        }
        if (session.getRole() != UserRole.USER) {
            HttpResponseUtil.sendError(
                    exchange,
                    403,
                    "접근할 수 없습니다.",
                    "일반 회원만 이용할 수 있는 기능입니다."
            );
            return;
        }
        Map<String, String> form = HttpRequestUtil.parseForm(exchange);
        String reservationIdValue = form.get("reservationId");

        try {
            long reservationId = parseReservationId(reservationIdValue);

            // 취소 처리. 이 메서드 안에서 이미 "본인 소유인지", "CONFIRMED 상태인지"까지
            // 전부 확인하고, 취소(CANCELLED)와 환불 요청(REQUESTED) 생성을 트랜잭션으로 처리합니다.
            // 조건에 안 맞으면(남의 예약, 이미 취소됨 등) BusinessException을 던집니다.
            reservationCancelService.cancelReservation(sessionId, reservationId);

            // 방금 생성된 환불 요청을 찾아서 결과 화면에 같이 보여줍니다.
            Refund refund = findRefundByReservationId(sessionId, reservationId);

            sendCancelResult(
                    exchange,
                    sessionId,
                    true,
                    "예약이 취소되었습니다.",
                    refund
            );
        } catch (BusinessException e) {
            // 남의 예약이거나, 이미 취소됐거나, 잘못된 예약번호인 경우 등
            // -> "취소 성공·실패 결과 표시" 요구사항의 "실패" 케이스
            try {
                sendCancelResult(
                        exchange,
                        sessionId,
                        false,
                        e.getMessage(),
                        null
                );
            } catch (SQLException ex) {
                HttpResponseUtil.sendError(
                        exchange,
                        500,
                        "오류가 발생했습니다.",
                        "예약 취소 처리 중 오류가 발생했습니다."
                );
            }
        } catch (SQLException e) {
            HttpResponseUtil.sendError(
                    exchange,
                    500,
                    "오류가 발생했습니다.",
                    "예약 취소 처리 중 오류가 발생했습니다."
            );
        }
    }

    /**
     * 방금 취소된 예약(reservationId)에 연결된 환불 요청을 찾습니다.
     * "최신순 정렬이니까 첫 번째 것"이 아니라, reservationId가 일치하는 걸 직접 찾습니다.
     */
    private Refund findRefundByReservationId(String sessionId, long reservationId) throws SQLException {
        List<Refund> refunds = memberRefundQueryService.getMyRefunds(sessionId);

        for (Refund refund : refunds) {
            if (refund.getReservationId() == reservationId) {
                return refund;
            }
        }

        return null;
    }

    /**
     * 예약 취소 결과 화면을 공통 레이아웃과 함께 반환합니다.
     * 현재 로그인 사용자의 이름과 일반 회원용 내비게이션 정보를 전달하고,
     * 환불 결과 영역은 서버에서 생성한 HTML 조각으로 삽입합니다.
     *
     * @param exchange 현재 HTTP 요청과 응답
     * @param sessionId 현재 로그인 세션 ID
     * @param success 예약 취소 성공 여부
     * @param message 사용자에게 표시할 결과 메시지
     * @param refund 생성된 환불 요청 정보
     * @throws IOException 응답 처리 중 오류가 발생한 경우
     */
    private void sendCancelResult(
            HttpExchange exchange,
            String sessionId,
            boolean success,
            String message,
            Refund refund
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

        // 성공 시에만 환불 요청 결과 영역을 생성합니다.
        String refundSection = "";

        if (refund != null) {
            refundSection = """
            <section>
                <h2>환불 요청 결과</h2>

                <dl>
                    <div>
                        <dt>환불 상태</dt>
                        <dd>{{refundStatus}}</dd>
                    </div>
                </dl>
            </section>
            """;
        }

        Map<String, String> values =
                new HashMap<>();

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

        values.put(
                "homeCurrent",
                ""
        );
        values.put(
                "roomsCurrent",
                ""
        );
        values.put(
                "myInfoCurrent",
                ""
        );
        values.put(
                "myReservationsCurrent",
                "current"
        );
        values.put(
                "myRefundsCurrent",
                ""
        );

        // 예약 취소 결과 화면에 표시할 데이터
        values.put(
                "resultTitle",
                success
                        ? "취소 완료"
                        : "취소 실패"
        );

        values.put(
                "message",
                message
        );

        values.put(
                "refundStatus",
                refund == null
                        ? ""
                        : refundStatusLabel(
                        refund.getStatus()
                )
        );

        HttpResponseUtil.sendTemplateWithHtml(
                exchange,
                "reservation-cancel-result.html",
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
                        ),
                        "refundSection",
                        refundSection
                )
        );
    }

    // RefundStatus enum 값을 한글 라벨로 변환.
    private String refundStatusLabel(RefundStatus status) {
        if (status == null) {
            return "-";
        }

        return switch (status) {
            case REQUESTED -> "환불 요청됨";
            case APPROVED -> "환불 승인됨";
            case REJECTED -> "환불 거절됨";
        };
    }

    /**
     * 화면을 반환할 수 없는 상황(404/403/405 등)을 간단한 안내 페이지로 응답합니다.
     * ReservationQueryHandler.sendMessage(...)와 동일한 헬퍼입니다.
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

        try (exchange) {
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, responseBody.length);
            exchange.getResponseBody().write(responseBody);
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
}
