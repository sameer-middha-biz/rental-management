package com.rental.pms.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static JwtTokenProvider tokenProvider;
    private static java.security.PrivateKey wrongPrivateKey;

    @BeforeAll
    static void setUp() throws Exception {
        tokenProvider = new JwtTokenProvider(
                new ClassPathResource("keys/private.pem"),
                new ClassPathResource("keys/public.pem"),
                Duration.ofMinutes(15),
                Duration.ofDays(7)
        );

        // Generate a different key pair for tamper testing
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair wrongKeyPair = generator.generateKeyPair();
        wrongPrivateKey = wrongKeyPair.getPrivate();
    }

    @Test
    void generateAccessToken_ShouldContainAllClaims() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        List<String> roles = List.of("AGENCY_ADMIN");
        List<String> permissions = List.of("PROPERTY_CREATE", "BOOKING_VIEW");

        String token = tokenProvider.generateAccessToken(userId, tenantId, roles, permissions);

        assertThat(token).isNotBlank();

        Claims claims = tokenProvider.validateAndExtractClaims(token);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("tenantId", String.class)).isEqualTo(tenantId.toString());
        assertThat(claims.getId()).isNotBlank(); // jti claim
        assertThat(tokenProvider.getRoles(claims)).containsExactly("AGENCY_ADMIN");
        assertThat(tokenProvider.getPermissions(claims)).containsExactly("PROPERTY_CREATE", "BOOKING_VIEW");
    }

    @Test
    void generateAccessToken_ShouldExpireIn15Minutes() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String token = tokenProvider.generateAccessToken(userId, tenantId, List.of(), List.of());
        Claims claims = tokenProvider.validateAndExtractClaims(token);

        Instant expiration = claims.getExpiration().toInstant();
        Instant issuedAt = claims.getIssuedAt().toInstant();
        Duration duration = Duration.between(issuedAt, expiration);

        assertThat(duration).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void generateRefreshToken_ShouldContainTypeClaimAndUserId() {
        UUID userId = UUID.randomUUID();

        String token = tokenProvider.generateRefreshToken(userId);
        Claims claims = tokenProvider.validateAndExtractClaims(token);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
        assertThat(claims.getId()).isNotBlank(); // jti claim
    }

    @Test
    void generateRefreshToken_ShouldExpireIn7Days() {
        UUID userId = UUID.randomUUID();

        String token = tokenProvider.generateRefreshToken(userId);
        Claims claims = tokenProvider.validateAndExtractClaims(token);

        Instant expiration = claims.getExpiration().toInstant();
        Instant issuedAt = claims.getIssuedAt().toInstant();
        Duration duration = Duration.between(issuedAt, expiration);

        assertThat(duration).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void validateAndExtractClaims_WhenExpiredToken_ShouldThrowExpiredJwtException() {
        // Create a token provider with 0-second expiry to force immediate expiration
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(
                new ClassPathResource("keys/private.pem"),
                new ClassPathResource("keys/public.pem"),
                Duration.ofSeconds(0),
                Duration.ofSeconds(0)
        );

        String token = shortLivedProvider.generateAccessToken(
                UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of());

        assertThatThrownBy(() -> tokenProvider.validateAndExtractClaims(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void validateAndExtractClaims_WhenTamperedToken_ShouldThrowJwtException() {
        // Sign a token with a wrong key
        String tamperedToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(15))))
                .signWith(wrongPrivateKey, Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> tokenProvider.validateAndExtractClaims(tamperedToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validateAndExtractClaims_WhenMalformedToken_ShouldThrowJwtException() {
        assertThatThrownBy(() -> tokenProvider.validateAndExtractClaims("not.a.valid.token"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void getUserId_WithInvalidUUID_ShouldThrowJwtException() {
        Claims claims = new io.jsonwebtoken.impl.DefaultClaims(java.util.Map.of("sub", "not-a-uuid"));

        assertThatThrownBy(() -> tokenProvider.getUserId(claims))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Invalid userId");
    }

    @Test
    void getTenantId_WithInvalidUUID_ShouldThrowJwtException() {
        Claims claims = new io.jsonwebtoken.impl.DefaultClaims(java.util.Map.of(
                "sub", UUID.randomUUID().toString(),
                "tenantId", "not-a-uuid"
        ));

        assertThatThrownBy(() -> tokenProvider.getTenantId(claims))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Invalid tenantId");
    }

    @Test
    void getUserId_ShouldReturnUUIDFromSubjectClaim() {
        UUID userId = UUID.randomUUID();
        String token = tokenProvider.generateAccessToken(userId, UUID.randomUUID(), List.of(), List.of());
        Claims claims = tokenProvider.validateAndExtractClaims(token);

        assertThat(tokenProvider.getUserId(claims)).isEqualTo(userId);
    }

    @Test
    void getTenantId_ShouldReturnNullForSuperAdminToken() {
        UUID userId = UUID.randomUUID();
        // Super Admin tokens have no tenantId
        String token = tokenProvider.generateAccessToken(userId, null, List.of("SUPER_ADMIN"), List.of());
        Claims claims = tokenProvider.validateAndExtractClaims(token);

        assertThat(tokenProvider.getTenantId(claims)).isNull();
    }

    @Test
    void getRoles_WhenNoRoles_ShouldReturnEmptyList() {
        String token = tokenProvider.generateAccessToken(
                UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of("SOME_PERMISSION"));
        Claims claims = tokenProvider.validateAndExtractClaims(token);

        assertThat(tokenProvider.getRoles(claims)).isEmpty();
    }

    @Test
    void getPermissions_WhenNoPermissions_ShouldReturnEmptyList() {
        String token = tokenProvider.generateAccessToken(
                UUID.randomUUID(), UUID.randomUUID(), List.of("AGENCY_ADMIN"), List.of());
        Claims claims = tokenProvider.validateAndExtractClaims(token);

        assertThat(tokenProvider.getPermissions(claims)).isEmpty();
    }

    @Test
    void getAccessTokenExpiry_ShouldReturn15Minutes() {
        assertThat(tokenProvider.getAccessTokenExpiry()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void getRefreshTokenExpiry_ShouldReturn7Days() {
        assertThat(tokenProvider.getRefreshTokenExpiry()).isEqualTo(Duration.ofDays(7));
    }
}
