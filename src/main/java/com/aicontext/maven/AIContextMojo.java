package com.aicontext.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.aicontext.maven.graph.ClassDependencyAnalyzer;
import com.aicontext.maven.graph.GraphEdge;
import com.aicontext.maven.graph.GraphNode;
import com.aicontext.maven.graph.GraphNotationParser;
import com.aicontext.maven.scaffolding.FileDefinition;
import com.aicontext.maven.scaffolding.FilterDefinition;
import com.aicontext.maven.scaffolding.ScaffoldingConfig;
import com.aicontext.maven.scaffolding.ScaffoldingConfigLoader;
import com.aicontext.maven.scaffolding.SortDefinition;
import com.aicontext.maven.template.MustacheTemplateEngine;
import com.aicontext.maven.template.TemplateEngine;
import com.aicontext.maven.template.TemplateLoader;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

/**
 * Maven plugin to extract @aicontext-* tags and generate AI assistant
 * documentation.
 */
@Mojo(name = "generate-docs", defaultPhase = LifecyclePhase.COMPILE)
public class AIContextMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "aicontext.sourceDir", defaultValue = "${project.basedir}/src/main/java")
    private File sourceDir;

    @Parameter(property = "aicontext.outputDir", defaultValue = "${project.basedir}/.aicontext")
    private File outputDir;

    @Parameter(property = "aicontext.assistants")
    private String assistants;

    @Parameter(property = "aicontext.projectName", defaultValue = "${project.artifactId}")
    private String projectName;

    @Parameter(property = "aicontext.assistantOutputDirs")
    private Map<String, String> assistantOutputDirs;

    @Parameter(property = "aicontext.forceOverwrite", defaultValue = "false")
    private boolean forceOverwrite;

    @Parameter(property = "aicontext.validateGraph", defaultValue = "true")
    private boolean validateGraph;

    // Default output locations for each assistant (correct locations where they
    // read instructions)
    private static final Map<String, String> DEFAULT_OUTPUT_DIRS = Map.of(
            "claude", ".", // Project root
            "cursor", ".", // Project root
            "copilot", ".github", // .github directory
            "bob", "." // Project root
    );

    // Keywords for security-related content detection
    private static final Set<String> SECURITY_KEYWORDS = Set.of(
            "security", "auth", "authentication", "authorization", "encrypt", "decrypt",
            "password", "credential", "token", "secret", "permission", "access control",
            "vulnerability", "injection", "xss", "csrf", "sanitize", "validate");

    // Keywords for business-critical content detection
    private static final Set<String> BUSINESS_KEYWORDS = Set.of(
            "payment", "transaction", "billing", "invoice", "critical", "compliance",
            "audit", "regulatory", "gdpr", "pci", "hipaa", "financial", "money");

    // Signature marker to identify plugin-generated files
    private static final String SIGNATURE_MARKER = "AIContext:generated";
    private static final String MARKDOWN_SIGNATURE = "<!-- " + SIGNATURE_MARKER + " -->";
    private static final String YAML_SIGNATURE = "# " + SIGNATURE_MARKER;

    // Custom section marker - content below this line is preserved on regeneration
    private static final String CUSTOM_SECTION_MARKER = "AICONTEXT:CUSTOM";
    private static final String MARKDOWN_CUSTOM_SECTION = "\n---\n\n<!-- " + CUSTOM_SECTION_MARKER
            + " - Content below this line will be preserved on regeneration -->\n\n" +
            "## Custom Notes\n\n" +
            "_Add your own project-specific notes, rules, or context below. This section will not be overwritten._\n\n";
    private static final String YAML_CUSTOM_SECTION = "\n# " + CUSTOM_SECTION_MARKER
            + " - Content below this line will be preserved on regeneration\n" +
            "# Add your own customizations below:\n";

    private static final Pattern TAG_PATTERN = Pattern.compile(
            "@aicontext-(rule|decision|context|graph|graph-ignore)\\s+(.+?)(?=@aicontext-|$)",
            Pattern.DOTALL);

    private static final Pattern DATE_PATTERN = Pattern.compile("\\[(\\d{4}-\\d{2}-\\d{2})\\]");

    @Override
    public void execute() throws MojoExecutionException {
        // Check if assistants parameter is configured
        if (assistants == null || assistants.trim().isEmpty()) {
            getLog().warn("AIContext: No assistants configured. Skipping documentation generation.");
            getLog().warn("AIContext: Please configure the 'assistants' parameter in your pom.xml.");
            getLog().warn("AIContext: Supported assistants: claude, cursor, copilot, bob");
            getLog().warn("AIContext: Example: <assistants>cursor</assistants>");
            return;
        }

        getLog().info("AIContext: Scanning Java source files...");

        try {
            // Parse assistant list
            String[] assistantList = assistants.split(",");

            // Check for existing instruction files before generating
            if (!forceOverwrite) {
                List<String> existingFiles = checkExistingInstructionFiles(assistantList);
                if (!existingFiles.isEmpty()) {
                    StringBuilder errorMsg = new StringBuilder();
                    errorMsg.append("ERROR: Found existing instruction file(s):\n");
                    for (String file : existingFiles) {
                        errorMsg.append("  - ").append(file).append("\n");
                    }
                    errorMsg.append("\nThe AIContext plugin cannot overwrite existing instruction files.\n");
                    errorMsg.append("Please migrate the content to @aicontext-* Javadoc annotations in your code,\n");
                    errorMsg.append("then remove the existing files and run the plugin again.\n");
                    errorMsg.append(
                            "\nAlternatively, use -Daicontext.forceOverwrite=true to overwrite existing files.");
                    throw new MojoExecutionException(errorMsg.toString());
                }
            }

            // Collect all AI context data
            List<AIContextEntry> entries = scanJavaFiles();

            // Validate graph documentation if enabled (dependency vs @aicontext-graph /
            // @aicontext-graph-ignore)
            if (validateGraph) {
                validateGraphDocumentation(entries);
            }

            // Generate documentation for each AI assistant
            for (String assistant : assistantList) {
                generateAssistantDocs(assistant.trim(), entries);
            }

            getLog().info(String.format(
                    "AIContext: Generated docs for %d assistants with %d entries",
                    assistantList.length, entries.size()));

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate AI context docs", e);
        }
    }

    /**
     * Check for existing instruction files that would be overwritten.
     */
    private List<String> checkExistingInstructionFiles(String[] assistantList) {
        List<String> existingFiles = new ArrayList<>();

        // If project is null, skip the check (test environment)
        if (project == null || project.getBasedir() == null) {
            return existingFiles;
        }

        String artifactId = project.getArtifactId();

        for (String assistant : assistantList) {
            String assistantName = assistant.trim().toLowerCase();
            Path outputDir = resolveOutputDir(assistantName, null);

            switch (assistantName) {
                case "claude":
                    checkFile(outputDir.resolve("CLAUDE.md"), existingFiles);
                    checkFile(outputDir.resolve("ARCHITECTURE.md"), existingFiles);
                    checkFile(outputDir.resolve("DECISIONS.md"), existingFiles);
                    checkFile(outputDir.resolve("RULES.md"), existingFiles);
                    checkFile(outputDir.resolve("TAG_INDEX.md"), existingFiles);
                    break;
                case "cursor":
                    checkDirectory(outputDir.resolve(".cursor/rules"), existingFiles);
                    break;
                case "copilot":
                    checkFile(outputDir.resolve("copilot-instructions.md"), existingFiles);
                    break;
                case "bob":
                    checkFile(outputDir.resolve(".Bobmodes"), existingFiles);
                    checkDirectory(outputDir.resolve(".Bob/rules-" + artifactId + "-mode"), existingFiles);
                    checkFile(outputDir.resolve(".bobrules-" + artifactId + "-mode"), existingFiles);
                    break;
            }
        }

        return existingFiles;
    }

    private void checkFile(Path path, List<String> existingFiles) {
        if (Files.exists(path) && Files.isRegularFile(path)) {
            // Check if the file was generated by the plugin (has our signature marker)
            if (!isPluginGeneratedFile(path)) {
                existingFiles.add(path.toString());
            }
        }
    }

    /**
     * Check if a file was generated by this plugin by looking for the signature
     * marker.
     */
    private boolean isPluginGeneratedFile(Path path) {
        try {
            // Read first few lines to check for signature
            String content = Files.readString(path);
            return content.contains(SIGNATURE_MARKER);
        } catch (IOException e) {
            // If we can't read the file, assume it's not ours
            return false;
        }
    }

    private void checkDirectory(Path path, List<String> existingFiles) {
        if (Files.exists(path) && Files.isDirectory(path)) {
            // Check if any file in the directory has our signature marker
            if (!isPluginGeneratedDirectory(path)) {
                existingFiles.add(path.toString() + " (directory)");
            }
        }
    }

    /**
     * Check if a directory was created by this plugin by checking if any file
     * inside has the signature.
     */
    private boolean isPluginGeneratedDirectory(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .anyMatch(this::isPluginGeneratedFile);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Resolve the output directory for a given assistant.
     * Priority: 1. assistantOutputDirs override, 2. config outputDir, 3. default
     * correct location
     * 
     * When project is null (test environment) or config-based generation is not
     * used,
     * falls back to legacy behavior using outputDir parameter with assistant
     * subdirectories.
     */
    private Path resolveOutputDir(String assistant, ScaffoldingConfig config) {
        // Handle null project (test environment or legacy mode) - fall back to
        // outputDir with subdirectory
        if (project == null || project.getBasedir() == null) {
            if (outputDir != null) {
                // Legacy behavior: outputDir/assistant (e.g., .aicontext/claude)
                return outputDir.toPath().resolve(assistant);
            }
            return Path.of(".").resolve(assistant);
        }

        Path baseDir = project.getBasedir().toPath();

        // 1. Check per-assistant override from Maven config
        if (assistantOutputDirs != null && assistantOutputDirs.containsKey(assistant)) {
            String overridePath = assistantOutputDirs.get(assistant);
            if (overridePath.startsWith("/") || overridePath.contains(":")) {
                // Absolute path
                return Path.of(overridePath);
            }
            return baseDir.resolve(overridePath);
        }

        // 2. Check config's outputDir (from YAML) - for new config-driven mode
        if (config != null && config.getOutputDir() != null) {
            String configOutputDir = config.getOutputDir();
            if (configOutputDir.equals(".")) {
                return baseDir;
            }
            return baseDir.resolve(configOutputDir);
        }

        // 3. Use default correct location for production use
        String defaultDir = DEFAULT_OUTPUT_DIRS.getOrDefault(assistant, ".");
        if (defaultDir.equals(".")) {
            return baseDir;
        }
        return baseDir.resolve(defaultDir);
    }

    private List<AIContextEntry> scanJavaFiles() throws IOException {
        List<AIContextEntry> entries = new ArrayList<>();
        JavaParser parser = new JavaParser();

        Files.walk(sourceDir.toPath())
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        CompilationUnit cu = parser.parse(path).getResult().orElse(null);
                        if (cu != null) {
                            entries.addAll(extractEntries(cu, path));
                        }
                    } catch (IOException e) {
                        getLog().warn("Failed to parse: " + path, e);
                    }
                });

        // Sort by priority (architectural > implementation)
        entries.sort(Comparator.comparingInt(AIContextEntry::getPriority).reversed());

        return entries;
    }

    private List<AIContextEntry> extractEntries(CompilationUnit cu, Path filePath) {
        List<AIContextEntry> entries = new ArrayList<>();
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        // Extract from classes (architectural level)
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            String className = cls.getNameAsString();
            cls.getJavadocComment().ifPresent(javadoc -> {
                List<TagData> tags = extractTags(javadoc.getContent());
                for (TagData tag : tags) {
                    entries.add(new AIContextEntry(
                            packageName + "." + className,
                            filePath.toString(),
                            AIContextEntry.Level.ARCHITECTURAL,
                            tag.type,
                            tag.content,
                            tag.timestamp,
                            cls.getBegin().map(pos -> pos.line).orElse(0)));
                }
            });
        });

        // Extract from methods (implementation level)
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            String methodName = method.getNameAsString();
            String className = method.findAncestor(ClassOrInterfaceDeclaration.class)
                    .map(cls -> cls.getNameAsString())
                    .orElse("Unknown");

            method.getJavadocComment().ifPresent(javadoc -> {
                List<TagData> tags = extractTags(javadoc.getContent());
                for (TagData tag : tags) {
                    entries.add(new AIContextEntry(
                            packageName + "." + className + "." + methodName + "()",
                            filePath.toString(),
                            AIContextEntry.Level.IMPLEMENTATION,
                            tag.type,
                            tag.content,
                            tag.timestamp,
                            method.getBegin().map(pos -> pos.line).orElse(0)));
                }
            });
        });

        return entries;
    }

    private List<TagData> extractTags(String javadocContent) {
        List<TagData> tags = new ArrayList<>();
        Matcher matcher = TAG_PATTERN.matcher(javadocContent);

        while (matcher.find()) {
            String type = matcher.group(1);
            String content = matcher.group(2).trim();

            // Extract timestamp if present
            String timestamp = null;
            Matcher dateMatcher = DATE_PATTERN.matcher(content);
            if (dateMatcher.find()) {
                timestamp = dateMatcher.group(1);
                content = content.replaceFirst("\\[" + timestamp + "\\]\\s*", "");
            }

            tags.add(new TagData(type, content, timestamp));
        }

        return tags;
    }

    /**
     * Validates that classes with @aicontext-graph document all project
     * dependencies
     * (or list them in @aicontext-graph-ignore). Lenient: warns if graph documents
     * unused types.
     */
    private void validateGraphDocumentation(List<AIContextEntry> entries) throws MojoExecutionException {
        List<AIContextEntry> graphEntries = entries.stream()
                .filter(e -> e.level == AIContextEntry.Level.ARCHITECTURAL && "graph".equals(e.type))
                .collect(Collectors.toList());
        if (graphEntries.isEmpty())
            return;

        Map<String, String> ignoreByLocation = entries.stream()
                .filter(e -> e.level == AIContextEntry.Level.ARCHITECTURAL && "graph-ignore".equals(e.type))
                .collect(Collectors.toMap(AIContextEntry::getLocation, e -> e.content, (a, b) -> a));

        Set<String> projectClasses = new HashSet<>();
        JavaParser parser = new JavaParser();
        try {
            Files.walk(sourceDir.toPath())
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            CompilationUnit cu = parser.parse(path).getResult().orElse(null);
                            if (cu != null) {
                                projectClasses.addAll(ClassDependencyAnalyzer.getClassSimpleNamesInCu(cu));
                            }
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException e) {
            getLog().warn("Could not build project class set for graph validation: " + e.getMessage());
            return;
        }

        List<String> errors = new ArrayList<>();
        for (AIContextEntry graphEntry : graphEntries) {
            String location = graphEntry.getLocation();
            String className = location.contains(".") ? location.substring(location.lastIndexOf('.') + 1) : location;
            Path path = Paths.get(graphEntry.filePath);
            if (!Files.isRegularFile(path) && project != null && project.getBasedir() != null) {
                path = project.getBasedir().toPath().resolve(graphEntry.filePath);
            }
            if (!Files.isRegularFile(path)) {
                getLog().debug("Skipping graph validation for " + location + ": file not found " + path);
                continue;
            }
            CompilationUnit cu;
            try {
                cu = parser.parse(path).getResult().orElse(null);
            } catch (IOException e) {
                getLog().warn("Could not parse " + path + " for graph validation: " + e.getMessage());
                continue;
            }
            if (cu == null)
                continue;
            var optCls = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(c -> className.equals(c.getNameAsString()))
                    .findFirst();
            if (optCls.isEmpty())
                continue;

            Set<String> actual = ClassDependencyAnalyzer.findUsedProjectTypes(cu, optCls.get(), projectClasses);
            Set<String> documented = GraphNotationParser.getDocumentedUses(graphEntry.content, className);
            Set<String> ignoreSet = parseGraphIgnore(ignoreByLocation.getOrDefault(location, ""));

            for (String dep : actual) {
                if (!documented.contains(dep) && !ignoreSet.contains(dep)) {
                    errors.add(String.format(
                            "%s: Class dependency '%s' found but not in graph. Add to @aicontext-graph or @aicontext-graph-ignore.",
                            graphEntry.filePath + ":" + graphEntry.lineNumber, dep));
                }
            }
            for (String doc : documented) {
                if (!actual.contains(doc) && !ignoreSet.contains(doc)) {
                    getLog().warn(graphEntry.filePath + ":" + graphEntry.lineNumber +
                            " Graph documents '" + doc + "' but code does not use it (lenient).");
                }
            }
        }
        if (!errors.isEmpty()) {
            for (String err : errors) {
                getLog().error(err);
            }
            throw new MojoExecutionException(
                    "Graph validation failed: dependency found but not documented. See errors above.");
        }
    }

    private static Set<String> parseGraphIgnore(String content) {
        if (content == null || content.isBlank())
            return Set.of();
        Set<String> set = new HashSet<>();
        for (String s : content.split(",")) {
            String t = s.trim();
            if (!t.isEmpty())
                set.add(t);
        }
        return set;
    }

    private void generateAssistantDocs(String assistant, List<AIContextEntry> entries)
            throws IOException {
        // Try to load configuration first
        ScaffoldingConfig config = loadScaffoldingConfig(assistant);

        if (config != null) {
            // Use configuration-driven generation
            generateFromConfig(config, entries);
        } else {
            // Fall back to hardcoded methods (backward compatibility)
            getLog().debug("No config found for " + assistant + ", using hardcoded generation");
            switch (assistant.toLowerCase()) {
                case "claude":
                    generateClaudeDocs(entries);
                    break;
                case "cursor":
                    generateCursorDocs(entries);
                    break;
                case "copilot":
                    generateCopilotDocs(entries);
                    break;
                default:
                    getLog().warn("Unknown assistant: " + assistant);
            }
        }
    }

    private ScaffoldingConfig loadScaffoldingConfig(String assistant) {
        try {
            // Try user config first (if project is available)
            if (project != null && project.getBasedir() != null) {
                Path userConfigDir = project.getBasedir().toPath()
                        .resolve("src/main/resources/aicontext/scaffolding");
                Path userConfig = userConfigDir.resolve(assistant + ".yaml");

                if (Files.exists(userConfig)) {
                    getLog().debug("Loading user config: " + userConfig);
                    return ScaffoldingConfigLoader.load(userConfig);
                }

                // Try .aicontext directory
                Path aicontextConfig = project.getBasedir().toPath()
                        .resolve(".aicontext/scaffolding")
                        .resolve(assistant + ".yaml");
                if (Files.exists(aicontextConfig)) {
                    getLog().debug("Loading config from .aicontext: " + aicontextConfig);
                    return ScaffoldingConfigLoader.load(aicontextConfig);
                }
            }

            // Fall back to classpath resource
            String resourcePath = "/scaffolding/" + assistant + ".yaml";
            getLog().debug("Loading default config from classpath: " + resourcePath);
            return ScaffoldingConfigLoader.loadFromResource(resourcePath);
        } catch (IOException e) {
            getLog().debug("Could not load config for " + assistant + ": " + e.getMessage());
            return null;
        }
    }

    private void generateFromConfig(ScaffoldingConfig config, List<AIContextEntry> entries)
            throws IOException {
        // Resolve output directory using the new logic
        Path assistantDir = resolveOutputDir(config.getAssistant(), config);
        Files.createDirectories(assistantDir);

        // Setup template engine
        Path userTemplateDir = null;
        Path defaultTemplateDir = null;

        if (project != null && project.getBasedir() != null) {
            userTemplateDir = project.getBasedir().toPath()
                    .resolve("src/main/resources/aicontext/templates");
            defaultTemplateDir = project.getBasedir().toPath()
                    .resolve("src/main/resources/scaffolding/templates");
        }

        // If default template dir doesn't exist in project, we'll use classpath
        Path effectiveDefaultDir = (defaultTemplateDir != null && Files.exists(defaultTemplateDir))
                ? defaultTemplateDir
                : null;

        TemplateLoader templateLoader = new TemplateLoader(
                (userTemplateDir != null && Files.exists(userTemplateDir)) ? userTemplateDir : null,
                effectiveDefaultDir,
                getLog());
        TemplateEngine templateEngine = new MustacheTemplateEngine(templateLoader);

        // Build base context
        Map<String, Object> baseContext = buildBaseContext(entries, config);

        // Generate each file
        for (FileDefinition fileDef : config.getFiles()) {
            generateFile(assistantDir, fileDef, entries, baseContext, templateEngine);
        }
    }

    private void generateFile(Path outputDir, FileDefinition fileDef,
            List<AIContextEntry> allEntries, Map<String, Object> baseContext,
            TemplateEngine templateEngine) throws IOException {
        // Per-entry generation (e.g. .cursor/rules/*.md)
        if (Boolean.TRUE.equals(fileDef.getPerEntry()) && fileDef.getEntryFilename() != null) {
            generateFilePerEntry(outputDir, fileDef, allEntries, baseContext, templateEngine);
            return;
        }

        // Apply filters
        List<AIContextEntry> filteredEntries = filterEntries(allEntries, fileDef.getFilter());

        // Apply sorting
        if (fileDef.getSort() != null) {
            filteredEntries = sortEntries(filteredEntries, fileDef.getSort());
        }

        // Apply limit
        if (fileDef.getLimit() != null && fileDef.getLimit() > 0) {
            filteredEntries = filteredEntries.stream()
                    .limit(fileDef.getLimit())
                    .collect(Collectors.toList());
        }

        // Build template context
        Map<String, Object> context = new HashMap<>(baseContext);
        context.put("entries", prepareEntriesForTemplate(filteredEntries));
        context.put("filteredEntries", prepareEntriesForTemplate(filteredEntries));

        // Group entries if needed
        if (fileDef.getGroupBy() != null) {
            Object groupedData = groupEntries(filteredEntries, fileDef.getGroupBy());
            context.put("groupedEntries", groupedData);
        }

        // Add file-specific context
        if (fileDef.getContext() != null) {
            context.putAll(fileDef.getContext());
        }

        // Add other files for cross-references
        List<Map<String, String>> files = new ArrayList<>();
        for (FileDefinition otherFile : baseContext.get("allFiles") != null
                ? (List<FileDefinition>) baseContext.get("allFiles")
                : List.<FileDefinition>of()) {
            Map<String, String> fileInfo = new HashMap<>();
            fileInfo.put("name", substituteVariables(otherFile.getName()));
            fileInfo.put("description", otherFile.getDescription() != null ? otherFile.getDescription() : "");
            files.add(fileInfo);
        }
        context.put("files", files);

        // Special handling for cursor/copilot/bob templates
        if (fileDef.getTemplate().contains("cursorrules")) {
            prepareCursorContext(context, filteredEntries);
        } else if (fileDef.getTemplate().contains("instructions")) {
            prepareCopilotContext(context, filteredEntries);
        } else if (fileDef.getTemplate().contains("bob")) {
            prepareBobContext(context, filteredEntries, allEntries);
        }

        // Render template
        String content = templateEngine.render(fileDef.getTemplate(), context);

        // Substitute variables in filename and resolve path
        String fileName = substituteVariables(fileDef.getName());
        Path outputFile = outputDir.resolve(fileName);

        // Create parent directories if needed (for nested paths like
        // .Bob/rules-{slug}/)
        Files.createDirectories(outputFile.getParent());

        // Write with signature marker to identify plugin-generated files
        writeSignedFile(outputFile, content);
        getLog().debug("Generated: " + outputFile);
    }

    /**
     * Generates one file per filtered entry (e.g. .cursor/rules/*.md with frontmatter).
     */
    private void generateFilePerEntry(Path outputDir, FileDefinition fileDef,
            List<AIContextEntry> allEntries, Map<String, Object> baseContext,
            TemplateEngine templateEngine) throws IOException {
        List<AIContextEntry> filteredEntries = filterEntries(allEntries, fileDef.getFilter());
        if (fileDef.getSort() != null) {
            filteredEntries = sortEntries(filteredEntries, fileDef.getSort());
        }
        if (fileDef.getLimit() != null && fileDef.getLimit() > 0) {
            filteredEntries = filteredEntries.stream()
                    .limit(fileDef.getLimit())
                    .collect(Collectors.toList());
        }

        Path rulesDir = outputDir.resolve(substituteVariables(fileDef.getName()));
        Files.createDirectories(rulesDir);

        Map<String, Integer> indexByKey = new HashMap<>();

        for (AIContextEntry entry : filteredEntries) {
            Map<String, Object> entryMap = entryToMap(entry);
            String locationSlug = locationToSlug(entry.location);
            String key = entry.type + ":" + locationSlug;
            int index = indexByKey.merge(key, 1, Integer::sum);

            Map<String, Object> context = new HashMap<>(baseContext);
            context.put("entry", entryMap);
            context.put("locationSlug", locationSlug);
            context.put("index", index);
            context.put("type", entry.type);
            String desc = truncate(normalizeJavadocContent(entry.content), 80);
            context.put("description", desc != null ? desc.replace("\"", "\\\"") : "");
            context.put("alwaysApply", entry.level == AIContextEntry.Level.ARCHITECTURAL);
            context.put("globs", List.of());

            String content = templateEngine.render(fileDef.getTemplate(), context);
            String fileName = substituteEntryFilename(fileDef.getEntryFilename(), entry.type, locationSlug, index);
            Path outputFile = rulesDir.resolve(fileName);
            writeSignedFile(outputFile, content);
            getLog().debug("Generated: " + outputFile);
        }
    }


    private static String locationToSlug(String location) {
        if (location == null) return "";
        return location.replace(".", "-").replace("()", "").replace(" ", "-")
                .replaceAll("[^a-zA-Z0-9_-]", "");
    }

    private static String substituteEntryFilename(String pattern, String type, String locationSlug, int index) {
        if (pattern == null) return type + "-" + locationSlug + (index > 1 ? "-" + index : "") + ".md";
        String s = pattern
                .replace("{{type}}", type)
                .replace("{{locationSlug}}", locationSlug)
                .replace("{{index}}", String.valueOf(index));
        if (index > 1 && s.endsWith(".md")) {
            s = s.substring(0, s.length() - 3) + "-" + index + ".md";
        }
        return s;
    }

    /**
     * Get the appropriate signature marker for a file based on its extension/type.
     */
    private String getSignatureForFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".yaml") || lowerName.endsWith(".yml") || lowerName.equals(".bobmodes")) {
            return YAML_SIGNATURE;
        }
        // Default to Markdown/HTML comment style (works for .md, .cursorrules, etc.)
        return MARKDOWN_SIGNATURE;
    }

    /**
     * Write content to a file with the plugin signature marker prepended.
     */
    /**
     * Write content to a file with the plugin signature marker prepended.
     * If the file already exists and contains custom content, preserve it.
     */
    private void writeSignedFile(Path file, String content) throws IOException {
        String fileName = file.getFileName().toString();
        String signature = getSignatureForFile(fileName);
        String customSection = getCustomSectionForFile(fileName);

        // Check if file exists and has custom content to preserve
        String preservedCustomContent = "";
        if (Files.exists(file)) {
            preservedCustomContent = extractCustomContent(file);
        }

        // Build the final content: signature + generated content + custom section +
        // preserved content
        StringBuilder finalContent = new StringBuilder();
        finalContent.append(signature).append("\n");
        finalContent.append(content);
        finalContent.append(customSection);
        finalContent.append(preservedCustomContent);

        Files.writeString(file, finalContent.toString());
    }

    /**
     * Get the appropriate custom section marker for a file based on its
     * extension/type.
     */
    private String getCustomSectionForFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".yaml") || lowerName.endsWith(".yml") || lowerName.equals(".bobmodes")) {
            return YAML_CUSTOM_SECTION;
        }
        return MARKDOWN_CUSTOM_SECTION;
    }

    /**
     * Extract custom content from an existing file (content below the custom
     * section marker).
     */
    private String extractCustomContent(Path file) {
        try {
            String existingContent = Files.readString(file);
            int markerIndex = existingContent.indexOf(CUSTOM_SECTION_MARKER);
            if (markerIndex == -1) {
                return "";
            }

            // Find the end of the marker line
            int lineEnd = existingContent.indexOf('\n', markerIndex);
            if (lineEnd == -1) {
                return "";
            }

            // Return everything after the marker line
            String customContent = existingContent.substring(lineEnd + 1);

            // Skip the default custom section template if present (user hasn't modified it)
            if (customContent.trim().startsWith("## Custom Notes") &&
                    customContent.contains("_Add your own project-specific notes")) {
                // Check if there's actual user content after the template
                int templateEnd = customContent.indexOf("_Add your own project-specific notes");
                if (templateEnd != -1) {
                    int lineAfterTemplate = customContent.indexOf('\n', templateEnd);
                    if (lineAfterTemplate != -1) {
                        String afterTemplate = customContent.substring(lineAfterTemplate + 1).trim();
                        if (afterTemplate.isEmpty()) {
                            return ""; // No user content, skip preserving
                        }
                        // User added content after the template - preserve everything
                        return customContent;
                    }
                }
                return "";
            }

            // For YAML, skip the default comment template
            if (customContent.trim().startsWith("# Add your own customizations below:")) {
                int lineAfterComment = customContent.indexOf('\n');
                if (lineAfterComment != -1) {
                    String afterComment = customContent.substring(lineAfterComment + 1).trim();
                    if (afterComment.isEmpty()) {
                        return "";
                    }
                    return customContent;
                }
                return "";
            }

            return customContent;
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Substitute Maven properties and project variables in a string.
     */
    private String substituteVariables(String input) {
        if (input == null)
            return null;

        String result = input;

        // Handle null project (test environment)
        String artifactId = (project != null) ? project.getArtifactId() : "unknown";
        String groupId = (project != null) ? project.getGroupId() : "unknown";
        String version = (project != null) ? project.getVersion() : "1.0.0";

        // Substitute ${project.artifactId}
        result = result.replace("${project.artifactId}", artifactId);

        // Substitute ${projectName}
        String projName = projectName != null ? projectName : artifactId;
        result = result.replace("${projectName}", projName);

        // Substitute ${project.groupId}
        result = result.replace("${project.groupId}", groupId);

        // Substitute ${project.version}
        result = result.replace("${project.version}", version);

        return result;
    }

    private List<AIContextEntry> filterEntries(List<AIContextEntry> entries,
            FilterDefinition filter) {
        if (filter == null) {
            return entries;
        }
        return entries.stream()
                .filter(entry -> filter.matches(entry))
                .collect(Collectors.toList());
    }

    private List<AIContextEntry> sortEntries(List<AIContextEntry> entries,
            SortDefinition sort) {
        Comparator<AIContextEntry> comparator = switch (sort.getField()) {
            case "timestamp" -> Comparator.comparing(
                    e -> e.timestamp != null ? e.timestamp : "9999-99-99");
            case "priority" -> Comparator.comparingInt(AIContextEntry::getPriority);
            case "location" -> Comparator.comparing(e -> e.location);
            case "lineNumber" -> Comparator.comparingInt(e -> e.lineNumber);
            default -> Comparator.comparingInt(AIContextEntry::getPriority);
        };

        if ("desc".equals(sort.getOrder())) {
            comparator = comparator.reversed();
        }

        return entries.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    private Object groupEntries(List<AIContextEntry> entries, String groupBy) {
        if (groupBy == null) {
            return null;
        }

        switch (groupBy) {
            case "package":
                return entries.stream()
                        .collect(Collectors.groupingBy(e -> {
                            int lastDot = e.location.lastIndexOf('.');
                            return lastDot > 0 ? e.location.substring(0, lastDot) : "";
                        }))
                        .entrySet().stream()
                        .map(entry -> {
                            Map<String, Object> group = new LinkedHashMap<>();
                            group.put("package", entry.getKey());
                            group.put("entries", prepareEntriesForTemplate(entry.getValue()));
                            return group;
                        })
                        .collect(Collectors.toList());
            case "type":
                return entries.stream()
                        .collect(Collectors.groupingBy(e -> e.type))
                        .entrySet().stream()
                        .map(entry -> {
                            Map<String, Object> group = new LinkedHashMap<>();
                            group.put("type", entry.getKey());
                            group.put("entries", prepareEntriesForTemplate(entry.getValue()));
                            return group;
                        })
                        .collect(Collectors.toList());
            case "level":
                return entries.stream()
                        .collect(Collectors.groupingBy(e -> e.level.name()))
                        .entrySet().stream()
                        .map(entry -> {
                            Map<String, Object> group = new LinkedHashMap<>();
                            String levelName = entry.getKey();
                            group.put("groupName",
                                    levelName.equals("ARCHITECTURAL") ? "Architectural Rules (Class-level)"
                                            : "Implementation Rules (Method-level)");
                            group.put("entries", prepareEntriesForTemplate(entry.getValue()));
                            return group;
                        })
                        .collect(Collectors.toList());
            default:
                return null;
        }
    }

    private List<Map<String, Object>> prepareEntriesForTemplate(List<AIContextEntry> entries) {
        return entries.stream().map(entry -> {
            Map<String, Object> map = new HashMap<>();
            map.put("location", entry.location);
            map.put("filePath", entry.filePath);
            map.put("level", entry.level.name());
            map.put("type", entry.type);
            map.put("typeUpperCase", entry.type.toUpperCase());
            map.put("typeLabel", entry.type.substring(0, 1).toUpperCase() + entry.type.substring(1));
            map.put("content", entry.content);
            map.put("timestamp", entry.timestamp);
            map.put("lineNumber", entry.lineNumber);

            // Extract package, class, and method names
            extractLocationComponents(map, entry.location);

            // Also set 'package' for backward compatibility
            map.put("package", map.get("packageName"));

            // Truncated content for index (preview)
            map.put("truncatedContent", truncate(entry.content, 100));
            map.put("preview", truncate(entry.content, 100));

            // Priority and categorization
            map.put("priority", calculateEntryPriority(entry));
            map.put("isSecurityRelated", isSecurityRelated(entry.content));
            map.put("isBusinessCritical", isBusinessCritical(entry.content));
            map.put("isArchitectural", entry.level == AIContextEntry.Level.ARCHITECTURAL);
            map.put("isImplementation", entry.level == AIContextEntry.Level.IMPLEMENTATION);

            // Parsed graph nodes for @aicontext-graph (relationship notation)
            if ("graph".equals(entry.type)) {
                List<GraphNode> graphNodes = GraphNotationParser.parseBlocks(entry.content);
                map.put("graphNodes", graphNodesToMaps(graphNodes));
                map.put("hasGraphNodes", !graphNodes.isEmpty());
                if (graphNodes.size() == 1) {
                    map.put("graphNode", graphNodeToMap(graphNodes.get(0)));
                    map.put("graphEdges", graphEdgesToMaps(graphNodes.get(0).getEdges()));
                }
            }

            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> graphNodesToMaps(List<GraphNode> nodes) {
        return nodes.stream().map(this::graphNodeToMap).collect(Collectors.toList());
    }

    private Map<String, Object> graphNodeToMap(GraphNode node) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", node.getName());
        m.put("edges", graphEdgesToMaps(node.getEdges()));
        return m;
    }

    private List<Map<String, Object>> graphEdgesToMaps(List<GraphEdge> edges) {
        return edges.stream().map(edge -> {
            Map<String, Object> m = new HashMap<>();
            m.put("relationType", edge.getRelationType());
            m.put("direction", edge.getDirection().name());
            m.put("targets", edge.getTargets());
            m.put("targetsList", edge.getTargets());
            m.put("targetsJoined", String.join(", ", edge.getTargets()));
            m.put("isOutbound", edge.isOutbound());
            m.put("isInbound", edge.isInbound());
            return m;
        }).collect(Collectors.toList());
    }

    private void prepareCursorContext(Map<String, Object> context,
            List<AIContextEntry> entries) {
        int ruleLimit = context.containsKey("ruleLimit") ? (Integer) context.get("ruleLimit") : 20;
        int decisionLimit = context.containsKey("decisionLimit") ? (Integer) context.get("decisionLimit") : 15;

        // Architectural rules only, with limit
        List<Map<String, Object>> archRules = entries.stream()
                .filter(e -> e.level == AIContextEntry.Level.ARCHITECTURAL &&
                        e.type.equals("rule"))
                .sorted(Comparator.comparingInt(this::calculateEntryPriority).reversed())
                .limit(ruleLimit)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("location", e.location);
                    m.put("content", e.content);
                    return m;
                })
                .collect(Collectors.toList());
        context.put("architecturalRules", archRules);
        context.put("hasArchitecturalRules", !archRules.isEmpty());

        // Decisions with limit, sorted by priority (recent and important first)
        List<Map<String, Object>> decisions = entries.stream()
                .filter(e -> e.type.equals("decision"))
                .sorted(Comparator.comparingInt(this::calculateEntryPriority).reversed())
                .limit(decisionLimit)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("location", e.location);
                    m.put("content", e.content);
                    m.put("timestamp", e.timestamp);
                    return m;
                })
                .collect(Collectors.toList());
        context.put("decisions", decisions);
        context.put("hasDecisions", !decisions.isEmpty());
    }

    /**
     * Calculate priority for an entry including security/business boosts and
     * recency.
     */
    private int calculateEntryPriority(AIContextEntry entry) {
        int basePriority = entry.level == AIContextEntry.Level.ARCHITECTURAL ? 100 : 50;

        int typePriority = switch (entry.type) {
            case "rule" -> 20;
            case "decision" -> 15;
            case "graph" -> 18;
            case "context" -> 10;
            default -> 0;
        };

        // Boost for security-related content
        int securityBoost = isSecurityRelated(entry.content) ? 30 : 0;

        // Boost for business-critical content
        int businessBoost = isBusinessCritical(entry.content) ? 25 : 0;

        // Recency boost for decisions
        int recencyBoost = calculateRecencyBoost(entry.timestamp);

        return basePriority + typePriority + securityBoost + businessBoost + recencyBoost;
    }

    private boolean isSecurityRelated(String content) {
        if (content == null)
            return false;
        String lowerContent = content.toLowerCase();
        return SECURITY_KEYWORDS.stream().anyMatch(lowerContent::contains);
    }

    private boolean isBusinessCritical(String content) {
        if (content == null)
            return false;
        String lowerContent = content.toLowerCase();
        return BUSINESS_KEYWORDS.stream().anyMatch(lowerContent::contains);
    }

    private int calculateRecencyBoost(String timestamp) {
        if (timestamp == null)
            return 0;
        try {
            LocalDate date = LocalDate.parse(timestamp);
            long daysAgo = ChronoUnit.DAYS.between(date, LocalDate.now());
            if (daysAgo < 30)
                return 20; // Recent
            if (daysAgo < 90)
                return 10; // Moderately recent
            return 0; // Old
        } catch (Exception e) {
            return 0;
        }
    }

    private void prepareCopilotContext(Map<String, Object> context,
            List<AIContextEntry> entries) {
        int ruleLimit = context.containsKey("ruleLimit") ? (Integer) context.get("ruleLimit") : 30;
        int decisionLimit = context.containsKey("decisionLimit") ? (Integer) context.get("decisionLimit") : 15;

        List<Map<String, Object>> rules = entries.stream()
                .filter(e -> e.type.equals("rule"))
                .limit(ruleLimit)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("location", e.location);
                    m.put("content", e.content);
                    m.put("filePath", e.filePath);
                    m.put("lineNumber", e.lineNumber);
                    m.put("level", e.level.name());
                    return m;
                })
                .collect(Collectors.toList());
        context.put("rules", rules);
        context.put("hasRules", !rules.isEmpty());

        List<Map<String, Object>> decisions = entries.stream()
                .filter(e -> e.type.equals("decision"))
                .limit(decisionLimit)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("location", e.location);
                    m.put("content", e.content);
                    m.put("timestamp", e.timestamp);
                    m.put("filePath", e.filePath);
                    m.put("lineNumber", e.lineNumber);
                    return m;
                })
                .collect(Collectors.toList());
        context.put("decisions", decisions);
        context.put("hasDecisions", !decisions.isEmpty());

        List<AIContextEntry> graphEntries = entries.stream()
                .filter(e -> e.type.equals("graph"))
                .collect(Collectors.toList());
        List<Map<String, Object>> graphEntriesForTemplate = prepareEntriesForTemplate(graphEntries);
        context.put("graphEntries", graphEntriesForTemplate);
        context.put("hasGraphEntries", !graphEntriesForTemplate.isEmpty());
    }

    private void prepareBobContext(Map<String, Object> context,
            List<AIContextEntry> filteredEntries, List<AIContextEntry> allEntries) {
        // Handle null project (test environment)
        String artifactId = (project != null) ? project.getArtifactId() : "unknown";

        // Add Bob-specific context variables
        context.put("projectArtifactId", artifactId);
        context.put("modeSlug", artifactId + "-mode");

        // Architectural rules
        List<Map<String, Object>> archRules = allEntries.stream()
                .filter(e -> e.level == AIContextEntry.Level.ARCHITECTURAL && e.type.equals("rule"))
                .map(this::entryToMap)
                .collect(Collectors.toList());
        context.put("architecturalRules", archRules);
        context.put("hasArchitecturalRules", !archRules.isEmpty());

        // Implementation rules
        List<Map<String, Object>> implRules = allEntries.stream()
                .filter(e -> e.level == AIContextEntry.Level.IMPLEMENTATION && e.type.equals("rule"))
                .map(this::entryToMap)
                .collect(Collectors.toList());
        context.put("implementationRules", implRules);
        context.put("hasImplementationRules", !implRules.isEmpty());

        // Decisions (sorted by timestamp desc)
        List<Map<String, Object>> decisions = allEntries.stream()
                .filter(e -> e.type.equals("decision"))
                .sorted(Comparator.comparing((AIContextEntry e) -> e.timestamp != null ? e.timestamp : "0000-00-00")
                        .reversed())
                .map(this::entryToMap)
                .collect(Collectors.toList());
        context.put("decisions", decisions);
        context.put("hasDecisions", !decisions.isEmpty());

        // Context entries (flat, for backward compatibility)
        List<Map<String, Object>> contextEntries = allEntries.stream()
                .filter(e -> e.type.equals("context"))
                .map(this::entryToMap)
                .collect(Collectors.toList());
        context.put("contextEntries", contextEntries);
        context.put("hasContextEntries", !contextEntries.isEmpty());

        // Grouped by location for compact context-notes: one section per class with bullet list
        List<Map<String, Object>> groupedContextEntries = allEntries.stream()
                .filter(e -> e.type.equals("context"))
                .collect(Collectors.groupingBy(e -> e.location))
                .entrySet().stream()
                .map(entry -> {
                    List<AIContextEntry> entries = entry.getValue();
                    Map<String, Object> group = new LinkedHashMap<>();
                    group.put("location", entry.getKey());
                    AIContextEntry first = entries.get(0);
                    group.put("filePath", first.filePath);
                    group.put("lineNumber", first.lineNumber);
                    group.put("items", entries.stream()
                            .map(e -> Map.<String, Object>of("content", normalizeJavadocContent(e.content)))
                            .collect(Collectors.toList()));
                    return group;
                })
                .collect(Collectors.toList());
        context.put("groupedContextEntries", groupedContextEntries);

        // Statistics for role definition
        context.put("totalRules", archRules.size() + implRules.size());
        context.put("totalDecisions", decisions.size());
        context.put("totalContext", contextEntries.size());
    }

    private Map<String, Object> entryToMap(AIContextEntry entry) {
        Map<String, Object> m = new HashMap<>();
        String content = normalizeJavadocContent(entry.content);
        m.put("content", content);
        m.put("location", entry.location);
        m.put("filePath", entry.filePath);
        m.put("lineNumber", entry.lineNumber);
        m.put("level", entry.level.name());
        m.put("type", entry.type);
        m.put("typeUpperCase", entry.type.toUpperCase());
        m.put("timestamp", entry.timestamp);
        m.put("preview", truncate(content, 100));

        // Extract package and class
        extractLocationComponents(m, entry.location);

        return m;
    }

    private void extractLocationComponents(Map<String, Object> map, String location) {
        if (location == null || location.isEmpty()) {
            map.put("packageName", "");
            map.put("className", "");
            map.put("methodName", "");
            return;
        }

        // Handle method names with parentheses
        String cleanLocation = location.replaceAll("\\(\\)$", "");

        String[] parts = cleanLocation.split("\\.");
        if (parts.length >= 2) {
            // Last part is class or method name
            String lastName = parts[parts.length - 1];
            String secondLast = parts[parts.length - 2];

            // Check if second-to-last starts with uppercase (likely class name)
            if (Character.isUpperCase(secondLast.charAt(0))) {
                // This is a method: package.Class.method
                map.put("className", secondLast);
                map.put("methodName", lastName);
                map.put("packageName", String.join(".",
                        Arrays.copyOfRange(parts, 0, parts.length - 2)));
            } else if (Character.isUpperCase(lastName.charAt(0))) {
                // This is a class: package.Class
                map.put("className", lastName);
                map.put("methodName", "");
                map.put("packageName", String.join(".",
                        Arrays.copyOfRange(parts, 0, parts.length - 1)));
            } else {
                map.put("className", lastName);
                map.put("methodName", "");
                map.put("packageName", String.join(".",
                        Arrays.copyOfRange(parts, 0, parts.length - 1)));
            }
        } else {
            map.put("className", location);
            map.put("methodName", "");
            map.put("packageName", "");
        }
    }

    private Map<String, Object> buildBaseContext(List<AIContextEntry> entries,
            ScaffoldingConfig config) {
        Map<String, Object> context = new HashMap<>();

        // Handle null project (test environment)
        String artifactId = (project != null) ? project.getArtifactId() : "unknown";
        String groupId = (project != null) ? project.getGroupId() : "unknown";
        String version = (project != null) ? project.getVersion() : "1.0.0";

        String projName = projectName != null ? projectName : artifactId;
        context.put("projectName", projName);
        context.put("projectArtifactId", artifactId);
        context.put("projectGroupId", groupId);
        context.put("projectVersion", version);
        context.put("projectType", "Java/Maven Project");
        context.put("lastUpdated", LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // Mode slug for Bob
        context.put("modeSlug", artifactId + "-mode");

        // Statistics
        Map<String, Long> tagCounts = entries.stream()
                .collect(Collectors.groupingBy(e -> e.type, Collectors.counting()));

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", entries.size());
        stats.put("rules", tagCounts.getOrDefault("rule", 0L));
        stats.put("decisions", tagCounts.getOrDefault("decision", 0L));
        stats.put("graph", tagCounts.getOrDefault("graph", 0L));
        stats.put("context", tagCounts.getOrDefault("context", 0L));

        long architectural = entries.stream()
                .filter(e -> e.level == AIContextEntry.Level.ARCHITECTURAL)
                .count();
        stats.put("architectural", architectural);
        stats.put("implementation", entries.size() - architectural);

        // By-type breakdown for enhanced statistics
        Map<String, Long> byType = new HashMap<>();
        byType.put("rule", tagCounts.getOrDefault("rule", 0L));
        byType.put("decision", tagCounts.getOrDefault("decision", 0L));
        byType.put("graph", tagCounts.getOrDefault("graph", 0L));
        byType.put("context", tagCounts.getOrDefault("context", 0L));
        stats.put("byType", byType);

        // By-level breakdown
        Map<String, Long> byLevel = new HashMap<>();
        byLevel.put("architectural", architectural);
        byLevel.put("implementation", (long) (entries.size() - architectural));
        stats.put("byLevel", byLevel);

        context.put("statistics", stats);

        // Tag types with descriptions
        List<Map<String, String>> tagTypes = List.of(
                Map.of("name", "rule", "description", "Coding rules and constraints"),
                Map.of("name", "decision", "description", "Design decisions with rationale"),
                Map.of("name", "graph", "description",
                        "Relationship graph (uses, calls, db, events, by, external, config)"),
                Map.of("name", "context", "description", "Business context and requirements"));
        context.put("tagTypes", tagTypes);

        // Store all file definitions for cross-references
        if (config != null) {
            context.put("allFiles", config.getFiles());
        }

        // Pre-compute grouped entries for common access patterns
        // Architectural rules
        List<Map<String, Object>> archRules = entries.stream()
                .filter(e -> e.level == AIContextEntry.Level.ARCHITECTURAL && e.type.equals("rule"))
                .sorted(Comparator.comparingInt(this::calculateEntryPriority).reversed())
                .map(this::entryToMap)
                .collect(Collectors.toList());
        context.put("architecturalRules", archRules);
        context.put("hasArchitecturalRules", !archRules.isEmpty());

        // Implementation rules
        List<Map<String, Object>> implRules = entries.stream()
                .filter(e -> e.level == AIContextEntry.Level.IMPLEMENTATION && e.type.equals("rule"))
                .sorted(Comparator.comparingInt(this::calculateEntryPriority).reversed())
                .map(this::entryToMap)
                .collect(Collectors.toList());
        context.put("implementationRules", implRules);
        context.put("hasImplementationRules", !implRules.isEmpty());

        // All decisions sorted by timestamp
        List<Map<String, Object>> decisions = entries.stream()
                .filter(e -> e.type.equals("decision"))
                .sorted(Comparator.comparing((AIContextEntry e) -> e.timestamp != null ? e.timestamp : "0000-00-00")
                        .reversed())
                .map(this::entryToMap)
                .collect(Collectors.toList());
        context.put("decisions", decisions);
        context.put("hasDecisions", !decisions.isEmpty());

        return context;
    }

    private void generateClaudeDocs(List<AIContextEntry> entries) throws IOException {
        Path claudeDir = resolveOutputDir("claude", null);
        Files.createDirectories(claudeDir);

        // Generate CLAUDE.md (main entry point)
        generateClaudeMainFile(claudeDir, entries);

        // Generate ARCHITECTURE.md (class-level context)
        generateArchitectureFile(claudeDir, entries);

        // Generate DECISIONS.md (decision log)
        generateDecisionsFile(claudeDir, entries);

        // Generate RULES.md (coding rules)
        generateRulesFile(claudeDir, entries);

        // Generate TAG_INDEX.md (searchable index)
        generateTagIndexFile(claudeDir, entries);
    }

    private void generateClaudeMainFile(Path claudeDir, List<AIContextEntry> entries)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        String projName = projectName != null ? projectName : project.getArtifactId();

        sb.append("# ").append(projName).append(" - AI Context Guide\n\n");
        sb.append("**Project**: ").append(projName).append("\n");
        sb.append("**Type**: Java/Maven Project\n");
        sb.append("**Last Updated**: ").append(LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");

        sb.append("## Quick Start for AI Assistants\n\n");
        sb.append("This project uses `@aicontext-*` Javadoc tags to provide context:\n\n");

        sb.append("### Tag Types\n\n");
        sb.append("- `@aicontext-rule`: Coding rules and constraints\n");
        sb.append("- `@aicontext-decision`: Design decisions with rationale\n");
        sb.append("- `@aicontext-graph`: Relationship graph (uses, calls, db, events, by, external, config)\n");
        sb.append("- `@aicontext-context`: Business context and requirements\n\n");

        sb.append("### Priority Levels\n\n");
        sb.append("**ARCHITECTURAL** (Class-level): Affects overall design and structure\n");
        sb.append("**IMPLEMENTATION** (Method-level): Specific implementation details\n\n");

        sb.append("## Key Documentation Files\n\n");
        sb.append("- [ARCHITECTURE.md](./ARCHITECTURE.md) - Class-level architectural guidance\n");
        sb.append("- [DECISIONS.md](./DECISIONS.md) - All design decisions chronologically\n");
        sb.append("- [RULES.md](./RULES.md) - Coding rules and constraints\n");
        sb.append("- [TAG_INDEX.md](./TAG_INDEX.md) - Searchable index of all tags\n\n");

        // Summary statistics
        Map<String, Long> tagCounts = entries.stream()
                .collect(Collectors.groupingBy(e -> e.type, Collectors.counting()));

        sb.append("## Statistics\n\n");
        sb.append(String.format("- Total Entries: %d\n", entries.size()));
        sb.append(String.format("- Rules: %d\n", tagCounts.getOrDefault("rule", 0L)));
        sb.append(String.format("- Decisions: %d\n", tagCounts.getOrDefault("decision", 0L)));
        sb.append(String.format("- Context Notes: %d\n", tagCounts.getOrDefault("context", 0L)));

        long architectural = entries.stream()
                .filter(e -> e.level == AIContextEntry.Level.ARCHITECTURAL)
                .count();
        sb.append(String.format("- Architectural: %d\n", architectural));
        sb.append(String.format("- Implementation: %d\n", entries.size() - architectural));

        writeSignedFile(claudeDir.resolve("CLAUDE.md"), sb.toString());
    }

    private void generateArchitectureFile(Path claudeDir, List<AIContextEntry> entries)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Architectural Guidance\n\n");
        sb.append("Class-level design patterns and architectural decisions.\n\n");

        entries.stream()
                .filter(e -> e.level == AIContextEntry.Level.ARCHITECTURAL)
                .collect(Collectors.groupingBy(e -> e.location.substring(0,
                        e.location.lastIndexOf('.'))))
                .forEach((pkg, pkgEntries) -> {
                    sb.append("\n## ").append(pkg).append("\n\n");
                    pkgEntries.forEach(entry -> {
                        String className = entry.location.substring(
                                entry.location.lastIndexOf('.') + 1);
                        sb.append("### ").append(className).append("\n");
                        sb.append("**File**: `").append(entry.filePath).append(":")
                                .append(entry.lineNumber).append("`\n\n");
                        sb.append("**").append(entry.type.toUpperCase()).append("**: ");
                        sb.append(entry.content).append("\n\n");
                        if (entry.timestamp != null) {
                            sb.append("*Date: ").append(entry.timestamp).append("*\n\n");
                        }
                    });
                });

        writeSignedFile(claudeDir.resolve("ARCHITECTURE.md"), sb.toString());
    }

    private void generateDecisionsFile(Path claudeDir, List<AIContextEntry> entries)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Design Decisions Log\n\n");
        sb.append("Chronological record of all design decisions.\n\n");

        entries.stream()
                .filter(e -> e.type.equals("decision"))
                .sorted(Comparator.comparing((AIContextEntry e) -> e.timestamp != null ? e.timestamp : "9999-99-99")
                        .reversed())
                .forEach(entry -> {
                    sb.append("## ").append(entry.location).append("\n");
                    sb.append("**Level**: ").append(entry.level).append("\n");
                    sb.append("**File**: `").append(entry.filePath).append(":")
                            .append(entry.lineNumber).append("`\n");
                    if (entry.timestamp != null) {
                        sb.append("**Date**: ").append(entry.timestamp).append("\n");
                    }
                    sb.append("\n").append(entry.content).append("\n\n");
                    sb.append("---\n\n");
                });

        writeSignedFile(claudeDir.resolve("DECISIONS.md"), sb.toString());
    }

    private void generateRulesFile(Path claudeDir, List<AIContextEntry> entries)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Coding Rules and Constraints\n\n");
        sb.append("## Architectural Rules (Class-level)\n\n");

        entries.stream()
                .filter(e -> e.type.equals("rule") &&
                        e.level == AIContextEntry.Level.ARCHITECTURAL)
                .forEach(entry -> {
                    sb.append("### ").append(entry.location).append("\n");
                    sb.append(entry.content).append("\n\n");
                    sb.append("*Source: `").append(entry.filePath).append(":")
                            .append(entry.lineNumber).append("`*\n\n");
                });

        sb.append("\n## Implementation Rules (Method-level)\n\n");

        entries.stream()
                .filter(e -> e.type.equals("rule") &&
                        e.level == AIContextEntry.Level.IMPLEMENTATION)
                .forEach(entry -> {
                    sb.append("### ").append(entry.location).append("\n");
                    sb.append(entry.content).append("\n\n");
                    sb.append("*Source: `").append(entry.filePath).append(":")
                            .append(entry.lineNumber).append("`*\n\n");
                });

        writeSignedFile(claudeDir.resolve("RULES.md"), sb.toString());
    }

    private void generateTagIndexFile(Path claudeDir, List<AIContextEntry> entries)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Tag Index\n\n");
        sb.append("Searchable index of all @aicontext-* tags.\n\n");

        Map<String, List<AIContextEntry>> byType = entries.stream()
                .collect(Collectors.groupingBy(e -> e.type));

        byType.forEach((type, typeEntries) -> {
            sb.append("## @aicontext-").append(type).append("\n\n");
            typeEntries.forEach(entry -> {
                sb.append("- **").append(entry.location).append("** ");
                sb.append("[").append(entry.level).append("] ");
                sb.append("(`").append(entry.filePath).append(":")
                        .append(entry.lineNumber).append("`)");
                sb.append("\n  ").append(truncate(entry.content, 100)).append("\n\n");
            });
        });

        writeSignedFile(claudeDir.resolve("TAG_INDEX.md"), sb.toString());
    }

    private void generateCursorDocs(List<AIContextEntry> entries) throws IOException {
        Path cursorDir = resolveOutputDir("cursor", null);
        Files.createDirectories(cursorDir);

        // Cursor uses .cursorrules format
        StringBuilder sb = new StringBuilder();
        sb.append("# AI Context Rules for Cursor\n\n");

        // High-priority architectural rules first
        sb.append("## Architectural Constraints\n\n");
        entries.stream()
                .filter(e -> e.level == AIContextEntry.Level.ARCHITECTURAL &&
                        e.type.equals("rule"))
                .forEach(entry -> {
                    sb.append("- **").append(entry.location).append("**: ");
                    sb.append(entry.content).append("\n");
                });

        sb.append("\n## Design Decisions\n\n");
        entries.stream()
                .filter(e -> e.type.equals("decision"))
                .limit(20) // Top 20 most important
                .forEach(entry -> {
                    sb.append("- **").append(entry.location).append("**: ");
                    sb.append(entry.content);
                    if (entry.timestamp != null) {
                        sb.append(" [").append(entry.timestamp).append("]");
                    }
                    sb.append("\n");
                });

        writeSignedFile(cursorDir.resolve(".cursorrules"), sb.toString());
    }

    private void generateCopilotDocs(List<AIContextEntry> entries) throws IOException {
        Path copilotDir = resolveOutputDir("copilot", null);
        Files.createDirectories(copilotDir);

        // GitHub Copilot reads from .github/copilot-instructions.md
        StringBuilder sb = new StringBuilder();
        sb.append("# GitHub Copilot Instructions\n\n");
        sb.append("## Project-Specific Rules\n\n");

        entries.stream()
                .filter(e -> e.type.equals("rule"))
                .forEach(entry -> {
                    sb.append("### ").append(entry.location).append("\n");
                    sb.append(entry.content).append("\n\n");
                });

        sb.append("## Key Design Decisions\n\n");
        entries.stream()
                .filter(e -> e.type.equals("decision"))
                .limit(15)
                .forEach(entry -> {
                    sb.append("- ").append(entry.content);
                    if (entry.timestamp != null) {
                        sb.append(" (").append(entry.timestamp).append(")");
                    }
                    sb.append("\n");
                });

        writeSignedFile(copilotDir.resolve("copilot-instructions.md"), sb.toString());
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength) + "...";
    }

    /**
     * Normalizes Javadoc content: collapse continuation lines ("\n * " or "\n *") into a single space
     * so multi-line tag content renders as one line and does not show literal " * " in output.
     */
    private static String normalizeJavadocContent(String content) {
        if (content == null || content.isEmpty()) return content;
        return content.replaceAll("\\s*\\n\\s*\\*\\s*", " ").replaceAll("\\s+", " ").trim();
    }

    // Data classes
    private static class TagData {
        final String type;
        final String content;
        final String timestamp;

        TagData(String type, String content, String timestamp) {
            this.type = type;
            this.content = content;
            this.timestamp = timestamp;
        }
    }

    public static class AIContextEntry {
        public enum Level {
            ARCHITECTURAL, IMPLEMENTATION
        }

        final String location;
        final String filePath;
        final Level level;
        final String type;
        final String content;
        final String timestamp;
        final int lineNumber;

        AIContextEntry(String location, String filePath, Level level,
                String type, String content, String timestamp, int lineNumber) {
            this.location = location;
            this.filePath = filePath;
            this.level = level;
            this.type = type;
            this.content = content;
            this.timestamp = timestamp;
            this.lineNumber = lineNumber;
        }

        public String getLocation() {
            return location;
        }

        public String getFilePath() {
            return filePath;
        }

        public Level getLevel() {
            return level;
        }

        public String getType() {
            return type;
        }

        public String getContent() {
            return content;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        int getPriority() {
            int basePriority = level == Level.ARCHITECTURAL ? 100 : 50;
            int typePriority = switch (type) {
                case "rule" -> 20;
                case "decision" -> 15;
                case "context" -> 10;
                default -> 0;
            };
            return basePriority + typePriority;
        }
    }
}