package com.aicontext.maven.scaffolding;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/**
 * Loads scaffolding configuration from YAML files.
 */
public class ScaffoldingConfigLoader {

    private static final Yaml yaml = new Yaml();

    /**
     * Loads configuration from a YAML file.
     */
    public static ScaffoldingConfig load(Path configPath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            Map<String, Object> data = yaml.load(inputStream);
            return ScaffoldingConfig.fromMap(data);
        }
    }

    /**
     * Loads configuration from a classpath resource.
     */
    public static ScaffoldingConfig loadFromResource(String resourcePath) throws IOException {
        try (InputStream inputStream = ScaffoldingConfigLoader.class
                .getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            Map<String, Object> data = yaml.load(inputStream);
            return ScaffoldingConfig.fromMap(data);
        }
    }
}
