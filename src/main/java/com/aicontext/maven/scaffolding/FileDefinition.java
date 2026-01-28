package com.aicontext.maven.scaffolding;

import java.util.HashMap;
import java.util.Map;

/**
 * Definition of a single file to generate.
 */
public class FileDefinition {
    private String name;
    private String template;
    private String description;
    private FilterDefinition filter;
    private SortDefinition sort;
    private String groupBy;
    private Integer limit;
    private Map<String, Object> context;

    public FileDefinition() {
        this.context = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public FilterDefinition getFilter() {
        return filter;
    }

    public void setFilter(FilterDefinition filter) {
        this.filter = filter;
    }

    public SortDefinition getSort() {
        return sort;
    }

    public void setSort(SortDefinition sort) {
        this.sort = sort;
    }

    public String getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(String groupBy) {
        this.groupBy = groupBy;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    @SuppressWarnings("unchecked")
    public static FileDefinition fromMap(Map<String, Object> map) {
        FileDefinition def = new FileDefinition();
        def.setName((String) map.get("name"));
        def.setTemplate((String) map.get("template"));
        def.setDescription((String) map.get("description"));
        def.setGroupBy((String) map.get("groupBy"));
        
        if (map.get("limit") instanceof Number) {
            def.setLimit(((Number) map.get("limit")).intValue());
        }

        if (map.get("filter") instanceof Map) {
            def.setFilter(FilterDefinition.fromMap((Map<String, Object>) map.get("filter")));
        }

        if (map.get("sort") instanceof Map) {
            def.setSort(SortDefinition.fromMap((Map<String, Object>) map.get("sort")));
        }

        if (map.get("context") instanceof Map) {
            def.setContext((Map<String, Object>) map.get("context"));
        }

        return def;
    }
}
