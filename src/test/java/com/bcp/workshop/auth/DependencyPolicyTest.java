package com.bcp.workshop.auth;

import io.quarkus.runtime.Quarkus;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Properties;

class DependencyPolicyTest {
    @Test
    void resolvesApprovedCommonsTextSecurityUpgrade() {
        assertEquals("1.15.0", StringEscapeUtils.class.getPackage().getImplementationVersion());
    }

    @Test
    void resolvesApprovedQuarkusLtsSecurityUpgrade() throws Exception {
        var metadata = new Properties();
        try (var stream = Quarkus.class.getResourceAsStream(
                "/META-INF/maven/io.quarkus/quarkus-core/pom.properties")) {
            assertNotNull(stream);
            metadata.load(stream);
        }
        assertEquals("3.27.6", metadata.getProperty("version"));
    }
}
