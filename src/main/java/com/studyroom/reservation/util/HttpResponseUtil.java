package com.studyroom.reservation.util;

import com.sun.net.httpserver.HttpExchange;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.io.IOException;
import java.io.InputStream;

/**
 * HTTP 응답에서 공통으로 사용하는 기능을 제공합니다.
 */
public final class HttpResponseUtil {

    private HttpResponseUtil() {
    }

    /**
     * 치환값 없이 HTML 템플릿을 반환합니다.
     */
    public static void sendTemplate(HttpExchange exchange, String fileName) throws IOException {
        sendTemplate(exchange, fileName, Map.of());
    }

    /**
     * HTML 템플릿의 {{항목명}}을 전달받은 값으로 치환합니다.
     */
    public static void sendTemplate(HttpExchange exchange, String fileName, Map<String, String> values) throws IOException {
        String resourcePath = "/templates/" + fileName;

        try (InputStream inputStream = HttpResponseUtil.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            String html = new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );

            for (Map.Entry<String, String> entry
                    : values.entrySet()) {
                String placeholder =
                        "{{" + entry.getKey() + "}}";

                html = html.replace(
                        placeholder,
                        escapeHtml(entry.getValue())
                );
            }

            byte[] responseBody = html.getBytes(
                    StandardCharsets.UTF_8
            );

            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "text/html; charset=UTF-8"
            );
            exchange.sendResponseHeaders(
                    200,
                    responseBody.length
            );
            exchange.getResponseBody().write(responseBody);
        } finally {
            exchange.close();
        }
    }

    /**
     * 동적으로 출력하는 문자열의 HTML 특수문자를 변환합니다.
     */
    private static String escapeHtml(String value) {
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
     * 브라우저를 지정한 경로로 이동시킵니다.
     */
    public static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set(
                "Location",
                location
        );
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }
}