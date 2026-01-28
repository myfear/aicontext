package com.aicontext.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for document generation functionality in AIContextMojo.
 */
class DocumentGenerationTest {

    private AIContextMojo mojo;
    private Path outputDir;
    private Path testBaseDir;

    @BeforeEach
    void setUp() throws Exception {
        mojo = new AIContextMojo();
        
        // Use target directory for test output instead of temp directory
        testBaseDir = Path.of("target/test-output", getClass().getSimpleName(), 
            String.valueOf(System.currentTimeMillis()));
        Files.createDirectories(testBaseDir);
        
        outputDir = testBaseDir.resolve("output");
        Files.createDirectories(outputDir);
        
        // Set projectName so we don't need to access project.getArtifactId()
        java.lang.reflect.Field projectNameField = AIContextMojo.class.getDeclaredField("projectName");
        projectNameField.setAccessible(true);
        projectNameField.set(mojo, "Test Project");
        
        java.lang.reflect.Field outputDirField = AIContextMojo.class.getDeclaredField("outputDir");
        outputDirField.setAccessible(true);
        outputDirField.set(mojo, outputDir.toFile());
    }

    @Test
    void testClaudeMainFileGeneration() throws Exception {
        List<Object> entries = createSampleEntries();
        Path claudeDir = outputDir.resolve("claude");
        Files.createDirectories(claudeDir);

        // Use reflection to call private method
        java.lang.reflect.Method method = AIContextMojo.class.getDeclaredMethod(
            "generateClaudeMainFile", Path.class, java.util.List.class);
        method.setAccessible(true);
        method.invoke(mojo, claudeDir, entries);

        Path mainFile = claudeDir.resolve("CLAUDE.md");
        assertThat(mainFile).exists();

        String content = Files.readString(mainFile);
        assertThat(content).contains("#");
        assertThat(content).contains("AI Context Guide");
        assertThat(content).contains("Total Entries:");
    }

    @Test
    void testArchitectureFileGeneration() throws Exception {
        List<Object> entries = createSampleEntries();
        Path claudeDir = outputDir.resolve("claude");
        Files.createDirectories(claudeDir);

        java.lang.reflect.Method method = AIContextMojo.class.getDeclaredMethod(
            "generateArchitectureFile", Path.class, java.util.List.class);
        method.setAccessible(true);
        method.invoke(mojo, claudeDir, entries);

        Path archFile = claudeDir.resolve("ARCHITECTURE.md");
        assertThat(archFile).exists();

        String content = Files.readString(archFile);
        assertThat(content).contains("# Architectural Guidance");
        assertThat(content).contains("com.example");
    }

    @Test
    void testDecisionsFileGeneration() throws Exception {
        List<Object> entries = createSampleEntries();
        Path claudeDir = outputDir.resolve("claude");
        Files.createDirectories(claudeDir);

        java.lang.reflect.Method method = AIContextMojo.class.getDeclaredMethod(
            "generateDecisionsFile", Path.class, java.util.List.class);
        method.setAccessible(true);
        method.invoke(mojo, claudeDir, entries);

        Path decisionsFile = claudeDir.resolve("DECISIONS.md");
        assertThat(decisionsFile).exists();

        String content = Files.readString(decisionsFile);
        assertThat(content).contains("# Design Decisions Log");
        assertThat(content).contains("decision");
    }

    @Test
    void testRulesFileGeneration() throws Exception {
        List<Object> entries = createSampleEntries();
        Path claudeDir = outputDir.resolve("claude");
        Files.createDirectories(claudeDir);

        java.lang.reflect.Method method = AIContextMojo.class.getDeclaredMethod(
            "generateRulesFile", Path.class, java.util.List.class);
        method.setAccessible(true);
        method.invoke(mojo, claudeDir, entries);

        Path rulesFile = claudeDir.resolve("RULES.md");
        assertThat(rulesFile).exists();

        String content = Files.readString(rulesFile);
        assertThat(content).contains("# Coding Rules and Constraints");
        assertThat(content).contains("Architectural Rules");
        assertThat(content).contains("Implementation Rules");
    }

    @Test
    void testTagIndexFileGeneration() throws Exception {
        List<Object> entries = createSampleEntries();
        Path claudeDir = outputDir.resolve("claude");
        Files.createDirectories(claudeDir);

        java.lang.reflect.Method method = AIContextMojo.class.getDeclaredMethod(
            "generateTagIndexFile", Path.class, java.util.List.class);
        method.setAccessible(true);
        method.invoke(mojo, claudeDir, entries);

        Path indexFile = claudeDir.resolve("TAG_INDEX.md");
        assertThat(indexFile).exists();

        String content = Files.readString(indexFile);
        assertThat(content).contains("# Tag Index");
        assertThat(content).contains("@aicontext-rule");
        assertThat(content).contains("@aicontext-decision");
    }

