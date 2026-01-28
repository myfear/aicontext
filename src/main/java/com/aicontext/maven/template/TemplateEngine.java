package com.aicontext.maven.template;

import java.io.IOException;
import java.util.Map;

/**
 * Interface for template rendering engines.
 */
public interface TemplateEngine {
    /**
     * Renders a template with the given context.
     *
     * @param templateName the name/path of the template
     * @param context the context data for rendering
     * @return the rendered content
     * @throws IOException if template cannot be loaded or rendered
     */
    String render(String templateName, Map<String, Object> context) throws IOException;
}
