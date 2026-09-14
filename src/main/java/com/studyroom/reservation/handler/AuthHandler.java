package com.studyroom.reservation.handler;

import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.service.AuthService;
import com.studyroom.reservation.session.LoginSession;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 회원가입과 로그인 HTTP 요청을 처리합니다.
 * GET 요청에는 HTML 파일을 반환하고 POST 요청에는 AuthService를 호출합니다.
 * 데이터베이스 작업과 인증 판단은 직접 처리하지 않고 Service에 위임합니다.
 */
public final class AuthHandler implements HttpHandler {

    private static final String LOGIN_PATH = "/login";
    private static final String SIGNUP_PATH = "/signup";
    private static final String LOGOUT_PATH = "/logout";

    private static final String SESSION_COOKIE_NAME = "SESSION_ID";

    private final AuthService authService;

    /**
     * 요청 처리에 사용할 인증 서비스를 전달받습니다.
     * AuthService 인터페이스에 의존하므로 JDBC와 메모리 구현체를 모두 사용할 수 있습니다.
     * 실제 서버에서는 JdbcAuthService를 전달합니다.
     *
     * @param authService 사용할 인증 서비스
     */
    public AuthHandler(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 요청 경로와 HTTP 메서드에 따라 회원가입 또는 로그인 처리를 실행합니다.
     * 등록되지 않은 요청은 404 응답으로 처리합니다.
     * 업무 예외가 발생하면 비밀번호를 노출하지 않고 오류 화면을 반환합니다.
     *
     * @param exchange 현재 HTTP 요청과 응답
     * @throws IOException 요청 본문이나 응답을 처리하지 못한 경우
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            // 로그인 화면 조회와 로그인 요청을 구분하여 처리합니다.
            if (LOGIN_PATH.equals(path) && "GET".equalsIgnoreCase(method)) {
                sendTemplate(exchange, "login.html");
                return;
            }

            if (LOGIN_PATH.equals(path) && "POST".equalsIgnoreCase(method)) {
                handleLogin(exchange);
                return;
            }

            // 회원가입 화면 조회와 회원가입 요청을 구분하여 처리합니다.
            if (SIGNUP_PATH.equals(path) && "GET".equalsIgnoreCase(method)) {
                sendTemplate(exchange, "signup.html");
                return;
            }

            if (SIGNUP_PATH.equals(path) && "POST".equalsIgnoreCase(method)) {
                handleSignUp(exchange);
                return;
            }

            // 로그아웃은 세션 상태를 변경하므로 POST 요청으로 처리합니다.
            if (LOGOUT_PATH.equals(path)
                    && "POST".equalsIgnoreCase(method)) {
                handleLogout(exchange);
                return;
            }

            sendMessage(exchange, 404, "요청한 페이지를 찾을 수 없습니다.");
        } catch (BusinessException e) {
            // BusinessException 메시지에는 평문 비밀번호가 포함되지 않아야 합니다.
            sendMessage(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendMessage(exchange, 500, "요청 처리 중 오류가 발생했습니다.");
        }
    }

    /**
     * 회원가입 Form 데이터를 읽고 AuthService의 회원가입 기능을 호출합니다.
     * 비밀번호 확인값은 HTTP 요청 처리 단계에서 비교합니다.
     * 회원가입이 끝나면 로그인 화면으로 이동합니다.
     *
     * @param exchange 현재 HTTP 요청과 응답
     * @throws IOException 요청 본문이나 응답을 처리하지 못한 경우
     */
    private void handleSignUp(HttpExchange exchange) throws IOException {
        Map<String, String> form = parseForm(exchange);

        String email = form.get("email");
        String name = form.get("name");
        String password = form.get("password");
        String passwordConfirm = form.get("passwordConfirm");

        // AuthService 인터페이스에 없는 비밀번호 확인은 Handler에서 처리합니다.
        if (!Objects.equals(password, passwordConfirm)) {
            throw new BusinessException("비밀번호 확인이 일치하지 않습니다.");
        }

        authService.signUp(email, password, name);

        // POST 결과를 새로고침했을 때 중복 제출되지 않도록 Redirect합니다.
        redirect(exchange, LOGIN_PATH);
    }

    /**
     * 로그인 Form 데이터를 읽고 AuthService의 로그인 기능을 호출합니다.
     * 로그인에 성공하면 발급받은 세션 ID를 HttpOnly 쿠키로 전달합니다.
     * 성공 화면에서는 POST 방식으로 로그아웃을 요청할 수 있습니다.
     *
     * @param exchange 현재 HTTP 요청과 응답
     * @throws IOException 요청 본문이나 응답을 처리하지 못한 경우
     */
    private void handleLogin(HttpExchange exchange) throws IOException {
        Map<String, String> form = parseForm(exchange);

        String email = form.get("email");
        String password = form.get("password");

        // 실제 회원 조회와 비밀번호 검사는 AuthService에서 수행합니다.
        LoginSession session = authService.login(email, password);

        exchange.getResponseHeaders().add(
                "Set-Cookie",
                SESSION_COOKIE_NAME + "=" + session.getSessionId()
                        + "; Path=/; HttpOnly; SameSite=Lax"
        );

        redirect(exchange, "/rooms");
    }

