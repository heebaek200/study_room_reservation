package com.studyroom.reservation.handler;

import com.studyroom.reservation.dto.User;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.service.UserService;
import com.studyroom.reservation.util.HttpRequestUtil;
import com.studyroom.reservation.util.HttpResponseUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserHandler implements HttpHandler {

    private static final String INFO_PATH = "/my-info";
    private static final String WITHDRAW_PATH = "/my-info/withdraw";
    private static final String ADMIN_USERS_PATH = "/admin/users";

    private static final String SESSION_COOKIE_NAME = "SESSION_ID";

    private final UserService userService;

    public UserHandler(UserService userService) {
        this.userService = userService;
    }

    // 1. 요청이 오면 주소(path)+메서드(GET/POST) 조합을 보고 어떤 처리 메서드를 부를지 결정
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if (INFO_PATH.equals(path) && "GET".equalsIgnoreCase(method)) {
                handleMyInfo(exchange);
                return;
            }

            if (INFO_PATH.equals(path) && "POST".equalsIgnoreCase(method)) {
                handleUpdateName(exchange);
                return;
            }

            if (WITHDRAW_PATH.equals(path) && "POST".equalsIgnoreCase(method)) {
                handleWithdraw(exchange);
                return;
            }

            if (ADMIN_USERS_PATH.equals(path) && "GET".equalsIgnoreCase(method)) {
                handleAdminUsers(exchange);
                return;
            }

            sendMessage(exchange, 404, "요청한 페이지를 찾을 수 없습니다.");
        } catch (BusinessException e) {
            // 업무 규칙 위반(로그인 필요, 이름 빈값 등) → 400 안내 화면
            sendMessage(exchange, 400, e.getMessage());
        } catch (Exception e) {
            // DB 오류 등 예상 못 한 문제 → 500 안내 화면
            sendMessage(exchange, 500, "요청 처리 중 오류가 발생했습니다.");
        }
    }

    // 2. GET /my-info - 로그인한 회원 정보를 조회해서 my-info.html로 보여줌
    private void handleMyInfo(HttpExchange exchange) throws IOException, SQLException {
        String sessionId = HttpRequestUtil.findCookie(exchange, SESSION_COOKIE_NAME);

        User user;
        try {
            user = userService.getMyInfo(sessionId);
        } catch (BusinessException e) {
            // 로그인 안 된 상태로 접근하면 로그인 화면으로 보냄
            HttpResponseUtil.redirect(exchange, "/login");
            return;
        }

        // redirect로 넘어온 쿼리스트링(?updated=true)이 있으면 성공 안내 문구를 보여줌
        String query = exchange.getRequestURI().getQuery();
        String message = "updated=true".equals(query) ? "이름이 변경되었습니다." : null;

        sendMyInfoPage(exchange, user, message);
    }

    // my-info.html 템플릿에 이메일·이름 값과 안내 문구(message)를 채워서 응답
    // message가 없으면(null) 안내 문구 자리는 빈 문자열로 채워짐
    private void sendMyInfoPage(HttpExchange exchange, User user, String message) throws IOException {
        String header =
                HttpResponseUtil.loadFragment(
                        "app-header.html"
                );

        String navigation =
                HttpResponseUtil.loadFragment(
                        "nav-user.html"
                );

        String footer =
                HttpResponseUtil.loadFragment(
                        "app-footer.html"
                );

        Map<String, String> values = new HashMap<>();

        values.put("userName", user.getName());
        values.put("roleName", "일반 회원");
        values.put("roleClass", "");

        values.put("homeCurrent", "");
        values.put("roomsCurrent", "");
        values.put("myInfoCurrent", "current");
        values.put("myReservationsCurrent", "");
        values.put("myRefundsCurrent", "");

        values.put("email", user.getEmail());
        values.put("name", user.getName());
        values.put(
                "message",
                message == null
                        ? ""
                        : message
        );

        HttpResponseUtil.sendTemplateWithHtml(
                exchange,
                "my-info.html",
                values,
                Map.of(
                        "header", header,
                        "navigation", navigation,
                        "footer", footer
                )
        );
    }

    // 3. POST /my-info - 폼으로 제출한 새 이름으로 수정. 성공하면 /my-info로 redirect(PRG 패턴),
    //    실패하면(BusinessException) 에러 페이지 대신 /my-info 화면에 안내 문구를 같이 보여줌
    private void handleUpdateName(HttpExchange exchange) throws IOException, SQLException {
        // 쿠키에서 sessionId 추출 (누가 요청했는지)
        String sessionId = HttpRequestUtil.findCookie(exchange, SESSION_COOKIE_NAME);
        // 요청 body(폼 데이터)에서 새로 입력한 이름 추출
        String updateName = parseForm(exchange).get("name");

        try {
            userService.updateMyName(sessionId, updateName);
        } catch (BusinessException e) {
            User user = userService.getMyInfo(sessionId);
            sendMyInfoPage(exchange, user, e.getMessage());
            return;
        }

        HttpResponseUtil.redirect(exchange, INFO_PATH + "?updated=true");
    }

    // 4. POST /my-info/withdraw - 탈퇴 처리. 성공하면 세션 쿠키를 만료시키고 완료 안내 화면을 보여줌(닫기 버튼으로 로그인 이동),
    //    실패하면(확정 예약 존재 등) /my-info 화면에 안내 문구를 같이 보여줌
    private void handleWithdraw(HttpExchange exchange) throws SQLException, IOException {
        String sessionId = HttpRequestUtil.findCookie(exchange, SESSION_COOKIE_NAME);

        try {
            userService.withdraw(sessionId);
        } catch (BusinessException e) {
            User user = userService.getMyInfo(sessionId);
            sendMyInfoPage(exchange, user, e.getMessage());
            return;
        }

        // 서버 세션은 이미 지워졌으니, 브라우저의 SESSION_ID 쿠키도 즉시 만료시킴 (Max-Age=0)
        exchange.getResponseHeaders().add(
                "Set-Cookie",
                SESSION_COOKIE_NAME
                        + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0"
        );

        sendWithdrawSuccessPage(exchange);
    }

    // 탈퇴 성공 안내 화면. "닫기" 버튼을 누르면 로그인 화면으로 이동함 (JS 없이 form+button으로 처리)
    private void sendWithdrawSuccessPage(HttpExchange exchange) throws IOException {
        String html = """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>탈퇴 완료</title>
                    <link rel="stylesheet" href="/css/common.css">
                </head>
                <body>
                <main>
                    <h1>탈퇴 완료</h1>
                    <p>탈퇴가 완료되었습니다. 로그아웃되었습니다.</p>
                    <form action="/login" method="get">
                        <button type="submit">닫기</button>
                    </form>
                </main>
                </body>
                </html>
                """;

        byte[] responseBody = html.getBytes(StandardCharsets.UTF_8);

        try {
            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "text/html; charset=UTF-8"
            );
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
        } finally {
            exchange.close();
        }
    }

    // 5. GET /admin/users - 관리자만 볼 수 있는 전체 회원 목록.
    //    (관리자가 아니면 userService.getAllUsers() 자체에서 BusinessException이 던져져서
    //     handle()의 바깥 catch가 자동으로 400 안내 화면으로 처리해줌)
    private void handleAdminUsers(HttpExchange exchange) throws IOException, SQLException {
        String sessionId = HttpRequestUtil.findCookie(exchange, SESSION_COOKIE_NAME);

        User currentUser;
        try {
            currentUser = userService.getMyInfo(sessionId);
        } catch (BusinessException e) {
            // 로그인 안 된 상태로 접근하면 로그인 화면으로 보냄
            HttpResponseUtil.redirect(exchange, "/login");
            return;
        }

        List<User> users = userService.getAllUsers(sessionId);

        // 회원 수만큼 <tr> 한 줄씩 만들어서 이어붙임 (표의 반복되는 부분을 미리 문자열로 완성)
        StringBuilder rows = new StringBuilder();
        for (User user : users) {
            rows.append("""
          <tr><td>%d</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>
          """.formatted(
                    user.getUserId(),
                    escapeHtml(user.getEmail()),
                    escapeHtml(user.getName()),
                    user.getRole(),
                    user.getStatus()
            ));
        }

        sendUsersPage(exchange, currentUser, rows.toString());
    }

    // admin-users.html 템플릿의 <tbody> 자리에 완성된 회원 목록 행(rows)을 채워서 응답
    private void sendUsersPage(HttpExchange exchange, User user, String rows) throws IOException {
        String header =
                HttpResponseUtil.loadFragment(
                        "app-header.html"
                );

        String navigation =
                HttpResponseUtil.loadFragment(
                        "nav-admin.html"
                );

        String footer =
                HttpResponseUtil.loadFragment(
                        "app-footer.html"
                );

        Map<String, String> values = new HashMap<>();

        values.put("userName", user.getName());
        values.put("roleName", "관리자");
        values.put("roleClass", "admin");

        values.put("homeCurrent", "");
        values.put("roomsCurrent", "");
        values.put("adminRoomsCurrent", "");
        values.put("adminUsersCurrent", "current");
        values.put("adminReservationsCurrent", "");
        values.put("adminRefundsCurrent", "");

        HttpResponseUtil.sendTemplateWithHtml(
                exchange,
                "admin-users.html",
                values,
                Map.of(
                        "header", header,
                        "navigation", navigation,
                        "footer", footer,
                        "userRows", rows
                )
        );
    }

    // ---- 아래는 여러 메서드가 공통으로 쓰는 헬퍼 ----

    // 성공/실패와 무관하게 쓰는 간단한 안내 메시지 화면 (404, 400, 500 등에서 사용)
    private void sendMessage(
            HttpExchange exchange,
            int statusCode,
            String message
    ) throws IOException {
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
                    <p><a href="/my-info">내 정보</a></p>
                </body>
                </html>
                """.formatted(escapeHtml(message));

        byte[] responseBody = html.getBytes(StandardCharsets.UTF_8);

        try {
            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "text/html; charset=UTF-8"
            );
            exchange.sendResponseHeaders(statusCode, responseBody.length);
            exchange.getResponseBody().write(responseBody);
        } finally {
            exchange.close();
        }
    }

    // HTML 특수문자(<, >, ", ' 등)를 이스케이프해서 XSS(악성 스크립트 삽입)를 방지
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

    // POST 요청의 body(application/x-www-form-urlencoded)를 key=value Map으로 파싱
    private Map<String, String> parseForm(HttpExchange exchange)
            throws IOException {
        String body;

        try (InputStream inputStream = exchange.getRequestBody()) {
            body = new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }

        Map<String, String> form = new HashMap<>();

        if (body.isBlank()) {
            return form;
        }

        for (String parameter : body.split("&")) {
            String[] pair = parameter.split("=", 2);

            String key = decode(pair[0]);
            String value = pair.length == 2
                    ? decode(pair[1])
                    : "";

            form.put(key, value);
        }

        return form;
    }

    // URL 인코딩된 폼 값을 원래 글자(한글 등)로 복원
    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
