package com.ewos.integration.infrastructure.adapter;

import java.io.IOException;
import org.springframework.stereotype.Component;

/**
 * Default {@link SftpTransport}. No SSH/SFTP client library (e.g. {@code com.jcraft:jsch} or {@code
 * com.hierynomus:sshj}) is available in this environment's offline Maven cache, so this environment
 * cannot ship a real one — see Sprint 14.4 Implementation Report, "Implementation Issues". Fails
 * loudly and immediately with a classification-friendly message rather than silently no-opping or
 * faking success, so the gap is never mistaken for a working integration. Replace this bean with a
 * real transport once the dependency can be added (network-enabled build environment) — {@link
 * SftpIntegrationAdapter} and its config/result handling need no changes.
 */
@Component
public class UnavailableSftpTransport implements SftpTransport {

    @Override
    public void upload(SftpTarget target, String fileName, byte[] content) throws IOException {
        throw new IOException(
                "No SFTP transport is configured in this deployment (missing SSH client dependency)."
                        + " Add a real SftpTransport implementation before enabling SFTP-type"
                        + " integration configurations.");
    }
}
