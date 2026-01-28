package com.aicontext.maven.scaffolding;

import java.util.Map;

/**
 * Definition for sorting entries.
 */
public class SortDefinition {
    private String field;
    private String order; // "asc" or "desc"

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    public static SortDefinition fromMap(Map<String, Object> map) {
        SortDefinition sort = new SortDefinition();
        sort.setField((String) map.get("field"));
        sort.setOrder((String) map.get("order"));
        return sort;
    }
}
