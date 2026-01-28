package com.aicontext.maven.template;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

/**
 * Mustache-based template engine implementation.
 */
public class MustacheTemplateEngine implements TemplateEngine {
    private final TemplateLoader templateLoader;
    private final MustacheFactory mustacheFactory;

    public MustacheTemplateEngine(TemplateLoader templateLoader) {
        this.templateLoader = templateLoader;
        this.mustacheFactory = new DefaultMustacheFactory();
    }

    @Override
    public String render(String templateName, Map<String, Object> context) throws IOException {
        // Load template content
        String templateContent = templateLoader.loadTemplate(templateName);

        // Compile and render
        Mustache mustache = mustacheFactory.compile(new StringReader(templateContent), templateName);
        StringWriter writer = new StringWriter();
        mustache.execute(writer, context).flush();
        return writer.toString();
    }
}
