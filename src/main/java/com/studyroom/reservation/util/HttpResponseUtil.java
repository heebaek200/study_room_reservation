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
    public static void sendTemplate(
            HttpExchange exchange,
            String fileName
    ) throws IOException {

        sendTemplateWithHtml(
                exchange,
                fileName,
                Map.of(),
                Map.of()
        );
    }

    /**
     * HTML 템플릿의 {{항목명}}을 전달받은 값으로 치환합니다.
     */
    public static void sendTemplate(
            HttpExchange exchange,
            String fileName,
            Map<String, String> values
    ) throws IOException {

        sendTemplateWithHtml(
                exchange,
                fileName,
                values,
                Map.of()
        );
    }

    /**
     * 일반 문자열과 HTML 조각을 구분하여 템플릿을 반환합니다.
     *
     * values는 HTML escape 후 치환하고,
     * htmlFragments는 서버에서 생성한 HTML 조각으로 간주하여
     * escape하지 않고 치환합니다.
     *
     * @param exchange 현재 HTTP 요청과 응답
     * @param fileName HTML 템플릿 파일명
     * @param values escape가 필요한 일반 문자열
     * @param htmlFragments HTML 태그를 포함할 수 있는 문자열
     * @throws IOException 템플릿 또는 응답 처리 중 오류가 발생한 경우
     */
    public static void sendTemplateWithHtml(
            HttpExchange exchange,
            String fileName,
            Map<String, String> values,
            Map<String, String> htmlFragments
    ) throws IOException {

        String resourcePath = "/templates/" + fileName;

        try (InputStream inputStream =
                     HttpResponseUtil.class.getResourceAsStream(
                             resourcePath
                     )) {

            if (inputStream == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            String html = new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );

            // 일반 문자열은 HTML 특수문자를 변환한 뒤 치환합니다.
            for (Map.Entry<String, String> entry
                    : values.entrySet()) {

                String placeholder =
                        "{{" + entry.getKey() + "}}";

                html = html.replace(
                        placeholder,
                        escapeHtml(entry.getValue())
                );
            }

            // 서버에서 생성한 HTML 조각은 escape하지 않고 치환합니다.
            for (Map.Entry<String, String> entry
                    : htmlFragments.entrySet()) {

                String placeholder =
                        "{{" + entry.getKey() + "}}";

                html = html.replace(
                        placeholder,
                        entry.getValue() == null
                                ? ""
                                : entry.getValue()
                );
            }

            byte[] responseBody =
                    html.getBytes(StandardCharsets.UTF_8);

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