    @Test
    void testCursorRulesGeneration() throws Exception {
        List<Object> entries = createSampleEntries();
        
        // Set outputDir field for this test
        java.lang.reflect.Field outputDirField = AIContextMojo.class.getDeclaredField("outputDir");
        outputDirField.setAccessible(true);
        outputDirField.set(mojo, outputDir.toFile());
        
        java.lang.reflect.Method method = AIContextMojo.class.getDeclaredMethod(
            "generateCursorDocs", java.util.List.class);
        method.setAccessible(true);
        method.invoke(mojo, entries);

        Path cursorDir = outputDir.resolve("cursor");
        Path cursorRules = cursorDir.resolve(".cursorrules");
        assertThat(cursorRules).exists();

        String content = Files.readString(cursorRules);
        assertThat(content).contains("# AI Context Rules for Cursor");
        assertThat(content).contains("Architectural Constraints");
        assertThat(content).contains("Design Decisions");
    }

    @Test
    void testCopilotInstructionsGeneration() throws Exception {
        List<Object> entries = createSampleEntries();
        
        // Set outputDir field for this test
        java.lang.reflect.Field outputDirField = AIContextMojo.class.getDeclaredField("outputDir");
        outputDirField.setAccessible(true);
        outputDirField.set(mojo, outputDir.toFile());
        
        java.lang.reflect.Method method = AIContextMojo.class.getDeclaredMethod(
            "generateCopilotDocs", java.util.List.class);
        method.setAccessible(true);
        method.invoke(mojo, entries);

        Path copilotDir = outputDir.resolve("copilot");
        Path copilotFile = copilotDir.resolve("copilot-instructions.md");
        assertThat(copilotFile).exists();

        String content = Files.readString(copilotFile);
        assertThat(content).contains("# GitHub Copilot Instructions");
        assertThat(content).contains("Project-Specific Rules");
        assertThat(content).contains("Key Design Decisions");
    }

    @Test
    void testTruncateMethod() throws Exception {
        java.lang.reflect.Method method = AIContextMojo.class.getDeclaredMethod(
            "truncate", String.class, int.class);
        method.setAccessible(true);

        String shortText = "Short";
        String result1 = (String) method.invoke(mojo, shortText, 10);
        assertThat(result1).isEqualTo("Short");

        String longText = "This is a very long text that should be truncated";
        String result2 = (String) method.invoke(mojo, longText, 20);
        // The truncate method adds "..." after the truncated text
        assertThat(result2).startsWith("This is a very long");
        assertThat(result2).endsWith("...");
        assertThat(result2.length()).isLessThanOrEqualTo(23); // 20 + "..."
    }

    @SuppressWarnings("unchecked")
    private List<Object> createSampleEntries() throws Exception {
        List<Object> entries = new ArrayList<>();
        
        // Access private inner class AIContextEntry via reflection
        Class<?> mojoClass = AIContextMojo.class;
        Class<?> entryClass = null;
        for (Class<?> innerClass : mojoClass.getDeclaredClasses()) {
            if (innerClass.getSimpleName().equals("AIContextEntry")) {
                entryClass = innerClass;
                break;
            }
        }
        
        if (entryClass == null) {
            throw new RuntimeException("AIContextEntry class not found");
        }
        
        // Get Level enum from inner class
        Class<?> levelEnum = null;
        for (Class<?> innerClass : entryClass.getDeclaredClasses()) {
            if (innerClass.getSimpleName().equals("Level")) {
                levelEnum = innerClass;
                break;
            }
        }
        
        if (levelEnum == null) {
            throw new RuntimeException("Level enum not found");
        }
        
        // Create entry using reflection
        java.lang.reflect.Constructor<?> constructor = 
            entryClass.getDeclaredConstructor(
                String.class, String.class, levelEnum,
                String.class, String.class, String.class, int.class);
        constructor.setAccessible(true);
        
        Object archLevel = java.lang.reflect.Array.get(levelEnum.getEnumConstants(), 0); // ARCHITECTURAL
        Object implLevel = java.lang.reflect.Array.get(levelEnum.getEnumConstants(), 1); // IMPLEMENTATION

        entries.add(constructor.newInstance(
            "com.example.SampleClass",
            "src/main/java/com/example/SampleClass.java",
            archLevel,
            "rule",
            "Always use prepared statements",
            null,
            10
        ));

        entries.add(constructor.newInstance(
            "com.example.SampleClass",
            "src/main/java/com/example/SampleClass.java",
            archLevel,
            "decision",
            "Using PostgreSQL for ACID compliance",
            "2024-01-15",
            12
        ));

        entries.add(constructor.newInstance(
            "com.example.SampleClass.method()",
            "src/main/java/com/example/SampleClass.java",
            implLevel,
            "rule",
            "Method-level rule",
            null,
            25
        ));

        return entries;
    }
}
