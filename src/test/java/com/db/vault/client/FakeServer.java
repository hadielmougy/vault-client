package com.db.vault.client;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/** A scripted HTTP server for client tests. Records every request it receives. */
final class FakeServer implements AutoCloseable {

    record Request(String method, String path, String query, String apiKey, String contentType, String body) {}

    record Response(int status, String body) {}

    final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
    private final List<Response> script = Collections.synchronizedList(new ArrayList<>());
    private volatile Function<Request, Response> responder;
    private final HttpServer server;

    FakeServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(), exchange.getRequestHeaders().getFirst("X-Api-Key"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requests.add(request);
            Response response;
            if (responder != null) {
                response = responder.apply(request);
            } else if (!script.isEmpty()) {
                response = script.remove(0);
            } else {
                response = new Response(500, "{\"code\":\"NO_SCRIPT\",\"message\":\"no response scripted\"}");
            }
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    FakeServer reply(int status, String body) {
        script.add(new Response(status, body));
        return this;
    }

    void respondWith(Function<Request, Response> fn) {
        this.responder = fn;
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
