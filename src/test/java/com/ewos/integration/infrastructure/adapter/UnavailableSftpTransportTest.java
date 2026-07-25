package com.ewos.integration.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class UnavailableSftpTransportTest {

    private final UnavailableSftpTransport transport = new UnavailableSftpTransport();

    @Test
    void alwaysThrowsAClearIoException() {
        SftpTransport.SftpTarget target =
                new SftpTransport.SftpTarget("host", 22, "user", "cred-ref", "/inbound");

        assertThatThrownBy(() -> transport.upload(target, "file.json", new byte[0]))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("No SFTP transport is configured");
    }
}
