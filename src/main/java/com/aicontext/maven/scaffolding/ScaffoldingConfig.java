package com.aicontext.maven.scaffolding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration for scaffolding structure of an AI assistant.
 */
public class ScaffoldingConfig {
    private String assistant;
    private String outputDir;
    private List<FileDefinition> files;

    public ScaffoldingConfig() {
        this.files = new ArrayList<>();
    }

    public String getAssistant() {
        return assistant;
    }

    public void setAssistant(String assistant) {
        this.assistant = assistant;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public List<FileDefinition> getFiles() {
        return files;
    }

    public void setFiles(List<FileDefinition> files) {
        this.files = files;
    }

    @SuppressWarnings("unchecked")
    public static ScaffoldingConfig fromMap(Map<String, Object> map) {
        ScaffoldingConfig config = new ScaffoldingConfig();
        config.setAssistant((String) map.get("assistant"));
        config.setOutputDir((String) map.get("outputDir"));

        if (map.get("files") instanceof List) {
            List<Object> filesList = (List<Object>) map.get("files");
            for (Object fileObj : filesList) {
                if (fileObj instanceof Map) {
                    config.getFiles().add(FileDefinition.fromMap((Map<String, Object>) fileObj));
                }
            }
        }

        return config;
    }
}