    /**
     * 요청 쿠키에서 세션 ID를 찾아 로그인 세션을 제거합니다.
     * 서버 세션을 제거한 뒤 브라우저의 SESSION_ID 쿠키도 만료시킵니다.
     * 처리가 끝나면 로그인 화면으로 이동합니다.
     *
     * @param exchange 현재 HTTP 요청과 응답
     * @throws IOException 응답을 전송하지 못한 경우
     */
    private void handleLogout(HttpExchange exchange) throws IOException {
        String sessionId = findCookie(
                exchange,
                SESSION_COOKIE_NAME
        );

        // 세션 ID가 없거나 이미 제거된 경우에도 로그아웃은 정상 종료합니다.
        authService.logout(sessionId);

        exchange.getResponseHeaders().add(
                "Set-Cookie",
                SESSION_COOKIE_NAME
                        + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0"
        );

        redirect(exchange, LOGIN_PATH);
    }

    /**
     * HTTP 요청의 Cookie 헤더에서 지정한 이름의 값을 찾습니다.
     * 여러 Cookie 헤더와 세미콜론으로 구분된 쿠키를 모두 검사합니다.
     * 요청한 쿠키가 없으면 null을 반환합니다.
     *
     * @param exchange 현재 HTTP 요청과 응답
     * @param cookieName 찾을 쿠키 이름
     * @return 쿠키값 또는 null
     */
    private String findCookie(
            HttpExchange exchange,
            String cookieName
    ) {
        List<String> cookieHeaders =
                exchange.getRequestHeaders().get("Cookie");

        if (cookieHeaders == null) {
            return null;
        }

        // 하나의 Cookie 헤더에는 여러 쿠키가 세미콜론으로 구분될 수 있습니다.
        for (String cookieHeader : cookieHeaders) {
            for (String cookie : cookieHeader.split(";")) {
                String[] pair = cookie.trim().split("=", 2);

                if (pair.length == 2
                        && cookieName.equals(pair[0])) {
                    return pair[1];
                }
            }
        }

        return null;
    }

    /**
     * application/x-www-form-urlencoded 형식의 요청 본문을 Map으로 변환합니다.
     * 키와 값은 UTF-8 기준으로 URL 디코딩합니다.
     * 같은 이름의 값이 여러 번 전달되면 마지막 값을 사용합니다.
     *
     * @param exchange 현재 HTTP 요청과 응답
     * @return 입력 항목의 이름과 값을 담은 Map
     * @throws IOException 요청 본문을 읽지 못한 경우
     */
    private Map<String, String> parseForm(HttpExchange exchange)
            throws IOException {
        String body;

        // 요청 본문 전체를 UTF-8 문자열로 변환합니다.
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

        // 각 key=value 항목을 분리하여 URL 디코딩합니다.
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

    /**
     * URL 인코딩된 Form 값을 UTF-8 문자열로 변환합니다.
     * 공백과 한글 등 브라우저가 인코딩한 문자를 원래 값으로 복구합니다.
     * 입력값 자체는 로그나 콘솔에 출력하지 않습니다.
     *
     * @param value URL 인코딩된 문자열
     * @return URL 디코딩된 문자열
     */
    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * resources/templates 아래의 HTML 파일을 읽어 응답합니다.
     * 클래스패스에서 파일을 찾지 못하면 404 오류를 반환합니다.
     * HTML 문서는 UTF-8 형식으로 전송합니다.
     *
     * @param exchange 현재 HTTP 요청과 응답
     * @param fileName 전송할 HTML 파일명
     * @throws IOException 파일이나 응답을 처리하지 못한 경우
     */
    private void sendTemplate(
            HttpExchange exchange,
            String fileName
    ) throws IOException {
        String resourcePath = "/templates/" + fileName;

        // 빌드 결과에 포함된 클래스패스 리소스를 읽습니다.
        try (InputStream inputStream =
                     AuthHandler.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                sendMessage(exchange, 404, "화면 파일을 찾을 수 없습니다.");
                return;
            }

            byte[] responseBody = inputStream.readAllBytes();
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

    /**
     * 처리 결과를 간단한 HTML 문서로 만들어 응답합니다.
     * 메시지는 HTML 특수문자를 변환하여 그대로 태그로 실행되지 않게 합니다.
     * 응답 본문은 UTF-8 형식으로 전송합니다.
     *
     * @param exchange 현재 HTTP 요청과 응답
     * @param statusCode HTTP 상태 코드
     * @param message 사용자에게 표시할 메시지
     * @throws IOException 응답을 전송하지 못한 경우
     */
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
                    <p><a href="/login">로그인</a></p>
                    <p><a href="/signup">회원가입</a></p>
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

    /**
     * 브라우저에 다른 주소로 이동하라는 응답을 반환합니다.
     * POST 요청 이후 새로고침으로 같은 요청이 반복되는 것을 방지합니다.
     * 응답 본문 없이 303 See Other 상태를 사용합니다.
     *
     * @param exchange 현재 HTTP 요청과 응답
     * @param location 이동할 경로
     * @throws IOException 응답을 전송하지 못한 경우
     */
    private void redirect(
            HttpExchange exchange,
            String location
    ) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    /**
     * 메시지에 포함된 HTML 특수문자를 안전한 문자열로 변환합니다.
     * 오류 메시지가 HTML 태그나 스크립트로 해석되는 것을 방지합니다.
     * null 메시지는 빈 문자열로 처리합니다.
     *
     * @param value 변환할 문자열
     * @return HTML 특수문자가 변환된 문자열
     */
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
