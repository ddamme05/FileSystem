package org.ddamme.security;

import org.ddamme.security.properties.JwtProperties;
import org.ddamme.security.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setExpirationMs(60_000); // 1 minute
        String base64_32_bytes = "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1uMDEyMzQ1Njc4OWFiY2RlZg=="; // 32 bytes base64
        props.setSecret(base64_32_bytes);
        jwtService = new JwtService(props);
        jwtService.initializeSecretKey();
    }

    @Test
    @DisplayName("generateToken and isTokenValid true for same user")
    void tokenLifecycle_valid() {
        UserDetails user = User.withUsername("alice").password("p").roles("USER").build();
        String token = jwtService.generateToken(user);
        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    @DisplayName("initializeSecretKey throws on invalid secret")
    void initSecret_invalid() {
        JwtProperties bad = new JwtProperties();
        bad.setExpirationMs(60000);
        bad.setSecret("");
        JwtService svc = new JwtService(bad);
        assertThatThrownBy(svc::initializeSecretKey).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("isTokenValid throws ExpiredJwtException for expired token")
    void token_invalid_whenExpired() {
        JwtProperties shortLived = new JwtProperties();
        shortLived.setExpirationMs(-1000);
        shortLived.setSecret("MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1uMDEyMzQ1Njc4OWFiY2RlZg==");
        JwtService shortSvc = new JwtService(shortLived);
        shortSvc.initializeSecretKey();

        UserDetails user = User.withUsername("alice").password("p").roles("USER").build();
        String token = shortSvc.generateToken(user);
        assertThatThrownBy(() -> shortSvc.isTokenValid(token, user))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("isTokenValid throws SignatureException for signature mismatch")
    void token_invalid_whenSignatureMismatch() {
        // Service A
        JwtProperties pA = new JwtProperties();
        pA.setExpirationMs(60_000);
        pA.setSecret("MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1uMDEyMzQ1Njc4OWFiY2RlZg==");
        JwtService serviceA = new JwtService(pA);
        serviceA.initializeSecretKey();

        // Service B with different secret
        JwtProperties pB = new JwtProperties();
        pB.setExpirationMs(60_000);
        pB.setSecret("QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo0NTM2Nzg5MDEyMzQ1Njc4OQ==");
        JwtService serviceB = new JwtService(pB);
        serviceB.initializeSecretKey();

        UserDetails user = User.withUsername("alice").password("p").roles("USER").build();
        String tokenFromA = serviceA.generateToken(user);

        assertThatThrownBy(() -> serviceB.isTokenValid(tokenFromA, user))
                .isInstanceOf(SignatureException.class);
    }
}


