# AIContext for Java

> Embed AI coding assistant context directly in your Java code using enhanced Javadoc tags

---

## Warning

**This plugin is not yet available on Maven Central.** You must build it from source and install it into your local Maven repository before you can use it in other projects.

---

## How to build

Build and install the plugin into your local Maven repository:

```bash
git clone https://github.com/myfear/aicontext.git
cd aicontext
mvn clean install
```

After a successful build, the plugin is available in your local repo and you can add it to any project's `pom.xml` as shown in Quick Start below.

---

## Quick Start

**1. Add the plugin to your `pom.xml`:**

```xml
<plugin>
    <groupId>com.aicontext</groupId>
    <artifactId>aicontext-maven-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <phase>compile</phase>
            <goals>
                <goal>generate-docs</goal>
            </goals>
            <configuration>
                <!-- Required: specify which assistant(s) to generate for -->
                <assistants>cursor</assistants>
            </configuration>
        </execution>
    </executions>
</plugin>
```

> **Note:** The `assistants` parameter is required. If not configured, the plugin will warn and skip generation. Supported values: `claude`, `cursor`, `copilot`, `bob` (comma-separated for multiple).

**2. Add tags to your code:**

```java
/**
 * Payment processing service.
 * 
 * @aicontext-decision [2024-01-15] Using Stripe for international support
 * @aicontext-rule Never log credit card details, use tokens only
 */
public class PaymentService { }
```

**3. Build your project:**

```bash
mvn compile
```

**4. Your AI assistants now have context!**

| Assistant | Reads From |
|-----------|-----------|
| Claude Code | `CLAUDE.md` (project root) |
| Cursor | `.cursor/rules/` (markdown files with frontmatter) |
| GitHub Copilot | `.github/copilot-instructions.md` |
| IBM Bob | `.Bobmodes` + `.Bob/rules-{artifactId}-mode/` |

---

## Overview

AIContext extends Javadoc with custom tags to capture architectural decisions, coding rules, and business context. It generates AI-friendly documentation for Claude Code, Cursor, GitHub Copilot, IBM Bob, and other assistants.

### Supported AI Assistants

| Assistant | Output Location | Format |
|-----------|----------------|--------|
| **Claude Code** | `CLAUDE.md`, `ARCHITECTURE.md`, `DECISIONS.md`, `RULES.md`, `TAG_INDEX.md` | Multi-file Markdown |
| **Cursor** | `.cursor/rules/` | Five grouped files (01–05) with frontmatter (`alwaysApply`, `applyIntelligently`, `description`) |
| **GitHub Copilot** | `.github/copilot-instructions.md` | Markdown instructions |
| **IBM Bob** | `.Bobmodes` + `.Bob/rules-{artifactId}-mode/` | Custom mode with instruction files |

## Key Concepts

### 1. **Priority Levels**

Tags are prioritized based on their location:

- **ARCHITECTURAL** (Class-level): Affects overall design, structure, and cross-cutting concerns
- **IMPLEMENTATION** (Method-level): Specific implementation details and patterns

This ensures AI assistants understand what's foundational vs. what's tactical.

### 2. **Multiple Tags Per Element**

Unlike standard Javadoc, you can add multiple `@aicontext-*` tags to a single class or method:

```java
/**
 * Payment processing service.
 * 
 * @aicontext-decision [2024-01-15] Using Stripe for international support
 * @aicontext-rule Never log credit card details, use tokens only
 * @aicontext-context PCI DSS Level 1 compliance required
 * @aicontext-rule 30-second timeout for all payment operations
 */
public class PaymentService {
    // ...
}
```

### 3. **Tag Types**

