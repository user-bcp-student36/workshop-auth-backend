package com.bcp.workshop.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Startup
@ApplicationScoped
public class TokenService {
    public static final long LIFETIME_SECONDS = 3600;
    private static final int MIN_KEY_BYTES = 32;
    private static final String ROLE_CLAIM = "role";
    private final byte[] key;
    private final Clock clock;

    @Inject
    public TokenService(@ConfigProperty(name = "auth.token.secret") String encodedKey) {
        this(encodedKey, Clock.systemUTC());
    }

    TokenService(String encodedKey, Clock clock) {
        try {
            key = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Token signing key must be Base64 encoded");
        }
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException("Token signing key must contain at least 256 bits");
        }
        this.clock = clock;
    }

    public String create(String username, String role) {
        Instant issuedAt = clock.instant();
        var claims = new JWTClaimsSet.Builder().subject(username).claim(ROLE_CLAIM, role)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plusSeconds(LIFETIME_SECONDS))).build();
        var token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT).build(), claims);
        try {
            token.sign(new MACSigner(key));
            return token.serialize();
        } catch (JOSEException error) {
            throw new IllegalStateException("Unable to sign authentication token", error);
        }
    }

    public JWTClaimsSet verify(String encoded) {
        try {
            SignedJWT token = SignedJWT.parse(encoded);
            if (!JWSAlgorithm.HS256.equals(token.getHeader().getAlgorithm())
                    || !token.verify(new MACVerifier(key))) {
                throw new IllegalArgumentException("Invalid token signature");
            }
            JWTClaimsSet claims = token.getJWTClaimsSet();
            validateClaims(claims);
            return claims;
        } catch (ParseException | JOSEException error) {
            throw new IllegalArgumentException("Invalid authentication token");
        }
    }

    private void validateClaims(JWTClaimsSet claims) throws ParseException {
        String role = claims.getStringClaim(ROLE_CLAIM);
        if (claims.getSubject() == null || claims.getSubject().isBlank()
                || role == null || role.isBlank()
                || claims.getIssueTime() == null || claims.getExpirationTime() == null) {
            throw new IllegalArgumentException("Required token claims are missing");
        }
        Instant now = clock.instant();
        if (!now.isBefore(claims.getExpirationTime().toInstant())
                || claims.getIssueTime().toInstant().isAfter(now)
                || !claims.getExpirationTime().after(claims.getIssueTime())) {
            throw new IllegalArgumentException("Token is expired or not yet valid");
        }
    }
}
