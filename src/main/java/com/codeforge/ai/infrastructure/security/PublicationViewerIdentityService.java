package com.codeforge.ai.infrastructure.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PublicationViewerIdentityService {

    public static final String VIEWER_COOKIE_NAME = "codeforge_viewer_id";
    private static final String VIEWER_COOKIE_PATH = "/api/v1/public";
    private static final long VIEWER_COOKIE_MAX_AGE_SECONDS = 365L * 24 * 60 * 60;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] hmacSecret;

    public PublicationViewerIdentityService(JwtProperties jwtProperties) {
        this.hmacSecret = resolveSecret(jwtProperties.secret());
    }

    public String resolveViewerKey(Long userId, HttpServletRequest request, HttpServletResponse response) {
        if (userId != null) {
            return "u:" + userId;
        }
        String viewerId = readViewerCookie(request);
        if (!StringUtils.hasText(viewerId)) {
            viewerId = UUID.randomUUID().toString();
            response.addHeader("Set-Cookie", buildViewerCookie(viewerId).toString());
        }
        return "a:" + viewerId;
    }

    public String hashViewerKey(String viewerKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacSecret, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(viewerKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash viewer key", exception);
        }
    }

    private static String readViewerCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (VIEWER_COOKIE_NAME.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue().trim();
            }
        }
        return null;
    }

    private static ResponseCookie buildViewerCookie(String viewerId) {
        return ResponseCookie.from(VIEWER_COOKIE_NAME, viewerId)
                .httpOnly(true)
                .path(VIEWER_COOKIE_PATH)
                .maxAge(VIEWER_COOKIE_MAX_AGE_SECONDS)
                .sameSite("Lax")
                .build();
    }

    private static byte[] resolveSecret(String secret) {
        try {
            return io.jsonwebtoken.io.Decoders.BASE64.decode(secret);
        } catch (RuntimeException exception) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
