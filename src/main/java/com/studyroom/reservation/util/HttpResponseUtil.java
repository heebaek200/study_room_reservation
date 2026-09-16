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

        String html;

        try {
            html = loadTemplateResource(fileName);
        } catch (IOException e) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        // 공통 컴포넌트 등의 HTML 조각은 escape하지 않고 삽입합니다.
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

        try {
            exchange.getResponseBody().write(responseBody);
        } finally {
            exchange.close();
        }
    }

    /**
     * components 디렉터리의 공통 HTML fragment를 읽어 반환합니다.
     * 헤더, 내비게이션, 푸터처럼 여러 화면에서 재사용하는
     * HTML 조각을 페이지 템플릿에 삽입할 때 사용합니다.
     *
     * @param fileName fragment HTML 파일명
     * @return fragment HTML 문자열
     * @throws IOException fragment를 읽지 못한 경우
     */
    public static String loadFragment(String fileName)
            throws IOException {

        return loadTemplateResource(
                "components/" + fileName
        );
    }

    /**
     * templates 디렉터리 아래의 HTML 리소스를 UTF-8 문자열로 읽습니다.
     * 일반 페이지 템플릿과 공통 fragment가 동일한 파일 읽기 로직을
     * 사용할 수 있도록 내부 공통 기능으로 제공합니다.
     *
     * @param resourcePath templates 기준 상대 경로
     * @return 읽어 온 HTML 문자열
     * @throws IOException HTML 리소스를 찾지 못하거나 읽지 못한 경우
     */
    private static String loadTemplateResource(
            String resourcePath
    ) throws IOException {

        String fullPath =
                "/templates/" + resourcePath;

        try (InputStream inputStream =
                     HttpResponseUtil.class.getResourceAsStream(
                             fullPath
                     )) {

            if (inputStream == null) {
                throw new IOException(
                        "템플릿 파일을 찾을 수 없습니다: "
                                + fullPath
                );
            }

            // 리소스 전체를 UTF-8 문자열로 변환
            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
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