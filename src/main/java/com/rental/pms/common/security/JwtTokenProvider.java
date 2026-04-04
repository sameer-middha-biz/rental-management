package com.rental.pms.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * RS256 JWT token creation and validation using JJWT.
 * Access tokens carry userId, tenantId, roles, and permissions.
 * Refresh tokens carry only the userId.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final Duration accessTokenExpiry;
    private final Duration refreshTokenExpiry;

    public JwtTokenProvider(
            @Value("${pms.jwt.private-key-location}") Resource privateKeyResource,
            @Value("${pms.jwt.public-key-location}") Resource publicKeyResource,
            @Value("${pms.jwt.access-token-expiry:15m}") Duration accessTokenExpiry,
            @Value("${pms.jwt.refresh-token-expiry:7d}") Duration refreshTokenExpiry
    ) {
        this.privateKey = loadPrivateKey(privateKeyResource);
        this.publicKey = loadPublicKey(publicKeyResource);
        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    /**
     * Generates an RS256-signed access token with user claims.
     */
    public String generateAccessToken(UUID userId, UUID tenantId, List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("tenantId", tenantId != null ? tenantId.toString() : null)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenExpiry)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Generates a refresh token containing only the userId.
     */
    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTokenExpiry)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Validates the token signature and expiry, returns parsed claims.
     *
     * @throws ExpiredJwtException if the token has expired
     * @throws JwtException if the token is invalid or tampered
     */
    public Claims validateAndExtractClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the subject (userId) from a valid token.
     */
    public UUID getUserId(Claims claims) {
        try {
            return UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException e) {
            throw new io.jsonwebtoken.JwtException("Invalid userId in token", e);
        }
    }

    /**
     * Extracts tenantId from the token claims.
     */
    public UUID getTenantId(Claims claims) {
        String tenantId = claims.get("tenantId", String.class);
        if (tenantId == null) {
            return null;
        }
        try {
            return UUID.fromString(tenantId);
        } catch (IllegalArgumentException e) {
            throw new io.jsonwebtoken.JwtException("Invalid tenantId in token", e);
        }
    }

    /**
     * Extracts roles from the token claims.
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        List<String> roles = claims.get("roles", List.class);
        return roles != null ? roles : List.of();
    }

    /**
     * Extracts permissions from the token claims.
     */
    @SuppressWarnings("unchecked")
    public List<String> getPermissions(Claims claims) {
        List<String> permissions = claims.get("permissions", List.class);
        return permissions != null ? permissions : List.of();
    }

    public Duration getAccessTokenExpiry() {
        return accessTokenExpiry;
    }

    public Duration getRefreshTokenExpiry() {
        return refreshTokenExpiry;
    }

    private PrivateKey loadPrivateKey(Resource resource) {
        try {
            String keyContent = readPemContent(resource);
            byte[] keyBytes = Base64.getDecoder().decode(keyContent);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("Failed to load RSA private key from: " + resource, ex);
        }
    }

    private PublicKey loadPublicKey(Resource resource) {
        try {
            String keyContent = readPemContent(resource);
            byte[] keyBytes = Base64.getDecoder().decode(keyContent);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("Failed to load RSA public key from: " + resource, ex);
        }
    }

    private String readPemContent(Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            String pem = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
        }
    }
}
