package com.studyroom.reservation.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;

/**
 * classpath의 CSS 파일을 HTTP 응답으로 제공합니다.
 */
public final class StaticFileHandler implements HttpHandler {

    private static final String URL_PREFIX = "/css/";
    private static final String RESOURCE_PREFIX =
            "/static/css/";

    @Override
    public void handle(HttpExchange exchange)
            throws IOException {

        if (!"GET".equalsIgnoreCase(
                exchange.getRequestMethod()
        )) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String path = exchange.getRequestURI().getPath();

        if (!path.startsWith(URL_PREFIX)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        String fileName =
                path.substring(URL_PREFIX.length());

        if (fileName.isBlank()
                || !fileName.endsWith(".css")
                || fileName.contains("..")
                || fileName.contains("/")
                || fileName.contains("\\")) {

            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        String resourcePath =
                RESOURCE_PREFIX + fileName;

        try (InputStream inputStream =
                     StaticFileHandler.class
                             .getResourceAsStream(
                                     resourcePath
                             )) {

            if (inputStream == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            byte[] responseBody =
                    inputStream.readAllBytes();

            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "text/css; charset=UTF-8"
            );

            exchange.sendResponseHeaders(
                    200,
                    responseBody.length
            );

            exchange.getResponseBody().write(
                    responseBody
            );
        } finally {
            exchange.close();
        }
    }
}