package org.ddamme.security.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    @Value("${security.jwt.secret}")
    private String jwtSecretBase64; // base64-encoded

    @Value("${security.jwt.expiration-ms:86400000}")
    private long tokenExpirationMilliseconds;

    @Value("${security.jwt.issuer:file-system}")
    private String issuer;

    // Small leeway for clock differences (seconds)
    @Value("${security.jwt.clock-skew-seconds:30}")
    private long clockSkewToleranceSeconds;

    private volatile SecretKey cachedSigningKey;

    private SecretKey signingKey() {
        SecretKey existingSigningKey = cachedSigningKey;
        if (existingSigningKey != null) return existingSigningKey;

        if (jwtSecretBase64 == null || jwtSecretBase64.isBlank()) {
            throw new IllegalStateException(
                "JWT secret is missing/blank. Provide SECURITY_JWT_SECRET as a base64-encoded value.");
        }
        byte[] decodedSecretKeyBytes;
        try {
            decodedSecretKeyBytes = Decoders.BASE64.decode(jwtSecretBase64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                "JWT secret must be base64-encoded. Update SECURITY_JWT_SECRET.", ex);
        }
        if (decodedSecretKeyBytes.length < 32) { // 256 bits minimum for HS256
            throw new IllegalStateException(
                "JWT secret too short. Need ≥ 32 bytes (256 bits) after base64 decode.");
        }
        existingSigningKey = Keys.hmacShaKeyFor(decodedSecretKeyBytes);
        cachedSigningKey = existingSigningKey;
        return existingSigningKey;
    }

    /** Build a token for a principal (usually username or userId). */
    public String generateToken(String subject, Map<String, ?> extraClaims) {
        Objects.requireNonNull(subject, "subject must not be null");
        Instant currentTimestamp = Instant.now();
        Instant expirationTimestamp = currentTimestamp.plusMillis(tokenExpirationMilliseconds);

        var builder = Jwts.builder()
                .issuer(issuer)
                .subject(subject)
                .issuedAt(Date.from(currentTimestamp))
                .expiration(Date.from(expirationTimestamp));

        if (extraClaims != null && !extraClaims.isEmpty()) {
            extraClaims.forEach(builder::claim);
        }

        return builder
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    /** Convenience method for UserDetails compatibility */
    public String generateToken(UserDetails userDetails) {
        return generateToken(userDetails.getUsername(), null);
    }

    /** Return signed claims or throw a JwtException subclass on invalid token. */
    public Jws<Claims> parseSignedClaims(String token) throws JwtException {
        String tokenWithoutBearer = stripBearer(token);
        return Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(issuer)
                .clockSkewSeconds(Math.max(0, clockSkewToleranceSeconds))
                .build()
                .parseSignedClaims(tokenWithoutBearer);
    }

    /** Simple validity check plus optional subject match. */
    public boolean isValid(String token, String expectedSubject) {
        try {
            if (token == null || token.trim().isEmpty()) {
                return false;
            }
            var signedClaims = parseSignedClaims(token);
            return expectedSubject == null
                    || expectedSubject.equals(signedClaims.getPayload().getSubject());
        } catch (JwtException e) {
            return false;
        }
    }

    /** Legacy method for UserDetails compatibility */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        return isValid(token, userDetails.getUsername());
    }

    public String extractSubject(String token) {
        return parseSignedClaims(token).getPayload().getSubject();
    }

    /** Legacy method for username extraction */
    public String extractUsername(String token) {
        return extractSubject(token);
    }

    private static String stripBearer(String token) {
        if (token == null) return "";
        String trimmedToken = token.trim();
        return (trimmedToken.regionMatches(true, 0, "Bearer ", 0, 7)) ? trimmedToken.substring(7).trim() : trimmedToken;
    }
}