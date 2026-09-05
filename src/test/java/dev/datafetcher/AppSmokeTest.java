package dev.datafetcher;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@io.quarkus.test.junit.QuarkusTest
class AppSmokeTest {

    @Test
    void quarkusHttpPortResolves() {
        String raw = ConfigProvider.getConfig().getValue("quarkus.http.port", String.class);
        assertTrue(raw != null && raw.matches("\\d+"), "quarkus.http.port must resolve to a numeric port");
    }
}