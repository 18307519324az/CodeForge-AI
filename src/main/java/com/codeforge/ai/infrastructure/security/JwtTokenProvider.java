package com.codeforge.ai.infrastructure.security;

import com.codeforge.ai.shared.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_ACCOUNT = "account";
    private static final String CLAIM_ROLES = "roles";

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(resolveSecret(jwtProperties.secret()));
    }

    public String createAccessToken(CurrentUser currentUser) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtProperties.accessTokenExpireSeconds());
        return Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(String.valueOf(currentUser.userId()))
                .claim(CLAIM_UID, currentUser.userId())
                .claim(CLAIM_ACCOUNT, currentUser.account())
                .claim(CLAIM_ROLES, currentUser.platformRoles())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public CurrentUser parseCurrentUser(String token) {
        Claims claims = parseClaims(token);
        Object uid = claims.get(CLAIM_UID);
        Long userId = uid instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(uid));
        Object roles = claims.get(CLAIM_ROLES);
        @SuppressWarnings("unchecked")
        List<String> platformRoles = roles instanceof List<?> roleList
                ? roleList.stream().map(String::valueOf).toList()
                : List.of();
        return new CurrentUser(userId, String.valueOf(claims.get(CLAIM_ACCOUNT)), platformRoles);
    }

    public boolean isValidToken(String token) {
        return resolveTokenError(token) == null;
    }

    public ErrorCode resolveTokenError(String token) {
        try {
            parseClaims(token);
            return null;
        } catch (ExpiredJwtException exception) {
            return ErrorCode.TOKEN_EXPIRED;
        } catch (JwtException | IllegalArgumentException exception) {
            return ErrorCode.TOKEN_INVALID;
        }
    }

    public long getAccessTokenExpireSeconds() {
        return jwtProperties.accessTokenExpireSeconds();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static byte[] resolveSecret(String secret) {
        try {
            return Decoders.BASE64.decode(secret);
        } catch (RuntimeException exception) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
