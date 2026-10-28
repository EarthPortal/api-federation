package org.semantics.apigateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared negative cache of unreachable portals. When a portal fails (e.g. times out), it is
 * remembered for a short window so the next requests skip it instead of waiting on it again.
 * Used by {@link ApiAccessor} so every endpoint (search, artefacts, annotator) benefits.
 */
@Component
public class PortalHealthRegistry {

    @Value("${gateway.portal-unreachable-ttl-ms:600000}")
    private long unreachableTtlMs;

    private final Map<String, Long> unreachableUntil = new ConcurrentHashMap<>();

    public boolean isUnreachable(String baseUrl) {
        if (baseUrl == null) return false;
        Long until = unreachableUntil.get(baseUrl);
        return until != null && until > System.currentTimeMillis();
    }

    public void markUnreachable(String baseUrl) {
        if (baseUrl != null) unreachableUntil.put(baseUrl, System.currentTimeMillis() + unreachableTtlMs);
    }

    public void markReachable(String baseUrl) {
        if (baseUrl != null) unreachableUntil.remove(baseUrl);
    }

    public static String baseUrl(String url) {
        if (url == null) return null;
        int scheme = url.indexOf("://");
        if (scheme < 0) return null;
        int slash = url.indexOf('/', scheme + 3);
        return slash < 0 ? url : url.substring(0, slash);
    }
}
