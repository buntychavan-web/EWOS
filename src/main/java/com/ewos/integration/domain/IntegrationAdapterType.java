package com.ewos.integration.domain;

/**
 * The transport an {@link IntegrationConfiguration} uses to move data to/from an external system.
 */
public enum IntegrationAdapterType {
    REST,
    SFTP,
    CSV,
    EXCEL,
    FILE_UPLOAD
}
