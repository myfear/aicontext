package com.aicontext.taglet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Element;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.UnknownBlockTagTree;

import jdk.javadoc.doclet.Taglet;

@ExtendWith(MockitoExtension.class)
class AIContextTagletTest {

    private AIContextTaglet taglet;

    @Mock
    private Element mockElement;

    @Mock
    private UnknownBlockTagTree mockTag;

    @Mock
    private TextTree mockTextTree;

    @BeforeEach
    void setUp() {
        taglet = new AIContextTaglet("aicontext-rule");
    }

    @Test
    void testGetName() {
        AIContextTaglet ruleTag = new AIContextTaglet("aicontext-rule");
        assertThat(ruleTag.getName()).isEqualTo("aicontext-rule");

        AIContextTaglet decisionTag = new AIContextTaglet("aicontext-decision");
        assertThat(decisionTag.getName()).isEqualTo("aicontext-decision");
    }

    @Test
    void testIsInlineTag() {
        assertThat(taglet.isInlineTag()).isFalse();
    }

    @Test
    void testGetAllowedLocations() {
        Set<Taglet.Location> locations = taglet.getAllowedLocations();
        
        assertThat(locations).containsExactlyInAnyOrder(
            Taglet.Location.TYPE,
            Taglet.Location.FIELD,
            Taglet.Location.CONSTRUCTOR,
            Taglet.Location.METHOD,
            Taglet.Location.OVERVIEW,
            Taglet.Location.PACKAGE
        );
    }

    @Test
    void testRegister() {
        Map<String, Taglet> tagletMap = new HashMap<>();
        
        AIContextTaglet.register(tagletMap);
        
        assertThat(tagletMap).hasSize(3);
        assertThat(tagletMap).containsKeys(
            "aicontext-rule",
            "aicontext-decision",
            "aicontext-context"
        );
        
        // Verify all registered taglets are instances of AIContextTaglet
        tagletMap.values().forEach(taglet -> {
            assertThat(taglet).isInstanceOf(AIContextTaglet.class);
        });
    }

    @Test
    void testToStringWithEmptyTags() {
        String result = taglet.toString(Collections.emptyList(), mockElement);
        
        assertThat(result).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testToStringWithSingleTag() {
        // Setup mock tag
        when(mockTag.getTagName()).thenReturn("aicontext-rule");
        doReturn((List<? extends DocTree>) List.of(mockTextTree)).when(mockTag).getContent();
        when(mockTextTree.toString()).thenReturn("Test rule content");
        
        List<? extends DocTree> tags = List.of(mockTag);
        String result = taglet.toString(tags, mockElement);
        
        assertThat(result).isNotNull();
        assertThat(result).contains("<div class=\"aicontext-tags\">");
        assertThat(result).contains("<div class=\"aicontext-tag aicontext-rule\">");
        assertThat(result).contains("<strong>RULE</strong>");
        assertThat(result).contains("Test rule content");
        assertThat(result).contains("Priority: ARCHITECTURAL");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testToStringWithMultipleTags() {
        UnknownBlockTagTree tag1 = mock(UnknownBlockTagTree.class);
        TextTree text1 = mock(TextTree.class);
        when(tag1.getTagName()).thenReturn("aicontext-rule");
        doReturn((List<? extends DocTree>) List.of(text1)).when(tag1).getContent();
        when(text1.toString()).thenReturn("First rule");

        UnknownBlockTagTree tag2 = mock(UnknownBlockTagTree.class);
        TextTree text2 = mock(TextTree.class);
        when(tag2.getTagName()).thenReturn("aicontext-decision");
        doReturn((List<? extends DocTree>) List.of(text2)).when(tag2).getContent();
        when(text2.toString()).thenReturn("Second decision");

        List<? extends DocTree> tags = List.of(tag1, tag2);
        String result = taglet.toString(tags, mockElement);
        
        assertThat(result).isNotNull();
        assertThat(result).contains("First rule");
        assertThat(result).contains("Second decision");
        assertThat(result).contains("RULE");
        assertThat(result).contains("DECISION");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testToStringWithDecisionTag() {
        AIContextTaglet decisionTaglet = new AIContextTaglet("aicontext-decision");
        
        when(mockTag.getTagName()).thenReturn("aicontext-decision");
        doReturn((List<? extends DocTree>) List.of(mockTextTree)).when(mockTag).getContent();
        when(mockTextTree.toString()).thenReturn("Using PostgreSQL for ACID compliance");
        
        String result = decisionTaglet.toString(List.of(mockTag), mockElement);
        
        assertThat(result).contains("DECISION");
        assertThat(result).contains("Using PostgreSQL for ACID compliance");
        assertThat(result).contains("Priority: ARCHITECTURAL");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testToStringWithContextTag() {
        AIContextTaglet contextTaglet = new AIContextTaglet("aicontext-context");
        
        when(mockTag.getTagName()).thenReturn("aicontext-context");
        doReturn((List<? extends DocTree>) List.of(mockTextTree)).when(mockTag).getContent();
        when(mockTextTree.toString()).thenReturn("GDPR compliance required");
        
        String result = contextTaglet.toString(List.of(mockTag), mockElement);
        
        assertThat(result).contains("CONTEXT");
        assertThat(result).contains("GDPR compliance required");
        assertThat(result).contains("Priority: IMPLEMENTATION");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testToStringIgnoresNonUnknownBlockTagTree() {
        DocTree otherTag = mock(DocTree.class);
        when(mockTag.getTagName()).thenReturn("aicontext-rule");
        doReturn((List<? extends DocTree>) List.of(mockTextTree)).when(mockTag).getContent();
        when(mockTextTree.toString()).thenReturn("Valid tag");
        
        List<? extends DocTree> tags = List.of(mockTag, otherTag);
        String result = taglet.toString(tags, mockElement);
        
        // Should only process the UnknownBlockTagTree
        assertThat(result).contains("Valid tag");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testToStringWithMultilineContent() {
        TextTree line1 = mock(TextTree.class);
        TextTree line2 = mock(TextTree.class);
        when(line1.toString()).thenReturn("First line");
        when(line2.toString()).thenReturn("Second line");
        
        when(mockTag.getTagName()).thenReturn("aicontext-rule");
        doReturn((List<? extends DocTree>) List.of(line1, line2)).when(mockTag).getContent();
        
        String result = taglet.toString(List.of(mockTag), mockElement);
        
        assertThat(result).contains("First line");
        assertThat(result).contains("Second line");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testToStringWithEmptyContent() {
        when(mockTag.getTagName()).thenReturn("aicontext-rule");
        @SuppressWarnings("unchecked")
        List<? extends DocTree> emptyList = Collections.<DocTree>emptyList();
        doReturn(emptyList).when(mockTag).getContent();
        
        String result = taglet.toString(List.of(mockTag), mockElement);
        
        assertThat(result).isNotNull();
        assertThat(result).contains("RULE");
    }
}
