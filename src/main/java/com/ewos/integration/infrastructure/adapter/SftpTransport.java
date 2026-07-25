package com.ewos.integration.infrastructure.adapter;

import java.io.IOException;

/**
 * Port for the actual SFTP wire protocol, kept separate from {@link SftpIntegrationAdapter} so
 * the adapter's config parsing / result classification is unit-testable without a real SSH
 * client. See {@link UnavailableSftpTransport} and Sprint 14.4's Implementation Report for why no
 * production transport ships in this environment.
 */
public interface SftpTransport {

    void upload(SftpTarget target, String fileName, byte[] content) throws IOException;

    record SftpTarget(String host, int port, String username, String credentialRef, String remotePath) {}
}
