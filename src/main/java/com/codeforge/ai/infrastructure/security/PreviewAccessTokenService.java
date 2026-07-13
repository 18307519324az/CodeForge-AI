package com.codeforge.ai.infrastructure.security;

import com.codeforge.ai.application.service.MarketplacePublicationAccessGuard;
import com.codeforge.ai.domain.app.entity.AppPublicationEntity;
import com.codeforge.ai.domain.app.enums.AppPublicationStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.AppPublicationEntityMapper;
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
import javax.crypto.SecretKey;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class PreviewAccessTokenService {

    public static final String PREVIEW_TOKEN_COOKIE_NAME = "codeforge_preview_token";
    private static final String CLAIM_PURPOSE = "purpose";
    private static final String CLAIM_VERSION_ID = "previewVersionId";
    private static final String CLAIM_USER_ID = "previewUserId";
    private static final String PREVIEW_PURPOSE = "STATIC_PREVIEW";
    public static final String PUBLIC_PREVIEW_PURPOSE = "PUBLIC_STATIC_PREVIEW";
    private static final String CLAIM_PUBLICATION_ID = "publicationId";
    private static final String CLAIM_APP_ID = "previewAppId";
    private static final String PREVIEW_COOKIE_PATH = "/api/v1/static-preview";
    private static final long PREVIEW_TOKEN_EXPIRE_SECONDS = 600L;

    private final SecretKey secretKey;
    private final JwtProperties jwtProperties;
    private final MarketplacePublicationAccessGuard marketplacePublicationAccessGuard;

    public PreviewAccessTokenService(JwtProperties jwtProperties,
                                     MarketplacePublicationAccessGuard marketplacePublicationAccessGuard) {
        this.jwtProperties = jwtProperties;
        this.marketplacePublicationAccessGuard = marketplacePublicationAccessGuard;
        this.secretKey = Keys.hmacShaKeyFor(resolveSecret(jwtProperties.secret()));
    }

    public String createPreviewToken(CurrentUser currentUser, Long versionId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(PREVIEW_TOKEN_EXPIRE_SECONDS);
        return Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject("preview:" + versionId)
                .claim(CLAIM_PURPOSE, PREVIEW_PURPOSE)
                .claim(CLAIM_VERSION_ID, versionId)
                .claim(CLAIM_USER_ID, currentUser.requiredUserId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public String createPublicPreviewToken(Long publicationId, Long appId, Long versionId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(PREVIEW_TOKEN_EXPIRE_SECONDS);
        return Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject("public-preview:" + publicationId)
                .claim(CLAIM_PURPOSE, PUBLIC_PREVIEW_PURPOSE)
                .claim(CLAIM_PUBLICATION_ID, publicationId)
                .claim(CLAIM_APP_ID, appId)
                .claim(CLAIM_VERSION_ID, versionId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public ErrorCode resolvePreviewTokenError(String token, Long expectedVersionId) {
        return resolvePreviewTokenError(token, expectedVersionId, null);
    }

    public ErrorCode resolvePreviewTokenError(String token,
                                              Long expectedVersionId,
                                              AppPublicationEntityMapper publicationMapper) {
        try {
            Claims claims = parseClaims(token);
            String purpose = String.valueOf(claims.get(CLAIM_PURPOSE));
            Long tokenVersionId = readLongClaim(claims, CLAIM_VERSION_ID);
            if (tokenVersionId == null || !expectedVersionId.equals(tokenVersionId)) {
                return ErrorCode.RESOURCE_FORBIDDEN;
            }
            if (PREVIEW_PURPOSE.equals(purpose)) {
                return null;
            }
            if (PUBLIC_PREVIEW_PURPOSE.equals(purpose)) {
                return resolvePublicPreviewClaims(claims, publicationMapper);
            }
            return ErrorCode.TOKEN_INVALID;
        } catch (ExpiredJwtException exception) {
            return ErrorCode.TOKEN_EXPIRED;
        } catch (JwtException | IllegalArgumentException exception) {
            return ErrorCode.TOKEN_INVALID;
        }
    }

    private ErrorCode resolvePublicPreviewClaims(Claims claims, AppPublicationEntityMapper publicationMapper) {
        if (publicationMapper == null) {
            return ErrorCode.TOKEN_INVALID;
        }
        Long publicationId = readLongClaim(claims, CLAIM_PUBLICATION_ID);
        if (publicationId == null) {
            return ErrorCode.TOKEN_INVALID;
        }
        AppPublicationEntity publication = publicationMapper.findActiveById(publicationId);
        ErrorCode publicationError = marketplacePublicationAccessGuard.resolvePublicAccessError(publication);
        if (publicationError != null) {
            return publicationError;
        }
        if (!Boolean.TRUE.equals(publication.getAllowPreview())) {
            return ErrorCode.PUBLICATION_PREVIEW_DISABLED;
        }
        Long tokenVersionId = readLongClaim(claims, CLAIM_VERSION_ID);
        if (!publication.getVersionId().equals(tokenVersionId)) {
            return ErrorCode.RESOURCE_FORBIDDEN;
        }
        return null;
    }

    private static Long readLongClaim(Claims claims, String claimName) {
        Object value = claims.get(claimName);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    public long getPreviewTokenExpireSeconds() {
        return PREVIEW_TOKEN_EXPIRE_SECONDS;
    }

    public ResponseCookie buildPreviewCookie(String token) {
        return ResponseCookie.from(PREVIEW_TOKEN_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path(PREVIEW_COOKIE_PATH)
                .maxAge(PREVIEW_TOKEN_EXPIRE_SECONDS)
                .build();
    }

    public ResponseCookie buildExpiredPreviewCookie() {
        return ResponseCookie.from(PREVIEW_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path(PREVIEW_COOKIE_PATH)
                .maxAge(0)
                .build();
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