| Tag | Purpose | Example |
|-----|---------|---------|
| `@aicontext-rule` | Coding rules, constraints, must-follow patterns | `@aicontext-rule Never retry 4xx errors, only 5xx` |
| `@aicontext-decision` | Design decisions with date and rationale | `@aicontext-decision [2024-01-15] Using PostgreSQL for ACID compliance` |
| `@aicontext-graph` | Relationship graph (compact notation) | See [Compact Graph Notation](#compact-graph-notation) below |
| `@aicontext-graph-ignore` | Intentional omission from graph validation | `@aicontext-graph-ignore StringUtils, LoggingHelper` |
| `@aicontext-context` | Business context, regulatory requirements | `@aicontext-context GDPR: 7-year data retention` |

### 4. **Compact Graph Notation** (`@aicontext-graph`)

Use `@aicontext-graph` on a class to document relationships in a compact, tree-style notation. This generates **RELATIONSHIPS.md** (Claude) and gives AI assistants a clear view of who uses whom, what is called, and where data lives.

**Syntax:** First line = node name. Following lines = edges: `├─[relation]→ targets` or `└─[by]← callers`.

| Relation | Meaning | Direction |
|----------|---------|-----------|
| `[uses]` | Dependencies (types used) | → |
| `[calls]` | Method calls | → |
| `[db]` | Database tables (e.g. `W:table(columns)`) | → |
| `[events]` | Events published | → |
| `[external]` | External APIs / URLs | → |
| `[config]` | Config keys / env vars | → |
| `[by]` | Callers (who calls this) | ← |

**Example in Javadoc:**

```java
/**
 * Payment processing service.
 *
 * @aicontext-graph
 * PaymentService
 *   ├─[uses]→ StripeClient, PaymentRepository, EventPublisher
 *   ├─[calls]→ StripeClient.charge(), PaymentRepository.save()
 *   ├─[db]→ W:payment_transactions(id,user_id,amount,currency,status)
 *   ├─[events]→ PaymentProcessedEvent
 *   └─[by]← OrderService.checkout(), SubscriptionService.charge()
 */
public class PaymentService { }
```

Multiple nodes (e.g. a related client) can be defined in one tag by separating blocks with a blank line:

```java
/**
 * @aicontext-graph
 * StripeClient
 *   ├─[external]→ https://api.stripe.com/v1
 *   ├─[config]→ STRIPE_API_KEY
 *   └─[by]← PaymentService, RefundService, WebhookController
 */
public class StripeClient { }
```

Relation types are extensible: any `[label]` is accepted. Use `→` for outbound edges (this → others) and `←` for inbound (others → this, e.g. callers).

#### Suggested graphs and validation

- **Generate suggested graphs (manual):** Run `mvn aicontext:generate-graphs` to write `target/suggested-graphs/<ClassName>.txt` per class. Review and copy the suggested `[uses]` block into class Javadoc.
- **During normal compile** (when `generate-docs` runs):
  - **Lenient:** The plugin **warns** if the graph documents a type the code does not use.
  - **Strict:** If the code uses a project class that is **not** in the graph and **not** listed in `@aicontext-graph-ignore`, the build **errors** with: `Class dependency 'X' found but not in graph. Add to @aicontext-graph or @aicontext-graph-ignore.`
- **@aicontext-graph-ignore:** Comma-separated class names to intentionally omit from graph validation (e.g. utilities you don’t want to document in the graph).

```java
/**
 * @aicontext-graph
 * PaymentService
 *   ├─[uses]→ StripeClient, PaymentRepository
 *   └─[by]← OrderService
 * @aicontext-graph-ignore StringUtils, LoggingHelper
 */
public class PaymentService { }
```

Disable validation: `<validateGraph>false</validateGraph>` in the plugin configuration.

## Installation

### 1. Add Plugin to Your POM

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.aicontext</groupId>
            <artifactId>aicontext-maven-plugin</artifactId>
            <version>1.0.0</version>
            <executions>
                <execution>
                    <phase>compile</phase>
                    <goals>
                        <goal>generate-docs</goal>
                    </goals>
                    <configuration>
                        <!-- Required: specify which assistant(s) to generate for -->
                        <!-- Options: claude, cursor, copilot, bob (comma-separated) -->
                        <assistants>cursor</assistants>
                        <!-- Optional: validate @aicontext-graph vs code (default true) -->
                        <!-- <validateGraph>false</validateGraph> -->
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

**Common configurations:**
- Single assistant: `<assistants>cursor</assistants>`
- Multiple assistants: `<assistants>claude,cursor,copilot</assistants>`
- All assistants: `<assistants>claude,cursor,copilot,bob</assistants>`

### 2. Add Javadoc Taglet (Optional)

For enhanced Javadoc HTML output:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <version>3.6.0</version>
    <configuration>
        <tagletArtifact>
            <groupId>com.aicontext</groupId>
            <artifactId>aicontext-taglet</artifactId>
            <version>1.0.0</version>
        </tagletArtifact>
    </configuration>
</plugin>
```

## Usage

### Step 1: Add Tags to Your Code

```java
/**
 * Repository for user authentication data.
 * 
 * @aicontext-decision [2024-01-10] Using bcrypt with cost factor 12 for password hashing
 * @aicontext-rule All queries must use prepared statements (SQL injection prevention)
 * @aicontext-context Passwords never stored in plaintext, even in logs
 */
@Repository
public class UserRepository {
    
    /**
     * Authenticates a user by username and password.
     * 
     * @aicontext-rule Rate limit: max 5 failed attempts per 15 minutes per IP
     */
    public User authenticate(String username, String password) {
        // Implementation
    }
}
```

### Step 2: Build Your Project

```bash
mvn compile
```

This generates instruction files in their correct locations:

```
your-project/
├── CLAUDE.md                    # Claude Code main entry point
├── ARCHITECTURE.md              # Class-level guidance
├── DECISIONS.md                 # Decision log
├── RULES.md                     # Coding rules
├── TAG_INDEX.md                 # Searchable index
├── .cursor/
│   └── rules/                   # Cursor rules (grouped .md files with frontmatter)
│       ├── 01-architectural-rules.md
│       ├── 02-implementation-rules.md
│       ├── 03-design-decisions.md
│       ├── 04-context-notes.md
│       └── 05-relationship-graph.md
├── .github/
│   └── copilot-instructions.md  # GitHub Copilot instructions
├── .Bobmodes                    # IBM Bob mode configuration
└── .Bob/
    └── rules-{artifactId}-mode/ # IBM Bob instruction files
        ├── 01-architectural-rules.md
        ├── 02-implementation-rules.md
        ├── 03-design-decisions.md
        ├── 04-context-notes.md
        └── 05-relationship-graph.md
```

### Step 3: Use with AI Assistants

#### Claude Code
Claude automatically reads `CLAUDE.md` when you open the project.

#### Cursor
Cursor reads rule files from `.cursor/rules/`. The plugin generates five grouped files with YAML frontmatter: `01-architectural-rules.md` and `02-implementation-rules.md` use `alwaysApply: true`; `03-design-decisions.md`, `04-context-notes.md`, and `05-relationship-graph.md` use `applyIntelligently: true`. Use the type dropdown in Cursor to control how rules are applied.

#### GitHub Copilot
Copilot reads `.github/copilot-instructions.md` automatically.

#### IBM Bob
Bob reads `.Bobmodes` for custom mode configuration. Activate the mode in Bob's UI to use project-specific instructions.

## Configuration Options

### Maven Plugin Options

```xml
<configuration>
    <!-- REQUIRED: Which AI assistants to generate docs for -->
    <!-- Options: claude, cursor, copilot, bob (comma-separated) -->
    <assistants>cursor</assistants>
    
    <!-- Source directory to scan (optional) -->
    <sourceDir>${project.basedir}/src/main/java</sourceDir>
    
    <!-- Project name (optional, defaults to ${project.artifactId}) -->
    <projectName>My Java Project</projectName>
    
    <!-- Force overwrite existing files (optional, default: false) -->
    <forceOverwrite>false</forceOverwrite>
    
    <!-- Per-assistant output directory overrides (optional) -->
    <assistantOutputDirs>
        <claude>${project.basedir}</claude>
        <cursor>${project.basedir}</cursor>
        <copilot>${project.basedir}/.github</copilot>
        <bob>${project.basedir}</bob>
    </assistantOutputDirs>
</configuration>
```

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `assistants` | **Yes** | - | Comma-separated list of assistants to generate for |
| `sourceDir` | No | `src/main/java` | Directory to scan for Java files |
| `projectName` | No | `${project.artifactId}` | Project name in generated docs |
| `forceOverwrite` | No | `false` | Overwrite existing instruction files |
| `assistantOutputDirs` | No | Correct locations | Per-assistant output directory overrides |

### Output Location Overrides

By default, files are generated in the correct locations where each assistant reads them. You can override this for testing, custom workflows, or backward compatibility:

**Testing/Development:**
```xml
<assistantOutputDirs>
    <claude>${project.basedir}/target/test-output</claude>
    <cursor>${project.basedir}/target/test-output</cursor>
</assistantOutputDirs>
```

**Backward Compatibility (old .aicontext structure):**
```xml
<assistantOutputDirs>
    <claude>${project.basedir}/.aicontext/claude</claude>
    <cursor>${project.basedir}/.aicontext/cursor</cursor>
    <copilot>${project.basedir}/.aicontext/copilot</copilot>
    <bob>${project.basedir}/.aicontext/bob</bob>
</assistantOutputDirs>
```

### Existing Files Protection

The plugin distinguishes between files it generated and manually-created files using a signature marker:

**Files generated by the plugin** contain a signature at the top:
```markdown
<!-- AIContext:generated -->
# My Project - AI Context Guide
...
```

**Behavior:**
- **Plugin-generated files**: Automatically regenerated, with custom content preserved (see below)
- **Manually-created files**: Plugin will **ERROR** and refuse to overwrite

If you have manually-created instruction files, you'll see:
```
ERROR: Found existing instruction file(s):
  - /path/to/project/CLAUDE.md
  - /path/to/project/.cursor/rules

The AIContext plugin cannot overwrite existing instruction files.
Please migrate the content to @aicontext-* Javadoc annotations in your code,
then remove the existing files and run the plugin again.

Alternatively, use -Daicontext.forceOverwrite=true to overwrite existing files.
```

To force overwrite manually-created files:
```bash
mvn compile -Daicontext.forceOverwrite=true
```

Or in configuration:
```xml
<configuration>
    <forceOverwrite>true</forceOverwrite>
</configuration>
```

### Custom Content Preservation

Every generated file includes a **custom section** at the bottom where you can add your own notes, rules, or context. This content is preserved when the plugin regenerates the file:

```markdown
<!-- AIContext:generated -->
# My Project - AI Context Guide

[... auto-generated content ...]

---

<!-- AICONTEXT:CUSTOM - Content below this line will be preserved on regeneration -->

## Custom Notes

_Add your own project-specific notes, rules, or context below. This section will not be overwritten._

## My Team's Conventions                    <-- Your custom content
                                            
- Always use dependency injection           <-- Preserved on regeneration
- Prefer composition over inheritance
- Document all public APIs
```

**How it works:**
1. Generated files end with a custom section marker
2. Add your own content below the marker
3. On regeneration, the plugin extracts and re-appends your custom content
4. Everything above the marker is regenerated; everything below is preserved

**Use cases:**
- Team-specific conventions not in code
- Temporary notes during refactoring
- Links to external documentation
- Project-specific AI assistant hints

> **Note:** The signature marker (`<!-- AIContext:generated -->`) and custom section marker (`<!-- AICONTEXT:CUSTOM -->`) should not be removed from generated files.

## Generated Documentation Structure

### Claude Code Files

**CLAUDE.md** (Main Entry Point):
```markdown
# My Java Project - AI Context Guide

**Project**: My Java Project
**Type**: Java/Maven Project
**Last Updated**: 2024-01-17T10:30:00

## Quick Start for AI Assistants

This project uses `@aicontext-*` Javadoc tags...

## Key Documentation Files

| File | Description |
|------|-------------|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Class-level architectural guidance |
| [DECISIONS.md](./DECISIONS.md) | All design decisions chronologically |
| [RULES.md](./RULES.md) | Coding rules and constraints |
| [TAG_INDEX.md](./TAG_INDEX.md) | Searchable index of all tags |

## Statistics

- Total Entries: 47
- Rules: 23
- Decisions: 12
- Context Notes: 8
- Architectural: 15
- Implementation: 32
```

**ARCHITECTURE.md** (Class-Level Guidance):
```markdown
# Architectural Guidance

## com.example.payment

### PaymentService
**File**: `src/main/java/com/example/payment/PaymentService.java:10`

**DECISION** (ARCHITECTURAL):
[2024-01-15] Using Stripe as primary payment gateway for international support.

---
```

**DECISIONS.md** (Decision Log):
```markdown
# Design Decisions Log

## com.example.payment.PaymentService
| Property | Value |
|----------|-------|
| **Level** | ARCHITECTURAL |
| **File** | `src/main/java/com/example/payment/PaymentService.java:10` |
| **Date** | 2024-01-15 |

### Decision
Using Stripe as primary payment gateway for international support.

---
```

### Cursor (`.cursor/rules/*.md`)

Rules are grouped into four files (like IBM Bob). Architectural and implementation rules use `alwaysApply: true`; design decisions and context notes use `applyIntelligently: true`.

**01-architectural-rules.md** (always applied):

```markdown
---
globs: []
alwaysApply: true
applyIntelligently: false
description: "Class-level rules that affect overall design and structure."
---

# Architectural Rules

## com.example.PaymentService

Never log credit card details, use tokens only.

*Source: `src/main/java/.../PaymentService.java:42`*

---
```

**03-design-decisions.md** (applied intelligently):

```markdown
---
globs: []
alwaysApply: false
applyIntelligently: true
description: "Key architectural and implementation decisions (newest first)."
---

# Design Decisions

## com.example.PaymentService

*Date: 2024-01-15*

Using Stripe as primary payment gateway for international support.

*Source: `src/main/java/.../PaymentService.java:45`*

---
```

### GitHub Copilot (copilot-instructions.md)

The plugin generates a single instructions file that includes project-specific rules, key design decisions, and (when present) a **Relationship Graph** section from `@aicontext-graph` tags.

```markdown
# GitHub Copilot Instructions

## Project-Specific Rules

### com.example.payment.PaymentService
Never log credit card details, use tokens only

*Source: `src/main/java/com/example/payment/PaymentService.java:10`*

## Key Design Decisions

- **com.example.payment.PaymentService**: Using Stripe for international support (2024-01-15)
```

### IBM Bob (.Bobmodes and instruction files)

**.Bobmodes:**
```yaml
customModes:
  - slug: myproject-mode
    name: My Project Mode
    roleDefinition: >-
      You are a software engineer working on My Project.
      This is a Java/Maven Project project. Your role is to understand
      and follow the project's architectural decisions, coding rules,
      and design patterns as documented in the codebase.
    whenToUse: >-
      Use this mode when working on My Project to ensure
      adherence to project-specific rules and architectural decisions.
    groups:
      - read
      - edit
      - browser
      - command
```

**.Bob/rules-myproject-mode/01-architectural-rules.md:**
```markdown
# Architectural Rules

Class-level rules that affect overall design and structure.

## com.example.payment.PaymentService
Never log credit card details, use tokens only

*Source: `src/main/java/com/example/payment/PaymentService.java:10`*
```

## Best Practices

### 1. **Use Architectural Tags for Design Patterns**

```java
/**
 * Event bus for domain events.
 * 
 * @aicontext-decision [2024-01-20] Using Spring's ApplicationEventPublisher 
 *                     for loose coupling between bounded contexts
 * @aicontext-rule All events must be immutable and serializable
 * @aicontext-context Events processed asynchronously to prevent blocking
 */
public class DomainEventBus {
    // ...
}
```

### 2. **Document Security Rules at Class Level**

```java
/**
 * Authentication filter for API endpoints.
 * 
 * @aicontext-rule All endpoints require JWT authentication except /health and /metrics
 * @aicontext-rule JWT tokens expire after 24 hours, refresh tokens after 7 days
 * @aicontext-context Using RS256 algorithm (asymmetric keys) for token verification
 */
public class JwtAuthenticationFilter {
    // ...
}
```

### 3. **Use Implementation Tags for Method-Specific Logic**

```java
/**
 * Sends notification emails.
 * 
 * @aicontext-rule Rate limit: 100 emails per hour per user
 */
public void sendEmail(String to, String subject, String body) {
    // Implementation
}
```

### 4. **Include Dates in Decisions**

Always format as `[YYYY-MM-DD]`:

```java
/**
 * @aicontext-decision [2024-01-15] Switching from MySQL to PostgreSQL 
 *                     for better JSON support and full-text search
 */
```

### 5. **Use Context for Business Rules**

```java
/**
 * @aicontext-context GDPR: Personal data must be anonymized after 30 days 
 *                    of account deletion request
 * @aicontext-context Tax regulation: Transaction records retained for 7 years
 */
```

## Customizing Output with Scaffolding

AIContext uses a flexible scaffolding system that allows you to customize both the **structure** (which files are generated) and **templates** (how content is formatted) for each AI assistant.

### How It Works

The plugin uses **YAML configuration files** to define the structure and **Mustache templates** for content generation. By default, it uses bundled configurations and templates, but you can override them in your project.

### Template Resolution Order

When generating documentation, the plugin looks for templates in this order:

1. **User Templates** (highest priority): `src/main/resources/aicontext/templates/`
2. **User Config Directory**: `.aicontext/scaffolding/` or `src/main/resources/aicontext/scaffolding/`
3. **Plugin Defaults**: Bundled templates and configs from the plugin

### Customizing Templates

To customize a template, create it in your project's resources directory:

```
src/main/resources/
└── aicontext/
    └── templates/
        └── claude/
            └── main.md.mustache  # Override Claude's main template
```

**Available Template Variables:**

- `{{projectName}}` - Project name
- `{{projectArtifactId}}` - Maven artifact ID
- `{{projectType}}` - Project type (e.g., "Java/Maven Project")
- `{{lastUpdated}}` - Timestamp of generation
- `{{entries}}` - List of all entries (after filtering)
- `{{statistics}}` - Object with counts (total, rules, decisions, etc.)
- `{{architecturalRules}}` - Pre-filtered architectural rules
- `{{implementationRules}}` - Pre-filtered implementation rules
- `{{decisions}}` - Pre-filtered decisions

**Entry Properties:**

Each entry has:
- `{{location}}` - Full location (e.g., "com.example.Service")
- `{{filePath}}` - Source file path
- `{{level}}` - ARCHITECTURAL or IMPLEMENTATION
- `{{type}}` - rule, decision, context, inference, or todo
- `{{content}}` - Entry content
- `{{timestamp}}` - Date if present
- `{{lineNumber}}` - Line number in source
- `{{packageName}}` - Package name
- `{{className}}` - Class name
- `{{preview}}` - Content truncated to 100 chars
- `{{isSecurityRelated}}` - Boolean for security-related content
- `{{isBusinessCritical}}` - Boolean for business-critical content

### Customizing Structure (Configuration)

To customize which files are generated, create a YAML configuration file:

```yaml
# src/main/resources/aicontext/scaffolding/claude.yaml
assistant: claude
outputDir: .  # Use "." for project root
files:
  - name: CLAUDE.md
    template: claude/main.md.mustache
    description: Main entry point
    context:
      includeStatistics: true
      includeFileLinks: true
  
  - name: SECURITY.md
    template: claude/security.md.mustache
    description: Security-specific rules
    filter:
      type: rule
    sort:
      field: priority
      order: desc
```

### Adding a New Assistant

To add support for a new AI assistant:

1. **Create Configuration**: `src/main/resources/aicontext/scaffolding/myassistant.yaml`

```yaml
assistant: myassistant
outputDir: .
files:
  - name: .myassistant-rules
    template: myassistant/rules.mustache
    filter:
      type: rule
    sort:
      field: priority
      order: desc
```

2. **Create Templates**: `src/main/resources/aicontext/templates/myassistant/rules.mustache`

3. **Use It**: Add to your plugin configuration

```xml
<assistants>claude,cursor,copilot,bob,myassistant</assistants>
```

## How AI Assistants Use This

### Claude Code Workflow

1. Opens your project
2. Automatically reads `CLAUDE.md` from project root
3. Understands:
   - Architectural constraints (class-level rules)
   - Implementation patterns (method-level rules)
   - Design decisions with rationale
   - Business context and requirements
4. When making changes:
   - Follows documented rules
   - Respects architectural decisions
   - Asks before violating constraints
   - Documents new context with `@aicontext-context` when relevant

### Example Interaction

```
You: "Add a new payment method for Apple Pay"

Claude: I'll add Apple Pay support following the existing payment architecture.

Based on the architectural rules in PaymentService:
- ✓ Will use tokenized payment references (no card data logging)
- ✓ Will implement 30-second timeout
- ✓ Will follow the same retry pattern (5xx only)

I notice from DECISIONS.md that Stripe is the primary gateway. 
Should we:
A) Add Apple Pay through Stripe (recommended - maintains consistency)
B) Integrate Apple Pay directly

