package com.aicontext.maven.scaffolding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.aicontext.maven.AIContextMojo.AIContextEntry;

/**
 * Definition for filtering entries.
 */
public class FilterDefinition {
    private String level;
    private String type;
    private String content;
    private List<FilterDefinition> or;

    public FilterDefinition() {
        this.or = new ArrayList<>();
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<FilterDefinition> getOr() {
        return or;
    }

    public void setOr(List<FilterDefinition> or) {
        this.or = or;
    }

    /**
     * Checks if an entry matches this filter.
     */
    public boolean matches(AIContextEntry entry) {
        // If this is an OR filter, check if any sub-filter matches
        if (!or.isEmpty()) {
            return or.stream().anyMatch(f -> f.matches(entry));
        }

        // Check level
        if (level != null) {
            String entryLevel = entry.getLevel().name();
            if (!level.equals(entryLevel)) {
                return false;
            }
        }

        // Check type
        if (type != null) {
            if (!type.equals(entry.getType())) {
                return false;
            }
        }

        // Check content (simple contains check)
        if (content != null) {
            if (!entry.getContent().toLowerCase().contains(content.toLowerCase())) {
                return false;
            }
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    public static FilterDefinition fromMap(Map<String, Object> map) {
        FilterDefinition filter = new FilterDefinition();
        filter.setLevel((String) map.get("level"));
        filter.setType((String) map.get("type"));
        filter.setContent((String) map.get("content"));

        if (map.get("or") instanceof List) {
            List<Object> orList = (List<Object>) map.get("or");
            for (Object orObj : orList) {
                if (orObj instanceof Map) {
                    filter.getOr().add(FilterDefinition.fromMap((Map<String, Object>) orObj));
                }
            }
        }

        return filter;
    }
}
