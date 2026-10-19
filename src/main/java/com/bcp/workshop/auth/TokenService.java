package com.bcp.workshop.auth;

import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.util.KeyUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.Config;

import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.Clock;
import java.util.Set;

@Singleton
public final class TokenService {
    private static final String KEY_LOCATION_PROPERTY = "auth.token.key-location";
    private static final String ISSUER_PROPERTY = "auth.token.issuer";
    private static final String AUDIENCE_PROPERTY = "auth.token.audience";
    private static final String INVALID_CONFIGURATION = "JWT signing configuration invalid";
    private static final int MINIMUM_RSA_BITS = 2048;
    private final PrivateKey signingKey;
    private final String issuer;
    private final String audience;
    private final Clock clock;

    @Inject
    public TokenService(Config config) {
        this(config, Clock.systemUTC());
    }

    private TokenService(Config config, Clock clock) {
        this(readKey(config.getOptionalValue(KEY_LOCATION_PROPERTY, String.class).orElse("")),
                config.getOptionalValue(ISSUER_PROPERTY, String.class).orElse(""),
                config.getOptionalValue(AUDIENCE_PROPERTY, String.class).orElse(""), clock);
    }

    public TokenService(PrivateKey signingKey, String issuer, String audience, Clock clock) {
        if (!(signingKey instanceof RSAPrivateKey rsa) || rsa.getModulus().bitLength() < MINIMUM_RSA_BITS
                || issuer == null || issuer.isBlank() || audience == null || audience.isBlank() || clock == null) {
            throw new IllegalStateException(INVALID_CONFIGURATION);
        }
        this.signingKey = signingKey;
        this.issuer = issuer;
        this.audience = audience;
        this.clock = clock;
    }

    public static TokenService fromConfiguration(Config config, Clock clock) {
        return new TokenService(config, clock);
    }

    private static PrivateKey readKey(String location) {
        if (location.isBlank()) {
            throw new IllegalStateException(INVALID_CONFIGURATION);
        }
        try {
            return KeyUtils.decodePrivateKey(KeyUtils.readKeyContent(location));
        } catch (Exception error) {
            throw new IllegalStateException(INVALID_CONFIGURATION);
        }
    }

    public String create(String username, String role) {
        long issuedAt = clock.instant().getEpochSecond();
        return Jwt.subject(username).groups(Set.of(role)).issuer(issuer).audience(audience)
                .issuedAt(issuedAt).expiresAt(issuedAt + TokenPolicy.LIFETIME_SECONDS)
                .jws().algorithm(SignatureAlgorithm.RS256).sign(signingKey);
    }
}