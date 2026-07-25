package com.ewos.integration.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared {@code outputDirectory} resolution for the file-writing adapters (CSV/Excel/File Upload). */
final class OutputDirectories {

    private OutputDirectories() {}

    static Path resolve(ObjectMapper json, String configJson) {
        if (configJson == null || configJson.isBlank()) {
            throw new IllegalArgumentException("Integration configuration is missing config_json");
        }
        JsonNode node;
        try {
            node = json.readTree(configJson);
        } catch (IOException e) {
            throw new IllegalArgumentException("Integration configuration config_json is not valid JSON", e);
        }
        JsonNode dirNode = node.get("outputDirectory");
        if (dirNode == null || dirNode.asText().isBlank()) {
            throw new IllegalArgumentException("Integration configuration is missing 'outputDirectory'");
        }
        Path dir = Path.of(dirNode.asText());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dir;
    }
}
