package com.clerova.trucking;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the FMCSA lookup against a local stand-in for the QCMobile API. */
class FmcsaClientTest {

    private HttpServer server;
    private final List<String> requested = new ArrayList<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requested.add(exchange.getRequestURI().toString());
            String body;
            int status = 200;

            if (path.equals("/carriers/docket-number/845210")) {
                body = "{\"content\":[{\"carrier\":{\"legalName\":\"CRESTLINE FREIGHT LLC\",\"dbaName\":null,\"dotNumber\":1234567}}]}";
            } else if (path.equals("/carriers/1234567/authority")) {
                body = "{\"content\":[{\"carrierAuthority\":{\"authorizedForBroker\":\"Y\",\"brokerAuthorityStatus\":\"A\"}}]}";
            } else if (path.equals("/carriers/docket-number/555")) {
                body = "{\"content\":[{\"carrier\":{\"legalName\":\"ROAD HAULERS INC\",\"dotNumber\":777}}]}";
            } else if (path.equals("/carriers/777/authority")) {
                body = "{\"content\":[{\"carrierAuthority\":{\"authorizedForBroker\":\"N\",\"brokerAuthorityStatus\":\"N\"}}]}";
            } else if (path.equals("/carriers/docket-number/500")) {
                status = 500;
                body = "{}";
            } else {
                body = "{\"content\":[]}";
            }

            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private FmcsaClient client(String key) {
        return new FmcsaClient(key, "http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @Test
    void activeBrokerAuthority() {
        BrokerCheck check = client("test-key").check("845210").orElseThrow();
        assertThat(check.status()).isEqualTo(FmcsaStatus.ACTIVE);
        assertThat(check.legalName()).isEqualTo("CRESTLINE FREIGHT LLC");
        assertThat(requested.getFirst()).contains("webKey=test-key");
    }

    @Test
    void aCarrierWithoutBrokerAuthorityAndAnUnknownNumber() {
        assertThat(client("k").check("555").orElseThrow().status()).isEqualTo(FmcsaStatus.NOT_AUTHORIZED);
        assertThat(client("k").check("999").orElseThrow().status()).isEqualTo(FmcsaStatus.NOT_FOUND);
    }

    @Test
    void noKeyOrAFailedLookupMeansNoCheck() {
        assertThat(client("").check("845210")).isEmpty();
        assertThat(client("").isEnabled()).isFalse();
        assertThat(client("k").check("500")).isEmpty();
        assertThat(client("k").check("12abc")).isEmpty();
        assertThat(requested).noneMatch(uri -> uri.contains("12abc"));
    }
}
