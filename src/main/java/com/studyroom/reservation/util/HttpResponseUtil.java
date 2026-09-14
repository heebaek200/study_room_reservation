package com.studyroom.reservation.util;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;

/**
 * HTTP 응답에서 공통으로 사용하는 기능을 제공합니다.
 */
public final class HttpResponseUtil {

    private HttpResponseUtil() {
    }

    /**
     * resources/templates 디렉터리의 HTML 파일을 반환합니다.
     *
     * @param exchange 현재 HTTP 요청과 응답
     * @param fileName 반환할 HTML 파일명
     * @throws IOException 파일 또는 응답을 처리하지 못한 경우
     */
    public static void sendTemplate(HttpExchange exchange, String fileName) throws IOException {
        String resourcePath = "/templates/" + fileName;

        try (InputStream inputStream =
                     HttpResponseUtil.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            byte[] responseBody = inputStream.readAllBytes();

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