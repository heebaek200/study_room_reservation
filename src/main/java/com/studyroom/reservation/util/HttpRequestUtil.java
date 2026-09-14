package com.studyroom.reservation.util;

import com.sun.net.httpserver.HttpExchange;

import java.util.List;

public final class HttpRequestUtil {

    private HttpRequestUtil() {
    }

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
                String[] pair = cookie.trim().split("=", 2);

                if (pair.length == 2
                        && cookieName.equals(pair[0])) {
                    return pair[1];
                }
            }
        }

        return null;
    }
}