package com.aicontext.maven.graph;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses compact graph notation for relationship diagrams.
 * <p>
 * Syntax:
 * <pre>
 * NodeName
 *   ├─[relation]→ target1, target2
 *   ├─[calls]→ Service.method()
 *   └─[by]← Caller.method()
 * </pre>
 * Supported relation types (extensible): uses, calls, db, events, by, external, config.
 */
public final class GraphNotationParser {

    // Line: optional leading spaces, ├ or └, ─, [relation], → or ←, rest (targets)
    private static final Pattern EDGE_LINE = Pattern.compile(
            "\\s*[├└]─\\s*\\[([^]]+)\\]\\s*(→|←)\\s*(.+)",
            Pattern.UNICODE_CHARACTER_CLASS);

    /** Strip Javadoc line prefix (e.g. " * " or " *") so " *   └─[uses]→ X" parses as edge. */
    private static String stripJavadocPrefix(String line) {
        if (line == null) return "";
        String s = line.trim();
        if (s.startsWith("*")) {
            s = s.substring(1).trim();
        }
        return s;
    }

    /**
     * Parses a single graph block (one node and its edges).
     * Handles Javadoc-style lines (leading " * ") so content from @aicontext-graph parses correctly.
     *
     * @param block multi-line string: first line = node name, following lines = edges
     * @return parsed node, or empty node if block is invalid
     */
    public static GraphNode parseBlock(String block) {
        if (block == null || block.isBlank()) {
            return new GraphNode("", List.of());
        }

        String[] lines = block.split("\\r?\\n");
        String nodeName = null;
        List<GraphEdge> edges = new ArrayList<>();

        for (String line : lines) {
            String normalized = stripJavadocPrefix(line);
            if (normalized.isEmpty()) {
                continue;
            }

            Matcher edgeMatcher = EDGE_LINE.matcher(normalized);
            if (edgeMatcher.matches()) {
                String relation = edgeMatcher.group(1).trim();
                String arrow = edgeMatcher.group(2);
                String targetStr = edgeMatcher.group(3).trim();

                GraphEdge.Direction direction = "→".equals(arrow)
                        ? GraphEdge.Direction.OUTBOUND
                        : GraphEdge.Direction.INBOUND;

                List<String> targets = splitTargets(targetStr);

                edges.add(new GraphEdge(relation, direction, targets));
            } else if (nodeName == null) {
                // First non-empty, non-edge line = node name
                nodeName = normalized;
            }
        }

        if (nodeName == null) {
            nodeName = "";
        }
        return new GraphNode(nodeName, edges);
    }

    /**
     * Parses multiple graph blocks separated by blank lines or double newlines.
     * Each block defines one node.
     *
     * @param content full content (e.g. from @aicontext-graph tag)
     * @return list of parsed nodes
     */
    public static List<GraphNode> parseBlocks(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<GraphNode> nodes = new ArrayList<>();
        String[] blocks = content.split("\\n\\s*\\n");

        for (String block : blocks) {
            GraphNode node = parseBlock(block);
            if (!node.getName().isEmpty()) {
                nodes.add(node);
            }
        }

        return nodes;
    }

    /**
     * Returns the set of targets from the [uses] edge for the given node.
     * Used for validation: compare with actual dependencies.
     * If nodeName is null or no matching node, uses the first node in the content.
     *
     * @param graphContent full @aicontext-graph content
     * @param nodeName     class simple name to match (e.g. "PaymentService"), or null for first node
     * @return set of documented "uses" targets (simple names)
     */
    public static Set<String> getDocumentedUses(String graphContent, String nodeName) {
        List<GraphNode> nodes = parseBlocks(graphContent);
        if (nodes.isEmpty()) return Set.of();
        GraphNode node = null;
        if (nodeName != null && !nodeName.isEmpty()) {
            for (GraphNode n : nodes) {
                if (nodeName.equals(n.getName())) {
                    node = n;
                    break;
                }
            }
        }
        if (node == null) node = nodes.get(0);
        Set<String> uses = new LinkedHashSet<>();
        for (GraphEdge e : node.getEdges()) {
            if ("uses".equals(e.getRelationType()) && e.isOutbound()) {
                for (String t : e.getTargets()) {
                    uses.add(simpleName(t));
                }
            }
        }
        return uses;
    }

    private static String simpleName(String name) {
        if (name == null || name.isEmpty()) return "";
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).trim() : name.trim();
    }

    /**
     * Splits target string by comma, but not commas inside parentheses (e.g. db column lists).
     */
    private static List<String> splitTargets(String targetStr) {
        if (targetStr == null || targetStr.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < targetStr.length(); i++) {
            char c = targetStr.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                String part = targetStr.substring(start, i).trim();
                if (!part.isEmpty()) {
                    result.add(part);
                }
                start = i + 1;
            }
        }
        String last = targetStr.substring(start).trim();
        if (!last.isEmpty()) {
            result.add(last);
        }
        return result;
    }

    private GraphNotationParser() {
    }
}
