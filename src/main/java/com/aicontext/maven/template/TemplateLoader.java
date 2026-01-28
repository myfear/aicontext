package com.aicontext.maven.template;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.logging.Log;

/**
 * Loads templates with user override support.
 * Resolution order: user templates -> plugin defaults
 */
public class TemplateLoader {
    private final Path userTemplateDir;
    private final Path defaultTemplateDir;
    private final Log log;

    public TemplateLoader(Path userTemplateDir, Path defaultTemplateDir, Log log) {
        this.userTemplateDir = userTemplateDir;
        this.defaultTemplateDir = defaultTemplateDir;
        this.log = log;
    }

    /**
     * Loads a template, trying user templates first, then defaults.
     *
     * @param templatePath relative path to template (e.g., "claude/main.md.mustache")
     * @return template content
     * @throws IOException if template cannot be found
     */
    public String loadTemplate(String templatePath) throws IOException {
        // Try user template first
        if (userTemplateDir != null) {
            Path userTemplate = userTemplateDir.resolve(templatePath);
            if (Files.exists(userTemplate)) {
                log.debug("Using user template: " + userTemplate);
                return Files.readString(userTemplate);
            }
        }

        // Fall back to default template directory if it exists
        if (defaultTemplateDir != null) {
            Path defaultTemplate = defaultTemplateDir.resolve(templatePath);
            if (Files.exists(defaultTemplate)) {
                log.debug("Using default template: " + defaultTemplate);
                return Files.readString(defaultTemplate);
            }
        }

        // Try classpath resource as last resort
        String resourcePath = "/scaffolding/templates/" + templatePath;
        try (InputStream inputStream = TemplateLoader.class.getResourceAsStream(resourcePath)) {
            if (inputStream != null) {
                log.debug("Using classpath template: " + resourcePath);
                return new String(inputStream.readAllBytes());
            }
        }

        throw new IOException("Template not found: " + templatePath + 
            " (checked user: " + userTemplateDir + ", default: " + defaultTemplateDir + 
            ", classpath: " + resourcePath + ")");
    }
}
