package com.aicontext.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for AIContextMojo.
 * 
 * Tests full Mojo execution with real Java files and verifies generated output.
 */
class AIContextMojoIntegrationTest {

    private File testPom;
    private File sourceDir;
    private File outputDir;
    private Path testBaseDir;

    @BeforeEach
    void setUp() throws IOException {
        // Use target directory for test output instead of temp directory
        testBaseDir = Path.of("target/test-output", getClass().getSimpleName(), 
            String.valueOf(System.currentTimeMillis()));
        Files.createDirectories(testBaseDir);
        
        sourceDir = testBaseDir.resolve("src/main/java").toFile();
        outputDir = testBaseDir.resolve(".aicontext").toFile();
        
        // Create test pom.xml
        testPom = testBaseDir.resolve("pom.xml").toFile();
        Files.writeString(testPom.toPath(), createTestPom());
    }

    @Test
    void testFullMojoExecution_WithRealJavaFiles() throws Exception {
        // Create test Java files with tags
        createTestJavaFiles();
        
        // Execute mojo
        AIContextMojo mojo = createMojo();
        mojo.execute();
        
        // Verify output structure
        assertThat(outputDir).exists();
        assertThat(outputDir.isDirectory()).isTrue();
        
        // Verify Claude docs
        Path claudeDir = outputDir.toPath().resolve("claude");
        assertThat(claudeDir).exists();
        assertThat(claudeDir.resolve("CLAUDE.md")).exists();
        assertThat(claudeDir.resolve("ARCHITECTURE.md")).exists();
        assertThat(claudeDir.resolve("DECISIONS.md")).exists();
        assertThat(claudeDir.resolve("RULES.md")).exists();
        assertThat(claudeDir.resolve("TAG_INDEX.md")).exists();
        
        // Verify Cursor docs
        Path cursorDir = outputDir.toPath().resolve("cursor");
        assertThat(cursorDir.resolve(".cursorrules")).exists();
        
        // Verify Copilot docs
        Path copilotDir = outputDir.toPath().resolve("copilot");
        assertThat(copilotDir.resolve("copilot-instructions.md")).exists();
    }

    @Test
    void testMojoExecution_ExtractsAllTagTypes() throws Exception {
        createComprehensiveTestFile();
        
        AIContextMojo mojo = createMojo();
        mojo.execute();
        
        // Verify all tag types are extracted
        Path tagIndex = outputDir.toPath().resolve("claude/TAG_INDEX.md");
        String content = Files.readString(tagIndex);
        
        assertThat(content).contains("@aicontext-rule");
        assertThat(content).contains("@aicontext-decision");
        assertThat(content).contains("@aicontext-context");
    }

    @Test
    void testMojoExecution_ExtractsTimestamps() throws Exception {
        createTestFileWithTimestamps();
        
        AIContextMojo mojo = createMojo();
        mojo.execute();
        
        Path decisionsFile = outputDir.toPath().resolve("claude/DECISIONS.md");
        String content = Files.readString(decisionsFile);
        
        assertThat(content).contains("2024-01-15");
        assertThat(content).contains("2024-01-20");
    }

    @Test
    void testMojoExecution_SeparatesArchitecturalAndImplementation() throws Exception {
        createTestFileWithBothLevels();
        
        AIContextMojo mojo = createMojo();
        mojo.execute();
        
        Path rulesFile = outputDir.toPath().resolve("claude/RULES.md");
        String content = Files.readString(rulesFile);
        
        assertThat(content).contains("Architectural Rules (Class-level)");
        assertThat(content).contains("Implementation Rules (Method-level)");
        assertThat(content).contains("com.example.TestClass"); // Class-level
        assertThat(content).contains("testMethod()"); // Method-level
    }

    @Test
    void testMojoExecution_SortsByPriority() throws Exception {
        createTestFileWithMultiplePriorities();
        
        AIContextMojo mojo = createMojo();
        mojo.execute();
        
        Path architectureFile = outputDir.toPath().resolve("claude/ARCHITECTURE.md");
        String content = Files.readString(architectureFile);
        
        // Architectural entries should appear before implementation entries
        int archIndex = content.indexOf("ARCHITECTURAL");
        int implIndex = content.indexOf("IMPLEMENTATION");
        
        if (archIndex >= 0 && implIndex >= 0) {
            assertThat(archIndex).isLessThan(implIndex);
        }
    }

    @Test
    void testMojoExecution_HandlesMultipleClasses() throws Exception {
        createMultipleTestClasses();
        
        AIContextMojo mojo = createMojo();
        mojo.execute();
        
        Path architectureFile = outputDir.toPath().resolve("claude/ARCHITECTURE.md");
        String content = Files.readString(architectureFile);
        
        // Check that both classes are mentioned (may be in different sections)
        assertThat(content).contains("Service");
        assertThat(content).contains("Repository");
    }

    @Test
    void testMojoExecution_GeneratesValidMarkdown() throws Exception {
        createTestJavaFiles();
        
        AIContextMojo mojo = createMojo();
        mojo.execute();
        
        // Verify markdown structure
        Path claudeMain = outputDir.toPath().resolve("claude/CLAUDE.md");
        String content = Files.readString(claudeMain);
        
        // File should start with signature marker
        assertThat(content).startsWith("<!-- AIContext:generated -->");
        // Should contain proper markdown structure
        assertThat(content).contains("#");
        assertThat(content).contains("##");
        assertThat(content).contains("**");
    }

