package com.aicontext.maven.graph;

import java.util.Collections;
import java.util.List;

/**
 * A node in a relationship graph: name and list of edges.
 */
public class GraphNode {

    private final String name;
    private final List<GraphEdge> edges;

    public GraphNode(String name, List<GraphEdge> edges) {
        this.name = name == null ? "" : name.trim();
        this.edges = edges == null ? List.of() : List.copyOf(edges);
    }

    public String getName() {
        return name;
    }

    public List<GraphEdge> getEdges() {
        return edges;
    }
}
