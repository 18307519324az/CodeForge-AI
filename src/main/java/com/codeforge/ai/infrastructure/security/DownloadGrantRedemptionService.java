package com.codeforge.ai.infrastructure.security;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Tracks redeemed download JWT grant ids (jti) so downloadCount increments at most once per grant.
 * Local single-instance profile; grant TTL aligns with download token expiry.
 */
@Component
public class DownloadGrantRedemptionService {

    private final ConcurrentHashMap<String, Long> redeemedAtMillis = new ConcurrentHashMap<>();

    public boolean tryRedeem(String grantId, long ttlSeconds) {
        if (grantId == null || grantId.isBlank()) {
            return false;
        }
        purgeExpired(ttlSeconds);
        return redeemedAtMillis.putIfAbsent(grantId, Instant.now().toEpochMilli()) == null;
    }

    private void purgeExpired(long ttlSeconds) {
        long cutoff = Instant.now().toEpochMilli() - ttlSeconds * 1000L;
        Iterator<Map.Entry<String, Long>> iterator = redeemedAtMillis.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue() < cutoff) {
                iterator.remove();
            }
        }
    }
}
