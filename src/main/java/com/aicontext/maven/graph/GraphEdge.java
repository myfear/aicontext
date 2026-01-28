package com.aicontext.maven.graph;

import java.util.Collections;
import java.util.List;

/**
 * A single edge in a relationship graph: relation type, direction, and targets.
 */
public class GraphEdge {

    public enum Direction {
        OUTBOUND,  // node → targets (uses, calls, db, events, external, config)
        INBOUND    // callers → node ([by]←)
    }

    private final String relationType;
    private final Direction direction;
    private final List<String> targets;

    public GraphEdge(String relationType, Direction direction, List<String> targets) {
        this.relationType = relationType == null ? "" : relationType;
        this.direction = direction;
        this.targets = targets == null ? List.of() : List.copyOf(targets);
    }

    public String getRelationType() {
        return relationType;
    }

    public Direction getDirection() {
        return direction;
    }

    public List<String> getTargets() {
        return targets;
    }

    public boolean isOutbound() {
        return direction == Direction.OUTBOUND;
    }

    public boolean isInbound() {
        return direction == Direction.INBOUND;
    }
}