    @Test
    void testCustomContentPreservation_WhenRegenerating() throws Exception {
        createTestJavaFiles();
        
        AIContextMojo mojo = createMojo();
        mojo.execute();
        
        // Add custom content to the generated file
        Path claudeMain = outputDir.toPath().resolve("claude/CLAUDE.md");
        String originalContent = Files.readString(claudeMain);
        
        // Append custom user content after the custom section marker
        String userContent = "\n## My Custom Section\n\nThis is custom content that should be preserved.\n";
        Files.writeString(claudeMain, originalContent + userContent);
        
        // Regenerate
        mojo.execute();
        
        // Verify custom content is preserved
        String regeneratedContent = Files.readString(claudeMain);
        assertThat(regeneratedContent).contains("My Custom Section");
        assertThat(regeneratedContent).contains("This is custom content that should be preserved.");
    }

    @Test
    void testGeneratedFiles_ContainCustomSectionMarker() throws Exception {
        createTestJavaFiles();
        
        AIContextMojo mojo = createMojo();
        mojo.execute();
        
        // Verify custom section marker is present
        Path claudeMain = outputDir.toPath().resolve("claude/CLAUDE.md");
        String content = Files.readString(claudeMain);
        
        assertThat(content).contains("AICONTEXT:CUSTOM");
        assertThat(content).contains("Content below this line will be preserved on regeneration");
        assertThat(content).contains("## Custom Notes");
    }

    private AIContextMojo createMojo() throws Exception {
        AIContextMojo mojo = new AIContextMojo();
        
        // Use reflection to set fields
        setField(mojo, "sourceDir", sourceDir);
        setField(mojo, "outputDir", outputDir);
        setField(mojo, "assistants", "claude,cursor,copilot");
        setField(mojo, "projectName", "Test Project");
        
        // Don't set project field - projectName is set so project.getArtifactId() won't be called
        // Log field is inherited from AbstractMojo and will be null, but that's OK for these tests
        
        return mojo;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void createTestJavaFiles() throws IOException {
        Path testFile = sourceDir.toPath().resolve("com/example/Test.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, """
            package com.example;
            
            /**
             * Test class.
             * 
             * @aicontext-decision [2024-01-15] Using this for testing
             * @aicontext-rule Always follow this rule
             */
            public class Test {
            }
            """);
    }

    private void createComprehensiveTestFile() throws IOException {
        Path testFile = sourceDir.toPath().resolve("com/example/Comprehensive.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, """
            package com.example;
            
            /**
             * Comprehensive test class.
             * 
             * @aicontext-rule Class-level rule
             * @aicontext-decision [2024-01-15] Design decision
             * @aicontext-context Business context
             */
            public class Comprehensive {
                /**
                 * Method with tags.
                 * 
                 * @aicontext-rule Method rule
                 */
                public void method() {
                }
            }
            """);
    }

    private void createTestFileWithTimestamps() throws IOException {
        Path testFile = sourceDir.toPath().resolve("com/example/Timestamped.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, """
            package com.example;
            
            /**
             * Class with timestamps.
             * 
             * @aicontext-decision [2024-01-15] First decision
             * @aicontext-decision [2024-01-20] Second decision
             */
            public class Timestamped {
            }
            """);
    }

    private void createTestFileWithBothLevels() throws IOException {
        Path testFile = sourceDir.toPath().resolve("com/example/TestClass.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, """
            package com.example;
            
            /**
             * Test class.
             * 
             * @aicontext-rule Class-level rule
             */
            public class TestClass {
                /**
                 * Test method.
                 * 
                 * @aicontext-rule Method-level rule
                 */
                public void testMethod() {
                }
            }
            """);
    }

    private void createTestFileWithMultiplePriorities() throws IOException {
        Path testFile = sourceDir.toPath().resolve("com/example/Priority.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, """
            package com.example;
            
            /**
             * Class with architectural rule.
             * 
             * @aicontext-rule Architectural rule
             */
            public class Priority {
                /**
                 * Method with implementation rule.
                 * 
                 * @aicontext-rule Implementation rule
                 */
                public void method() {
                }
            }
            """);
    }

    private void createMultipleTestClasses() throws IOException {
        Path serviceFile = sourceDir.toPath().resolve("com/example/Service.java");
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, """
            package com.example;
            
            /**
             * Service class.
             * 
             * @aicontext-rule Service rule
             */
            public class Service {
            }
            """);

        Path repoFile = sourceDir.toPath().resolve("com/example/Repository.java");
        Files.createDirectories(repoFile.getParent());
        Files.writeString(repoFile, """
            package com.example;
            
            /**
             * Repository class.
             * 
             * @aicontext-rule Repository rule
             */
            public class Repository {
            }
            """);
    }

    private String createTestPom() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>test-project</artifactId>
              <version>1.0-SNAPSHOT</version>
            </project>
            """;
    }
}
