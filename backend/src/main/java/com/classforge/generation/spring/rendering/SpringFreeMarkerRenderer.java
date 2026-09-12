package com.classforge.generation.spring.rendering;

import com.classforge.generation.spring.generated.*;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SpringFreeMarkerRenderer {
    private final Configuration configuration;
    public SpringFreeMarkerRenderer() {
        configuration = new Configuration(Configuration.VERSION_2_3_34);
        configuration.setTemplateLoader(new ClassTemplateLoader(getClass().getClassLoader(), "generation/spring"));
        configuration.setDefaultEncoding("UTF-8"); configuration.setLocale(Locale.ROOT);
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false); configuration.setWrapUncheckedExceptions(true); configuration.setFallbackOnNullLoopVariable(false);
    }
    public GeneratedFile render(String templatePath, String outputPath, Map<String, Object> context) {
        try { Template template = configuration.getTemplate(templatePath); StringWriter output = new StringWriter(); template.process(context, output); return new GeneratedFile(outputPath, GeneratedFileType.TEXT, normalizeGeneratedText(output.toString()).getBytes(StandardCharsets.UTF_8)); }
        catch (IOException exception) { throw failure(GeneratedProjectDiagnosticCode.TEMPLATE_NOT_FOUND, templatePath, exception); }
        catch (TemplateException | RuntimeException exception) { throw failure(GeneratedProjectDiagnosticCode.TEMPLATE_RENDER_FAILED, templatePath, exception); }
    }
    public static String normalizeGeneratedText(String text) { String normalized = text.replace("\r\n", "\n").replace('\r', '\n'); return normalized.replaceFirst("\\n*$", "") + "\n"; }
    private GeneratedProjectException failure(GeneratedProjectDiagnosticCode code, String path, Exception cause) { return new GeneratedProjectException(java.util.List.of(new GeneratedProjectDiagnostic(code, path, cause.getMessage())), cause); }
}
