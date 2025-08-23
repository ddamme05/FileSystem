package org.ddamme.security;

import org.ddamme.security.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.JwtException;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // Set up the service using reflection to inject values
        ReflectionTestUtils.setField(jwtService, "jwtSecretBase64", "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1uMDEyMzQ1Njc4OWFiY2RlZg=="); // 32 bytes base64
        ReflectionTestUtils.setField(jwtService, "tokenExpirationMilliseconds", 60000L); // 1 minute
        ReflectionTestUtils.setField(jwtService, "issuer", "test-issuer");
        ReflectionTestUtils.setField(jwtService, "clockSkewToleranceSeconds", 30L);
    }

    @Test
    @DisplayName("generateToken and isTokenValid true for same user")
    void tokenLifecycle_valid() {
        UserDetails user = User.withUsername("alice").password("p").roles("USER").build();
        String token = jwtService.generateToken(user);
        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
        assertThat(jwtService.isValid(token, "alice")).isTrue();
        assertThat(jwtService.extractSubject(token)).isEqualTo("alice");
    }

    @Test
    @DisplayName("generateToken with extra claims")
    void tokenWithExtraClaims() {
        String token = jwtService.generateToken("alice", 
            java.util.Map.of("role", "admin", "department", "IT"));
        assertThat(token).isNotBlank();
        assertThat(jwtService.extractSubject(token)).isEqualTo("alice");
        assertThat(jwtService.isValid(token, "alice")).isTrue();
        
        // Extract claims to verify extra claims were included
        var claims = jwtService.parseSignedClaims(token).getPayload();
        assertThat(claims.get("role")).isEqualTo("admin");
        assertThat(claims.get("department")).isEqualTo("IT");
        assertThat(claims.getIssuer()).isEqualTo("test-issuer");
    }

    @Test
    @DisplayName("isValid returns false for invalid token")
    void token_invalid_whenMalformed() {
        assertThat(jwtService.isValid("invalid.token.here", "alice")).isFalse();
        assertThat(jwtService.isValid("", "alice")).isFalse();
        assertThat(jwtService.isValid(null, "alice")).isFalse();
    }

    @Test
    @DisplayName("isValid returns false for wrong subject")
    void token_invalid_whenWrongSubject() {
        String token = jwtService.generateToken("alice", null);
        assertThat(jwtService.isValid(token, "bob")).isFalse();
    }

    @Test
    @DisplayName("stripBearer functionality through parseSignedClaims")
    void bearerTokenStripping() {
        String token = jwtService.generateToken("alice", null);
        String bearerToken = "Bearer " + token;
        
        // Should work with Bearer prefix
        var claims = jwtService.parseSignedClaims(bearerToken);
        assertThat(claims.getPayload().getSubject()).isEqualTo("alice");
        
        // Should also work without Bearer prefix
        var claimsWithoutBearer = jwtService.parseSignedClaims(token);
        assertThat(claimsWithoutBearer.getPayload().getSubject()).isEqualTo("alice");
    }

    @Test
    @DisplayName("parseSignedClaims throws JwtException for invalid token")
    void parseSignedClaims_throwsOnInvalid() {
        assertThatThrownBy(() -> jwtService.parseSignedClaims("invalid.token"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("isValid returns false for expired token")
    void token_invalid_whenExpired() throws InterruptedException {
        // Create a service with very short expiration
        JwtService shortExpirationService = new JwtService();
        ReflectionTestUtils.setField(shortExpirationService, "jwtSecretBase64", "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1uMDEyMzQ1Njc4OWFiY2RlZg==");
        ReflectionTestUtils.setField(shortExpirationService, "tokenExpirationMilliseconds", 1L); // 1 millisecond
        ReflectionTestUtils.setField(shortExpirationService, "issuer", "test-issuer");
        ReflectionTestUtils.setField(shortExpirationService, "clockSkewToleranceSeconds", 0L); // No tolerance for this test

        String token = shortExpirationService.generateToken("alice", null);
        
        // Wait for token to expire
        Thread.sleep(10);
        
        assertThat(shortExpirationService.isValid(token, "alice")).isFalse();
    }

    @Test
    @DisplayName("isValid returns false for token with wrong issuer")
    void token_invalid_whenWrongIssuer() {
        // Create a service with different issuer
        JwtService differentIssuerService = new JwtService();
        ReflectionTestUtils.setField(differentIssuerService, "jwtSecretBase64", "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1uMDEyMzQ1Njc4OWFiY2RlZg==");
        ReflectionTestUtils.setField(differentIssuerService, "tokenExpirationMilliseconds", 60000L);
        ReflectionTestUtils.setField(differentIssuerService, "issuer", "different-issuer"); // Different issuer!
        ReflectionTestUtils.setField(differentIssuerService, "clockSkewToleranceSeconds", 30L);

        // Generate token with different issuer
        String tokenWithDifferentIssuer = differentIssuerService.generateToken("alice", null);
        
        // Our service should reject it due to issuer mismatch
        assertThat(jwtService.isValid(tokenWithDifferentIssuer, "alice")).isFalse();
        
        // Should also throw when trying to parse directly
        assertThatThrownBy(() -> jwtService.parseSignedClaims(tokenWithDifferentIssuer))
                .isInstanceOf(JwtException.class);
    }
}


