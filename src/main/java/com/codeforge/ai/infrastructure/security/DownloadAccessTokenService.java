package com.codeforge.ai.infrastructure.security;

import com.codeforge.ai.application.service.MarketplacePublicationAccessGuard;
import com.codeforge.ai.domain.app.entity.AppPublicationEntity;
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
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class DownloadAccessTokenService {

    public static final String DOWNLOAD_PURPOSE = "PUBLIC_DOWNLOAD";
    private static final String CLAIM_PURPOSE = "purpose";
    private static final String CLAIM_PUBLICATION_ID = "publicationId";
    private static final String CLAIM_APP_ID = "downloadAppId";
    private static final String CLAIM_VERSION_ID = "downloadVersionId";
    private static final String CLAIM_EXPORT_PACKAGE_ID = "exportPackageId";
    private static final long DOWNLOAD_TOKEN_EXPIRE_SECONDS = 600L;

    private final SecretKey secretKey;
    private final JwtProperties jwtProperties;
    private final MarketplacePublicationAccessGuard marketplacePublicationAccessGuard;

    public DownloadAccessTokenService(JwtProperties jwtProperties,
                                      MarketplacePublicationAccessGuard marketplacePublicationAccessGuard) {
        this.jwtProperties = jwtProperties;
        this.marketplacePublicationAccessGuard = marketplacePublicationAccessGuard;
        this.secretKey = Keys.hmacShaKeyFor(resolveSecret(jwtProperties.secret()));
    }

    public String createDownloadToken(Long publicationId,
                                      Long appId,
                                      Long versionId,
                                      Long exportPackageId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(DOWNLOAD_TOKEN_EXPIRE_SECONDS);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(jwtProperties.issuer())
                .subject("download:" + publicationId)
                .claim(CLAIM_PURPOSE, DOWNLOAD_PURPOSE)
                .claim(CLAIM_PUBLICATION_ID, publicationId)
                .claim(CLAIM_APP_ID, appId)
                .claim(CLAIM_VERSION_ID, versionId)
                .claim(CLAIM_EXPORT_PACKAGE_ID, exportPackageId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public ErrorCode resolveDownloadTokenError(String token,
                                               AppPublicationEntityMapper publicationMapper) {
        try {
            Claims claims = parseClaims(token);
            if (!DOWNLOAD_PURPOSE.equals(String.valueOf(claims.get(CLAIM_PURPOSE)))) {
                return ErrorCode.TOKEN_INVALID;
            }
            Long publicationId = readLongClaim(claims, CLAIM_PUBLICATION_ID);
            Long exportPackageId = readLongClaim(claims, CLAIM_EXPORT_PACKAGE_ID);
            if (publicationId == null || exportPackageId == null) {
                return ErrorCode.TOKEN_INVALID;
            }
            AppPublicationEntity publication = publicationMapper.findActiveById(publicationId);
            ErrorCode publicationError = marketplacePublicationAccessGuard.resolvePublicAccessError(publication);
            if (publicationError != null) {
                return publicationError;
            }
            if (!Boolean.TRUE.equals(publication.getAllowDownload())) {
                return ErrorCode.PUBLICATION_DOWNLOAD_DISABLED;
            }
            Long tokenAppId = readLongClaim(claims, CLAIM_APP_ID);
            if (tokenAppId == null || !publication.getAppId().equals(tokenAppId)) {
                return ErrorCode.RESOURCE_FORBIDDEN;
            }
            Long tokenVersionId = readLongClaim(claims, CLAIM_VERSION_ID);
            if (tokenVersionId == null || !publication.getVersionId().equals(tokenVersionId)) {
                return ErrorCode.RESOURCE_FORBIDDEN;
            }
            return null;
        } catch (ExpiredJwtException exception) {
            return ErrorCode.TOKEN_EXPIRED;
        } catch (JwtException | IllegalArgumentException exception) {
            return ErrorCode.TOKEN_INVALID;
        }
    }

    public Long readExportPackageId(String token) {
        Claims claims = parseClaims(token);
        return readLongClaim(claims, CLAIM_EXPORT_PACKAGE_ID);
    }

    public Long readAppId(String token) {
        Claims claims = parseClaims(token);
        return readLongClaim(claims, CLAIM_APP_ID);
    }

    public Long readVersionId(String token) {
        Claims claims = parseClaims(token);
        return readLongClaim(claims, CLAIM_VERSION_ID);
    }

    public Long readPublicationId(String token) {
        Claims claims = parseClaims(token);
        return readLongClaim(claims, CLAIM_PUBLICATION_ID);
    }

    public String readGrantId(String token) {
        Claims claims = parseClaims(token);
        return claims.getId();
    }

    public long getDownloadTokenExpireSeconds() {
        return DOWNLOAD_TOKEN_EXPIRE_SECONDS;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
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

    private static byte[] resolveSecret(String secret) {
        try {
            return Decoders.BASE64.decode(secret);
        } catch (RuntimeException exception) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
