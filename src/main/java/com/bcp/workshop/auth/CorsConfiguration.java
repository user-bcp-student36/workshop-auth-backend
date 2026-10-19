package com.bcp.workshop.auth;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;

@Startup
@ApplicationScoped
public class CorsConfiguration {
    private static final int MAX_PORT = 65535;

    @Inject
    public CorsConfiguration(@ConfigProperty(name = "auth.cors.origins") String origins) {
        for (String origin : origins.split(",", -1)) {
            validateOrigin(origin.trim());
        }
    }

    private void validateOrigin(String origin) {
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid CORS origin syntax");
        }
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || !uri.getRawPath().isEmpty() || uri.getQuery() != null
                || uri.getFragment() != null || uri.getPort() > MAX_PORT || uri.getPort() == 0) {
            throw new IllegalArgumentException("CORS configuration requires explicit HTTP(S) origins");
        }
    }
}
