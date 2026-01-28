package com.aicontext.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.aicontext.maven.graph.ClassDependencyAnalyzer;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

/**
 * Generates suggested @aicontext-graph content per class into target/suggested-graphs/
 * so developers can review and copy into Javadoc.
 * <p>
 * Run manually: {@code mvn aicontext:generate-graphs}
 */
@Mojo(name = "generate-graphs", requiresProject = true)
public class GenerateGraphsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "aicontext.sourceDir", defaultValue = "${project.basedir}/src/main/java")
    private File sourceDir;

    @Parameter(property = "aicontext.suggestedGraphsDir", defaultValue = "${project.basedir}/target/suggested-graphs")
    private File suggestedGraphsDir;

    private static final String HEADER = "# Suggested @aicontext-graph for this class.\n"
            + "# Review and copy the block below into class-level Javadoc.\n"
            + "# Add [calls], [db], [events], [by] as needed.\n\n";

    @Override
    public void execute() throws MojoExecutionException {
        if (!sourceDir.isDirectory()) {
            getLog().warn("Source directory does not exist: " + sourceDir);
            return;
        }

        getLog().info("AIContext: Generating suggested graphs...");

        try {
            Path sourcePath = sourceDir.toPath();
            JavaParser parser = new JavaParser();

            // Pass 1: collect all project class simple names
            Set<String> projectClasses = new HashSet<>();
            List<Path> javaFiles = Files.walk(sourcePath)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path path : javaFiles) {
                CompilationUnit cu = parser.parse(path).getResult().orElse(null);
                if (cu != null) {
                    projectClasses.addAll(ClassDependencyAnalyzer.getClassSimpleNamesInCu(cu));
                }
            }

            // Pass 2: for each class, compute used types and write suggested graph
            Path outDir = suggestedGraphsDir.toPath();
            Files.createDirectories(outDir);
            int count = 0;

            for (Path path : javaFiles) {
                CompilationUnit cu = parser.parse(path).getResult().orElse(null);
                if (cu == null) continue;

                for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                    String className = cls.getNameAsString();
                    Set<String> used = ClassDependencyAnalyzer.findUsedProjectTypes(cu, cls, projectClasses);
                    String content = formatSuggestedGraph(className, used);
                    Path outFile = outDir.resolve(className + ".txt");
                    Files.writeString(outFile, HEADER + content);
                    count++;
                }
            }

            getLog().info("AIContext: Wrote " + count + " suggested graph(s) to " + outDir);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate suggested graphs", e);
        }
    }

    private static String formatSuggestedGraph(String className, Set<String> used) {
        StringBuilder sb = new StringBuilder();
        sb.append(className).append("\n");
        if (used.isEmpty()) {
            sb.append("  # ├─[uses]→ (none detected)\n");
        } else {
            List<String> sorted = new ArrayList<>(used);
            sorted.sort(Comparator.naturalOrder());
            sb.append("  ├─[uses]→ ").append(String.join(", ", sorted)).append("\n");
        }
        sb.append("  # Add more edges: [calls], [db], [events], [by]←, [external], [config]\n");
        return sb.toString();
    }
}