How would you like to proceed?
```

## FAQ

**Q: Does this replace regular Javadoc?**
A: No! `@aicontext-*` tags complement standard Javadoc. Use `@param`, `@return`, `@throws` for API documentation, and `@aicontext-*` for AI context.

**Q: Will this slow down builds?**
A: Minimal impact. The plugin runs during compile phase and processes only Javadoc comments.

**Q: Can I use this with Gradle?**
A: Not yet, but a Gradle plugin is planned.

**Q: Do I need to tag every class/method?**
A: No. Tag only where context is critical:
- Security-sensitive code
- Business logic with specific rules
- Design decisions that might seem arbitrary
- Integration points with external systems

**Q: Can I customize the output format?**
A: Yes! You can customize both templates and structure. See the [Customizing Output with Scaffolding](#customizing-output-with-scaffolding) section.

**Q: What if I already have instruction files?**
A: The plugin will ERROR and refuse to overwrite existing files. Migrate your content to `@aicontext-*` tags, remove the old files, then run the plugin. Or use `forceOverwrite=true` to overwrite.

**Q: How do I migrate from code comments to Javadoc tags?**
A: Search for TODO comments, architecture notes, and business rules in your code. Convert them to appropriate `@aicontext-*` tags.

## IDE Configuration

### Resolve Lifecycle Mapping Warning (Optional)

If you see a warning like "Plugin execution not covered by lifecycle configuration" in your IDE:

**For Eclipse (m2e):**

```xml
<plugin>
    <groupId>org.eclipse.m2e</groupId>
    <artifactId>lifecycle-mapping</artifactId>
    <version>1.0.0</version>
    <configuration>
        <lifecycleMappingMetadata>
            <pluginExecutions>
                <pluginExecution>
                    <pluginExecutionFilter>
                        <groupId>com.aicontext</groupId>
                        <artifactId>aicontext-maven-plugin</artifactId>
                        <versionRange>[1.0.0,)</versionRange>
                        <goals>
                            <goal>generate-docs</goal>
                        </goals>
                    </pluginExecutionFilter>
                    <action>
                        <execute>
                            <runOnIncremental>false</runOnIncremental>
                        </execute>
                    </action>
                </pluginExecution>
            </pluginExecutions>
        </lifecycleMappingMetadata>
    </configuration>
</plugin>
```

**For IntelliJ IDEA:**
Settings → Build Tools → Maven → Ignored Build Steps → Add the plugin

**For VSCode:**
Use the Eclipse lifecycle mapping configuration above.

## Contributing

Issues and PRs welcome! This is an open-source project.

## License

Apache License 2.0 - See [LICENSE](LICENSE) file for details.
