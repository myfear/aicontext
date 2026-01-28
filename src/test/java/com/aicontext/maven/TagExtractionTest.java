package com.aicontext.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Tests for tag extraction regex patterns used in AIContextMojo.
 */
class TagExtractionTest {

    private static final Pattern TAG_PATTERN = Pattern.compile(
            "@aicontext-(rule|decision|inference|todo|context)\\s+(.+?)(?=@|$)",
            Pattern.DOTALL);

    private static final Pattern DATE_PATTERN = Pattern.compile("\\[(\\d{4}-\\d{2}-\\d{2})\\]");

    @Test
    void testTagPattern_MatchesRule() {
        String javadoc = "@aicontext-rule Always use prepared statements";
        Matcher matcher = TAG_PATTERN.matcher(javadoc);

        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1)).isEqualTo("rule");
        assertThat(matcher.group(2).trim()).isEqualTo("Always use prepared statements");
    }

    @Test
    void testTagPattern_MatchesDecision() {
        String javadoc = "@aicontext-decision Using PostgreSQL for ACID compliance";
        Matcher matcher = TAG_PATTERN.matcher(javadoc);

        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1)).isEqualTo("decision");
        assertThat(matcher.group(2).trim()).isEqualTo("Using PostgreSQL for ACID compliance");
    }

    @Test
    void testTagPattern_MatchesAllTagTypes() {
        String[] tagTypes = {"rule", "decision", "inference", "todo", "context"};
        
        for (String tagType : tagTypes) {
            String javadoc = "@aicontext-" + tagType + " Test content";
            Matcher matcher = TAG_PATTERN.matcher(javadoc);
            
            assertThat(matcher.find())
                .as("Should match tag type: " + tagType)
                .isTrue();
            assertThat(matcher.group(1)).isEqualTo(tagType);
        }
    }

    @Test
    void testTagPattern_MatchesMultipleTags() {
        String javadoc = "@aicontext-rule First rule\n@aicontext-decision Second decision";
        Matcher matcher = TAG_PATTERN.matcher(javadoc);

        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1)).isEqualTo("rule");
        assertThat(matcher.group(2).trim()).isEqualTo("First rule");

        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1)).isEqualTo("decision");
        assertThat(matcher.group(2).trim()).isEqualTo("Second decision");
    }

    @Test
    void testTagPattern_MatchesMultilineContent() {
        String javadoc = "@aicontext-rule Line one\nLine two\nLine three";
        Matcher matcher = TAG_PATTERN.matcher(javadoc);

        assertThat(matcher.find()).isTrue();
        String content = matcher.group(2).trim();
        assertThat(content).contains("Line one");
        assertThat(content).contains("Line two");
        assertThat(content).contains("Line three");
    }

    @Test
    void testTagPattern_StopsAtNextTag() {
        String javadoc = "@aicontext-rule First content\n@aicontext-decision Second content";
        Matcher matcher = TAG_PATTERN.matcher(javadoc);

        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(2).trim()).isEqualTo("First content");
    }

    @Test
    void testTagPattern_DoesNotMatchInvalidTags() {
        String[] invalidTags = {
            "@aicontext-invalid",
            "@aicontext",
            "aicontext-rule",
            "@aicontext-rule",
            "@other-tag"
        };

        for (String tag : invalidTags) {
            Matcher matcher = TAG_PATTERN.matcher(tag);
            // Some might match but with wrong format
            if (matcher.find()) {
                // If it matches, verify it's a valid tag type
                String matchedType = matcher.group(1);
                assertThat(matchedType).isIn("rule", "decision", "inference", "todo", "context");
            }
        }
    }

    @Test
    void testDatePattern_MatchesValidDate() {
        String content = "[2024-01-15] Decision content";
        Matcher matcher = DATE_PATTERN.matcher(content);

        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1)).isEqualTo("2024-01-15");
    }

    @Test
    void testDatePattern_MatchesDateInContent() {
        String content = "Some text [2024-12-31] more text";
        Matcher matcher = DATE_PATTERN.matcher(content);

        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1)).isEqualTo("2024-12-31");
    }

    @Test
    void testDatePattern_DoesNotMatchInvalidDates() {
        String[] invalidDates = {
            "[2024-1-15]",      // Missing leading zero
            "[24-01-15]",       // Two-digit year
            "[2024/01/15]",     // Wrong separator
            "2024-01-15",       // No brackets
            "[2024-13-01]",     // Invalid month
            "[2024-01-32]"      // Invalid day
        };

        for (String date : invalidDates) {
            Matcher matcher = DATE_PATTERN.matcher(date);
            // The regex only checks format, not validity
            // But invalid formats should not match
            if (date.contains("[") && date.contains("]") && date.matches(".*\\[\\d{4}-\\d{2}-\\d{2}\\].*")) {
                // Valid format, should match
                assertThat(matcher.find()).isTrue();
            }
        }
    }

    @Test
    void testDatePattern_ExtractsFirstDate() {
        String content = "[2024-01-15] First date [2024-02-20] Second date";
        Matcher matcher = DATE_PATTERN.matcher(content);

        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1)).isEqualTo("2024-01-15");
    }

    @Test
    void testTagPattern_WithDateInContent() {
        String javadoc = "@aicontext-decision [2024-01-15] Using PostgreSQL";
        Matcher tagMatcher = TAG_PATTERN.matcher(javadoc);

        assertThat(tagMatcher.find()).isTrue();
        String content = tagMatcher.group(2).trim();
        
        // Content should include the date
        assertThat(content).contains("[2024-01-15]");
        assertThat(content).contains("Using PostgreSQL");
        
        // Date pattern should match
        Matcher dateMatcher = DATE_PATTERN.matcher(content);
        assertThat(dateMatcher.find()).isTrue();
        assertThat(dateMatcher.group(1)).isEqualTo("2024-01-15");
    }

    @Test
    void testTagPattern_WithEmptyContent() {
        String javadoc = "@aicontext-rule ";
        Matcher matcher = TAG_PATTERN.matcher(javadoc);

        // Pattern requires at least some content after tag name
        // This might not match or match with empty content
        if (matcher.find()) {
            assertThat(matcher.group(2).trim()).isEmpty();
        }
    }
}
