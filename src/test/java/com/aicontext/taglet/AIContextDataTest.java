package com.aicontext.taglet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.aicontext.taglet.AIContextTaglet.AIContextData;

class AIContextDataTest {

    @Test
    void testConstructorAndGetters() {
        AIContextData data = new AIContextData(
            "com.example.SampleClass",
            AIContextData.Level.ARCHITECTURAL,
            AIContextData.TagType.RULE,
            "Test content",
            "2024-01-15"
        );

        assertThat(data.getLocation()).isEqualTo("com.example.SampleClass");
        assertThat(data.getLevel()).isEqualTo(AIContextData.Level.ARCHITECTURAL);
        assertThat(data.getType()).isEqualTo(AIContextData.TagType.RULE);
        assertThat(data.getContent()).isEqualTo("Test content");
        assertThat(data.getTimestamp()).isEqualTo("2024-01-15");
    }

    @Test
    void testGetPriority_ArchitecturalRule() {
        AIContextData data = new AIContextData(
            "com.example.Class",
            AIContextData.Level.ARCHITECTURAL,
            AIContextData.TagType.RULE,
            "Content",
            null
        );

        // ARCHITECTURAL (100) + RULE (20) = 120
        assertThat(data.getPriority()).isEqualTo(120);
    }

    @Test
    void testGetPriority_ArchitecturalDecision() {
        AIContextData data = new AIContextData(
            "com.example.Class",
            AIContextData.Level.ARCHITECTURAL,
            AIContextData.TagType.DECISION,
            "Content",
            null
        );

        // ARCHITECTURAL (100) + DECISION (15) = 115
        assertThat(data.getPriority()).isEqualTo(115);
    }

    @Test
    void testGetPriority_ImplementationRule() {
        AIContextData data = new AIContextData(
            "com.example.Class.method()",
            AIContextData.Level.IMPLEMENTATION,
            AIContextData.TagType.RULE,
            "Content",
            null
        );

        // IMPLEMENTATION (50) + RULE (20) = 70
        assertThat(data.getPriority()).isEqualTo(70);
    }

    @Test
    void testGetPriority_AllTagTypes() {
        // Test priority ordering
        AIContextData rule = new AIContextData("", AIContextData.Level.ARCHITECTURAL, 
            AIContextData.TagType.RULE, "", null);
        AIContextData decision = new AIContextData("", AIContextData.Level.ARCHITECTURAL, 
            AIContextData.TagType.DECISION, "", null);
        AIContextData context = new AIContextData("", AIContextData.Level.ARCHITECTURAL, 
            AIContextData.TagType.CONTEXT, "", null);

        // Verify priority ordering: RULE > DECISION > CONTEXT
        assertThat(rule.getPriority()).isGreaterThan(decision.getPriority());
        assertThat(decision.getPriority()).isGreaterThan(context.getPriority());
    }

    @Test
    void testGetPriority_LevelOrdering() {
        // Test that ARCHITECTURAL > IMPLEMENTATION for same tag type
        AIContextData archRule = new AIContextData("", AIContextData.Level.ARCHITECTURAL, 
            AIContextData.TagType.RULE, "", null);
        AIContextData implRule = new AIContextData("", AIContextData.Level.IMPLEMENTATION, 
            AIContextData.TagType.RULE, "", null);

        assertThat(archRule.getPriority()).isGreaterThan(implRule.getPriority());
    }

    @Test
    void testLevelEnum() {
        assertThat(AIContextData.Level.ARCHITECTURAL).isNotNull();
        assertThat(AIContextData.Level.IMPLEMENTATION).isNotNull();
        assertThat(AIContextData.Level.INFORMATIONAL).isNotNull();
        assertThat(AIContextData.Level.values()).hasSize(3);
    }

    @Test
    void testTagTypeEnum() {
        assertThat(AIContextData.TagType.RULE).isNotNull();
        assertThat(AIContextData.TagType.DECISION).isNotNull();
        assertThat(AIContextData.TagType.CONTEXT).isNotNull();
        assertThat(AIContextData.TagType.values()).hasSize(3);
    }
}
