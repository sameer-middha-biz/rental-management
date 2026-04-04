package com.rental.pms.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.pms.common.dto.ErrorResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Extracts Bearer token from Authorization header, validates via JwtTokenProvider,
 * and sets Authentication in SecurityContext.
 * Skips silently if no token is present (allows public endpoints through).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = jwtTokenProvider.validateAndExtractClaims(token);

            // Skip refresh tokens — they should not be used for API authentication
            if ("refresh".equals(claims.get("type", String.class))) {
                filterChain.doFilter(request, response);
                return;
            }

            UUID userId = jwtTokenProvider.getUserId(claims);
            UUID tenantId = jwtTokenProvider.getTenantId(claims);
            List<String> roles = jwtTokenProvider.getRoles(claims);
            List<String> permissions = jwtTokenProvider.getPermissions(claims);

            JwtUserDetails userDetails = new JwtUserDetails(userId, tenantId, roles, permissions);

            // Build authorities from roles (prefixed with ROLE_) and permissions
            List<SimpleGrantedAuthority> authorities = Stream.concat(
                    roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)),
                    permissions.stream().map(SimpleGrantedAuthority::new)
            ).toList();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (ExpiredJwtException ex) {
            log.debug("Expired JWT token for request: {}", request.getRequestURI());
            writeErrorResponse(response, 401, "Unauthorized", "Token has expired", "AUTH.TOKEN.EXPIRED",
                    request.getRequestURI());
            return;
        } catch (JwtException ex) {
            log.debug("Invalid JWT token for request: {}", request.getRequestURI());
            writeErrorResponse(response, 401, "Unauthorized", "Invalid token", "AUTH.TOKEN.INVALID",
                    request.getRequestURI());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeErrorResponse(HttpServletResponse response, int status, String error,
                                    String message, String errorCode, String path) throws IOException {
        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(), status, error, message, errorCode, path, null);
        response.setStatus(status);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
