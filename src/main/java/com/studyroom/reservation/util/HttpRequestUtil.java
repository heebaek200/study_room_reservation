package com.studyroom.reservation.util;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 요청에서 공통으로 사용하는 값을 읽습니다.
 */
public final class HttpRequestUtil {

    private HttpRequestUtil() {
    }

    /**
     * 요청 쿠키에서 지정한 이름의 값을 찾습니다.
     */
    public static String findCookie(
            HttpExchange exchange,
            String cookieName
    ) {
        List<String> cookieHeaders =
                exchange.getRequestHeaders().get("Cookie");

        if (cookieHeaders == null) {
            return null;
        }

        for (String cookieHeader : cookieHeaders) {
            for (String cookie : cookieHeader.split(";")) {
                String[] pair =
                        cookie.trim().split("=", 2);

                if (pair.length == 2
                        && cookieName.equals(pair[0])) {
                    return pair[1];
                }
            }
        }

        return null;
    }

    /**
     * application/x-www-form-urlencoded 형식의
     * 요청 본문을 Map으로 변환합니다.
     */
    public static Map<String, String> parseForm(
            HttpExchange exchange
    ) throws IOException {
        String requestBody;

        try (InputStream inputStream =
                     exchange.getRequestBody()) {
            requestBody = new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }

        return parseUrlEncoded(requestBody);
    }

    /**
     * URL의 Query String을 Map으로 변환합니다.
     * <p>
     * 예: ?roomId=1&result=success
     */
    public static Map<String, String> parseQuery(
            HttpExchange exchange
    ) {
        String rawQuery =
                exchange.getRequestURI().getRawQuery();

        return parseUrlEncoded(rawQuery);
    }

    /**
     * key=value&key=value 형식의 문자열을 분석합니다.
     */
    private static Map<String, String> parseUrlEncoded(
            String value
    ) {
        Map<String, String> parameters =
                new HashMap<>();

        if (value == null || value.isBlank()) {
            return parameters;
        }

        for (String parameter : value.split("&")) {
            String[] pair = parameter.split("=", 2);

            String key = decode(pair[0]);
            String parameterValue = pair.length == 2
                    ? decode(pair[1])
                    : "";

            parameters.put(key, parameterValue);
        }

        return parameters;
    }

    /**
     * URL 인코딩된 문자열을 UTF-8로 변환합니다.
     */
    private static String decode(String value) {
        return URLDecoder.decode(
                value,
                StandardCharsets.UTF_8
        );
    }
}
