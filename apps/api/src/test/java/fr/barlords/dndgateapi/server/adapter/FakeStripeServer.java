package fr.barlords.dndgateapi.server.adapter;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** A local stand-in for the Stripe API: answers canned JSON by method and path, and records what it receives. */
public final class FakeStripeServer implements AutoCloseable {

    public record Call(String method, String path, String query, String body) {

        /** The form-encoded body (or query string) as decoded key/value pairs. */
        public Map<String, String> form() {
            Map<String, String> form = new LinkedHashMap<>();
            String raw = body.isEmpty() ? query : body;
            if (raw == null || raw.isEmpty()) {
                return form;
            }
            for (String pair : raw.split("&")) {
                int equals = pair.indexOf('=');
                String key = URLDecoder.decode(equals < 0 ? pair : pair.substring(0, equals), StandardCharsets.UTF_8);
                String value = equals < 0 ? "" : URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
                form.put(key, value);
            }
            return form;
        }
    }

    private record Stub(int status, String json) {
    }

    private final HttpServer server;
    private final Map<String, Stub> stubs = new ConcurrentHashMap<>();
    private final List<Call> calls = new CopyOnWriteArrayList<>();

    public FakeStripeServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            calls.add(new Call(method, path, exchange.getRequestURI().getRawQuery(), body));
            Stub stub = stubs.getOrDefault(method + " " + path,
                    new Stub(404, "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"No such route\"}}"));
            byte[] bytes = stub.json().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(stub.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    public FakeStripeServer answer(String method, String path, String json) {
        stubs.put(method + " " + path, new Stub(200, json));
        return this;
    }

    public FakeStripeServer fail(String method, String path, int status, String json) {
        stubs.put(method + " " + path, new Stub(status, json));
        return this;
    }

    public String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    public List<Call> calls() {
        return new ArrayList<>(calls);
    }

    /** The only call received on this method and path. */
    public Call only(String method, String path) {
        List<Call> matching = calls.stream()
                .filter(call -> call.method().equals(method) && call.path().equals(path)).toList();
        if (matching.size() != 1) {
            throw new AssertionError("Expected one " + method + " " + path + " but got " + matching.size());
        }
        return matching.get(0);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
