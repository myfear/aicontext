package com.aicontext.taglet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.UnknownBlockTagTree;

import jdk.javadoc.doclet.Taglet;

/**
 * Custom Javadoc taglet for AI coding assistant context.
 * 
 * Supports multiple tags per element:
 * - @aicontext-rule: Coding rules and constraints
 * - @aicontext-decision: Design decisions with rationale
 * - @aicontext-context: Business context and requirements
 * - @aicontext-graph: Relationship graph (uses, calls, db, events, by, external, config)
 * - @aicontext-graph-ignore: Intentional omission from graph validation (comma-separated class names)
 */
public class AIContextTaglet implements Taglet {

    private static final Map<String, Integer> TAG_PRIORITY = new HashMap<>();

    static {
        // Class-level tags have higher priority (architectural guidance)
        TAG_PRIORITY.put("aicontext-rule", 100);
        TAG_PRIORITY.put("aicontext-decision", 90);
        TAG_PRIORITY.put("aicontext-graph", 85);
        TAG_PRIORITY.put("aicontext-graph-ignore", 82);
        TAG_PRIORITY.put("aicontext-context", 80);
    }

    @Override
    public Set<Taglet.Location> getAllowedLocations() {
        return Set.of(
                Taglet.Location.TYPE,
                Taglet.Location.FIELD,
                Taglet.Location.CONSTRUCTOR,
                Taglet.Location.METHOD,
                Taglet.Location.OVERVIEW,
                Taglet.Location.PACKAGE);
    }

    @Override
    public boolean isInlineTag() {
        return false;
    }

    @Override
    public String getName() {
        return tagName;
    }

    /**
     * Registers all AI context tags.
     */
    public static void register(Map<String, Taglet> tagletMap) {
        AIContextTaglet ruleTag = new AIContextTaglet("aicontext-rule");
        AIContextTaglet decisionTag = new AIContextTaglet("aicontext-decision");
        AIContextTaglet graphTag = new AIContextTaglet("aicontext-graph");
        AIContextTaglet graphIgnoreTag = new AIContextTaglet("aicontext-graph-ignore");
        AIContextTaglet contextTag = new AIContextTaglet("aicontext-context");

        tagletMap.put(ruleTag.getName(), ruleTag);
        tagletMap.put(decisionTag.getName(), decisionTag);
        tagletMap.put(graphTag.getName(), graphTag);
        tagletMap.put(graphIgnoreTag.getName(), graphIgnoreTag);
        tagletMap.put(contextTag.getName(), contextTag);
    }

    private final String tagName;

    public AIContextTaglet(String tagName) {
        this.tagName = tagName;
    }

    @Override
    public String toString(List<? extends DocTree> tags, Element element) {
        if (tags.isEmpty()) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        result.append("<div class=\"aicontext-tags\">");

        for (DocTree tag : tags) {
            if (tag instanceof UnknownBlockTagTree) {
                result.append(formatTag((UnknownBlockTagTree) tag));
            }
        }

        result.append("</div>");
        return result.toString();
    }

    private String formatTag(UnknownBlockTagTree tag) {
        String type = tag.getTagName();
        String priority = getPriorityLabel(type);
        String content = tag.getContent().stream()
                .map(DocTree::toString)
                .reduce("", (a, b) -> a + b)
                .trim();

        return String.format(
                "<div class=\"aicontext-tag %s\">" +
                        "<strong>%s</strong> [Priority: %s]: %s" +
                        "</div>",
                type,
                formatTagName(type),
                priority,
                content);
    }

    private String formatTagName(String tagName) {
        return tagName.replace("aicontext-", "").toUpperCase();
    }

    private String getPriorityLabel(String tagName) {
        int priority = TAG_PRIORITY.getOrDefault(tagName, 0);
        if (priority >= 90)
            return "ARCHITECTURAL";
        if (priority >= 70)
            return "IMPLEMENTATION";
        return "INFORMATIONAL";
    }

    /**
     * Extracts all AI context data for processing.
     */
    public static class AIContextData {
        public enum Level {
            ARCHITECTURAL, // Class-level: affects overall design
            IMPLEMENTATION, // Method-level: specific implementation details
            INFORMATIONAL // General context and notes
        }

        public enum TagType {
            RULE,
            DECISION,
            CONTEXT
        }

        private final String location;
        private final Level level;
        private final TagType type;
        private final String content;
        private final String timestamp;

        public AIContextData(String location, Level level, TagType type,
                String content, String timestamp) {
            this.location = location;
            this.level = level;
            this.type = type;
            this.content = content;
            this.timestamp = timestamp;
        }

        public String getLocation() {
            return location;
        }

        public Level getLevel() {
            return level;
        }

        public TagType getType() {
            return type;
        }

        public String getContent() {
            return content;
        }

        public String getTimestamp() {
            return timestamp;
        }

        /**
         * Returns priority for sorting (higher = more important).
         */
        public int getPriority() {
            int basePriority = level == Level.ARCHITECTURAL ? 100 : level == Level.IMPLEMENTATION ? 50 : 10;

            int typePriority = switch (type) {
                case RULE -> 20;
                case DECISION -> 15;
                case CONTEXT -> 10;
            };

            return basePriority + typePriority;
        }
    }
}