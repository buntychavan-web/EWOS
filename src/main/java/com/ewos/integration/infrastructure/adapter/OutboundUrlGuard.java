package com.ewos.integration.infrastructure.adapter;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Baseline SSRF mitigation for {@link RestIntegrationAdapter}: {@code config_json.url} is
 * arbitrary, tenant-admin-supplied input (a tenant can grant {@code INTEGRATION_WRITE} to its own
 * custom role, not only platform {@code SYSTEM_ADMIN} — see {@code Role.tenantId}), and this
 * adapter's job is to make an outbound HTTP call to it server-side. Without a check, that URL could
 * target the cloud metadata endpoint, an internal admin API, or any other host on the platform's
 * private network.
 *
 * <p>Rejects the scheme unless it's {@code http}/{@code https}, and rejects a literal IP address
 * (or a hostname that resolves to one) in a loopback, link-local, site-local (RFC 1918), or
 * multicast range. A hostname that fails to resolve at all is deliberately let through — that is
 * not evidence of anything suspicious, and blocking it here would only produce a worse error
 * message than the real connection attempt already would.
 */
final class OutboundUrlGuard {

    private OutboundUrlGuard() {}

    static void assertSafe(String rawUrl) {
        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Integration configuration 'url' is malformed", e);
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException(
                    "Integration configuration 'url' must use http or https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Integration configuration 'url' has no host");
        }
        if ("localhost".equalsIgnoreCase(host)
                || host.toLowerCase(Locale.ROOT).endsWith(".local")) {
            throw new IllegalArgumentException(
                    "Integration configuration 'url' may not target a local host");
        }
        try {
            InetAddress resolved = InetAddress.getByName(host);
            if (resolved.isLoopbackAddress()
                    || resolved.isLinkLocalAddress()
                    || resolved.isSiteLocalAddress()
                    || resolved.isAnyLocalAddress()
                    || resolved.isMulticastAddress()) {
                throw new IllegalArgumentException(
                        "Integration configuration 'url' resolves to a private or local address");
            }
        } catch (UnknownHostException e) {
            // Cannot resolve here — not evidence of an unsafe target. The real HTTP call will
            // surface its own, more specific error if the host genuinely doesn't exist.
            return;
        }
    }
}
