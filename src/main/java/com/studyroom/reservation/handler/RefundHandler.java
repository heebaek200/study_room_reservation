package com.studyroom.reservation.handler;

import com.studyroom.reservation.dto.Refund;
import com.studyroom.reservation.dto.Reservation;
import com.studyroom.reservation.dto.User;
import com.studyroom.reservation.enums.RefundStatus;
import com.studyroom.reservation.enums.ReservationStatus;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.service.AdminRefundService;
import com.studyroom.reservation.service.AdminReservationQueryService;
import com.studyroom.reservation.service.MemberRefundQueryService;
import com.studyroom.reservation.service.UserService;
import com.studyroom.reservation.util.HttpRequestUtil;
import com.studyroom.reservation.util.HttpResponseUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RefundHandler implements HttpHandler {

    private static final String SESSION_COOKIE_NAME = "SESSION_ID";

    private final MemberRefundQueryService memberRefundQueryService;
    private final AdminReservationQueryService adminReservationQueryService;
    private final AdminRefundService adminRefundService;
    private final UserService userService;

    public RefundHandler(
            MemberRefundQueryService memberRefundQueryService,
            AdminReservationQueryService adminReservationQueryService,
            AdminRefundService adminRefundService,
            UserService userService
    ) {
        this.memberRefundQueryService =
                memberRefundQueryService;
        this.adminReservationQueryService =
                adminReservationQueryService;
        this.adminRefundService =
                adminRefundService;
        this.userService =
                userService;
    }

    @Override
    public void handle(HttpExchange exchange)
            throws IOException {

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {

            if ("/my-refunds".equals(path)
                    && "GET".equalsIgnoreCase(method)) {
                handleMyRefunds(exchange);
                return;
            }

            if ("/admin/reservations".equals(path)
                    && "GET".equalsIgnoreCase(method)) {
                handleAdminReservations(exchange);
                return;
            }

            if ("/admin/refunds".equals(path)
                    && "GET".equalsIgnoreCase(method)) {
                handleAdminRefunds(exchange);
                return;
            }

            if ("/admin/refunds/approve".equals(path)
                    && "POST".equalsIgnoreCase(method)) {
                processRefund(exchange, true);
                return;
            }

            if ("/admin/refunds/reject".equals(path)
                    && "POST".equalsIgnoreCase(method)) {
                processRefund(exchange, false);
                return;
            }

            HttpResponseUtil.sendError(
                    exchange,
                    404,
                    "페이지를 찾을 수 없습니다.",
                    "요청한 페이지를 찾을 수 없습니다."
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
                    "환불 관련 요청 처리 중 오류가 발생했습니다."
            );
        }
    }

    /**
     * 일반 회원의 본인 환불 내역을 조회합니다.
     */
    private void handleMyRefunds(
            HttpExchange exchange
    ) throws IOException, SQLException {

        String sessionId = findSessionId(exchange);

        User currentUser;
        try {
            currentUser =
                    userService.getMyInfo(
                            sessionId
                    );
        } catch (BusinessException e) {
            HttpResponseUtil.redirect(
                    exchange,
                    "/login"
            );
            return;
        }

        List<Refund> refunds =
                memberRefundQueryService.getMyRefunds(sessionId);

        Map<String, String> values = new HashMap<>();

        values.put("userName", currentUser.getName());
        values.put("roleName", "일반 회원");
        values.put("roleClass", "");

        values.put("homeCurrent", "");
        values.put("roomsCurrent", "");
        values.put("myInfoCurrent", "");
        values.put("myReservationsCurrent", "");
        values.put("myRefundsCurrent", "current");

        values.put(
                "emptyMessage",
                refunds.isEmpty()
                        ? "환불 내역이 없습니다."
                        : ""
        );

        HttpResponseUtil.sendTemplateWithHtml(
                exchange,
                "my-refunds.html",
                values,
                Map.of(
                        "header",
                        HttpResponseUtil.loadFragment("app-header.html"),
                        "navigation",
                        HttpResponseUtil.loadFragment("nav-user.html"),
                        "footer",
                        HttpResponseUtil.loadFragment("app-footer.html"),
                        "refundRows",
                        buildMemberRefundRows(refunds)
                )
        );
    }

    /**
     * 관리자의 전체 예약 목록 또는 상세 예약을 조회합니다.
     */
    private void handleAdminReservations(
            HttpExchange exchange
    ) throws IOException, SQLException {

        String sessionId = findSessionId(exchange);

        User currentUser;
        try {
            currentUser =
                    userService.getMyInfo(
                            sessionId
                    );
        } catch (BusinessException e) {
            HttpResponseUtil.redirect(
                    exchange,
                    "/login"
            );
            return;
        }

        Map<String, String> query =
                HttpRequestUtil.parseQuery(exchange);

        String reservationIdValue =
                query.get("reservationId");

        List<Reservation> reservations;

        if (reservationIdValue == null
                || reservationIdValue.isBlank()) {

            reservations =
                    adminReservationQueryService
                            .getReservations(sessionId);
        } else {
            long reservationId =
                    parseId(
                            reservationIdValue,
                            "예약 번호"
                    );

            Reservation reservation =
                    adminReservationQueryService.getReservation(
                            sessionId,
                            reservationId
                    );

            reservations = List.of(reservation);
        }


        Map<String, String> values = new HashMap<>();

        values.put("userName", currentUser.getName());
        values.put("roleName", "관리자");
        values.put("roleClass", "admin");

        values.put("homeCurrent", "");
        values.put("roomsCurrent", "");
        values.put("adminRoomsCurrent", "");
        values.put("adminUsersCurrent", "");
        values.put("adminReservationsCurrent", "current");
        values.put("adminRefundsCurrent", "");

        HttpResponseUtil.sendTemplateWithHtml(
                exchange,
                "admin-reservations.html",
                values,
                Map.of(
                        "header",
                        HttpResponseUtil.loadFragment("app-header.html"),
                        "navigation",
                        HttpResponseUtil.loadFragment("nav-admin.html"),
                        "footer",
                        HttpResponseUtil.loadFragment("app-footer.html"),
                        "reservationRows",
                        buildReservationRows(reservations)
                )
        );
    }

    /**
     * 관리자의 환불 목록·상태별 목록·상세 정보를 조회합니다.
     */
    private void handleAdminRefunds(
            HttpExchange exchange
    ) throws IOException, SQLException {

        String sessionId = findSessionId(exchange);

        User currentUser;
        try {
            currentUser =
                    userService.getMyInfo(
                            sessionId
                    );
        } catch (BusinessException e) {
            HttpResponseUtil.redirect(
                    exchange,
                    "/login"
            );
            return;
        }

        Map<String, String> query =
                HttpRequestUtil.parseQuery(exchange);

        String refundIdValue = query.get("refundId");
        String statusValue = query.get("status");

        String resultMessage =
                "success".equals(query.get("result"))
                        ? "처리가 완료되었습니다."
                        : "";

        List<Refund> refunds;

        // 환불 번호가 있으면 상세 조회를 우선합니다.
        if (refundIdValue != null && !refundIdValue.isBlank()) {
            long refundId = parseId(
                    refundIdValue,
                    "환불 번호"
            );

            Refund refund = adminRefundService.getRefund(
                    sessionId,
                    refundId
            );

            // 기존 테이블 출력 메서드를 재사용합니다.
            refunds = List.of(refund);

        } else if (statusValue == null
                || statusValue.isBlank()
                || "ALL".equalsIgnoreCase(statusValue)) {

            refunds = adminRefundService.getRefunds(sessionId);

        } else {
            RefundStatus status = parseRefundStatus(statusValue);

            refunds = adminRefundService.getRefundsByStatus(
                    sessionId,
                    status
            );
        }


        Map<String, String> values = new HashMap<>();

        values.put("userName", currentUser.getName());
        values.put("roleName", "관리자");
        values.put("roleClass", "admin");

        values.put("homeCurrent", "");
        values.put("roomsCurrent", "");
        values.put("adminRoomsCurrent", "");
        values.put("adminUsersCurrent", "");
        values.put("adminReservationsCurrent", "");
        values.put("adminRefundsCurrent", "current");
        values.put("resultMessage", defaultString(resultMessage));

        HttpResponseUtil.sendTemplateWithHtml(
                exchange,
                "admin-refunds.html",
                values,
                Map.of(
                        "header",
                        HttpResponseUtil.loadFragment("app-header.html"),
                        "navigation",
                        HttpResponseUtil.loadFragment("nav-admin.html"),
                        "footer",
                        HttpResponseUtil.loadFragment("app-footer.html"),
                        "refundRows",
                        buildAdminRefundRows(refunds)
                )
        );
    }

    /**
     * 관리자 환불 승인 또는 거절을 처리합니다.
     */
    private void processRefund(
            HttpExchange exchange,
            boolean approve
    ) throws IOException, SQLException {

        String sessionId = findSessionId(exchange);
        Map<String, String> form =
                HttpRequestUtil.parseForm(exchange);

        long refundId =
                parseId(
                        form.get("refundId"),
                        "환불 번호"
                );

        if (approve) {
            adminRefundService.approveRefund(
                    sessionId,
                    refundId
            );
        } else {
            adminRefundService.rejectRefund(
                    sessionId,
                    refundId
            );
        }

        HttpResponseUtil.redirect(
                exchange,
                "/admin/refunds?result=success"
        );
    }

    /**
     * 회원용 환불 목록 HTML을 생성합니다.
     */
    private String buildMemberRefundRows(
            List<Refund> refunds
    ) {
        if (refunds.isEmpty()) {
            return "";
        }

        StringBuilder rows = new StringBuilder();

        for (Refund refund : refunds) {
            rows.append("""
            <tr>
                <td>%d</td>
                <td>%d</td>
                <td>%s</td>
                <td>%s원</td>
                <td>%s</td>
            </tr>
            """.formatted(
                    refund.getRefundId(),
                    refund.getReservationId(),
                    escapeHtml(refund.getRoomName()),
                    String.format("%,.0f", refund.getRefundAmount()),
                    refundStatusLabel(refund.getStatus()) // 아래 메서드 호출
            ));
        }

        return rows.toString();
    }

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

    /**
     * 관리자 환불 목록 HTML을 생성합니다.
     */
    private String buildAdminRefundRows(
            List<Refund> refunds
    ) {
        if (refunds.isEmpty()) {
            return """
                    <tr>
                        <td colspan="4">
                            환불 내역이 없습니다.
                        </td>
                    </tr>
                    """;
        }

        StringBuilder rows = new StringBuilder();

        for (Refund refund : refunds) {
            String action = "";

            if (refund.getStatus() == RefundStatus.REQUESTED) {
                action = """
                        <form action="/admin/refunds/approve"
                              method="post">
                            <input type="hidden"
                                   name="refundId"
                                   value="%d">
                            <button type="submit">승인</button>
                        </form>
                        <form action="/admin/refunds/reject"
                              method="post">
                            <input type="hidden"
                                   name="refundId"
                                   value="%d">
                            <button type="submit">거절</button>
                        </form>
                        """.formatted(
                        refund.getRefundId(),
                        refund.getRefundId()
                );
            }

            rows.append("""
                    <tr>
                        <td>%d</td>
                        <td>%d</td>
                        <td>%s</td>
                        <td>%s</td>
                    </tr>
                    """.formatted(
                    refund.getRefundId(),
                    refund.getReservationId(),
                    refundStatusLabel(refund.getStatus()),
                    action
            ));
        }

        return rows.toString();
    }

    /**
     * 관리자 예약 목록 HTML을 생성합니다.
     */
    private String buildReservationRows(
            List<Reservation> reservations
    ) {
        if (reservations.isEmpty()) {
            return """
                <tr>
                    <td colspan="7">예약 내역이 없습니다.</td>
                </tr>
                """;
        }

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        StringBuilder rows = new StringBuilder();

        for (Reservation reservation : reservations) {
            rows.append("""
                <tr>
                    <td>%d</td>
                    <td>%d</td>
                    <td>%d</td>
                    <td>%s</td>
                    <td>%s</td>
                    <td>%s원</td>
                    <td>%s</td>
                </tr>
                """.formatted(
                    reservation.getReservationId(),
                    reservation.getUserId(),
                    reservation.getRoomId(),
                    reservation.getStartTime().format(formatter),
                    reservation.getEndTime().format(formatter),
                    String.format(
                            Locale.KOREA,
                            "%,.0f",
                            reservation.getTotalPrice()
                    ),
                    reservationStatusLabel(reservation.getStatus())
            ));
        }

        return rows.toString();
    }

    /**
     * 예약 상태를 한글 배지 HTML로 변환합니다.
     */
    private String reservationStatusLabel(ReservationStatus status) {
        return switch (status) {
            case CONFIRMED ->
                    """
                    <span class="status status-approved">예약 확정</span>
                    """;
            case CANCELLED ->
                    """
                    <span class="status status-rejected">예약 취소</span>
                    """;
        };
    }

    private RefundStatus parseRefundStatus(
            String value
    ) {
        try {
            return RefundStatus.valueOf(
                    value.toUpperCase()
            );
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "올바른 환불 상태가 아닙니다."
            );
        }
    }

    private long parseId(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    fieldName + "를 입력해 주세요."
            );
        }

        try {
            long id = Long.parseLong(value);

            if (id <= 0) {
                throw new BusinessException(
                        "올바른 " + fieldName + "를 입력해 주세요."
                );
            }

            return id;

        } catch (NumberFormatException e) {
            throw new BusinessException(
                    "올바른 " + fieldName + "를 입력해 주세요."
            );
        }
    }

    private String findSessionId(
            HttpExchange exchange
    ) {
        return HttpRequestUtil.findCookie(
                exchange,
                SESSION_COOKIE_NAME
        );
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }


    /**
     * 환불 상태를 한글 상태 배지 HTML로 변환합니다.
     */
    private String refundStatusLabel(RefundStatus status) {
        return switch (status) {
            case REQUESTED ->
                    "<span class=\"status status-requested\">처리 대기</span>";
            case APPROVED ->
                    "<span class=\"status status-approved\">승인</span>";
            case REJECTED ->
                    "<span class=\"status status-rejected\">거절</span>";
        };
    }

}