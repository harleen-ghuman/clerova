package com.clerova.billing;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StripeClientTest {

    private HttpServer server;
    private final List<String> requests = new ArrayList<>();
    private StripeClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath()
                    + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery())
                    + " " + exchange.getRequestHeaders().getFirst("Authorization") + " " + body);

            byte[] response = "{\"id\":\"price_1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        BillingProperties properties = new BillingProperties(
                "sk_test_example", "", "http://127.0.0.1:" + server.getAddress().getPort(),
                "https://app.example.com", 14, 300, 1500, 20);
        client = new StripeClient(properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsLookupKeyQueriesAndFormBodies() {
        client.get("/v1/prices?active=true&lookup_keys%5B%5D=starter_monthly");

        Map<String, String> form = new LinkedHashMap<>();
        form.put("line_items[0][price]", "price_1");
        form.put("success_url", "https://app.example.com/?checkout=success&session_id={CHECKOUT_SESSION_ID}");
        client.post("/v1/checkout/sessions", form);

        assertThat(requests.get(0))
                .isEqualTo("GET /v1/prices?active=true&lookup_keys%5B%5D=starter_monthly Bearer sk_test_example ");
        assertThat(requests.get(1)).startsWith("POST /v1/checkout/sessions Bearer sk_test_example ")
                .contains("line_items%5B0%5D%5Bprice%5D=price_1")
                .contains("session_id%3D%7BCHECKOUT_SESSION_ID%7D");
    }
}
