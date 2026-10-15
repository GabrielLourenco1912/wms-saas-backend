package dev.lourencogabriel.wmssaas.mail;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.springframework.stereotype.Component;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class EmailTemplateRenderer {

    private static final String TEMPLATE_DIRECTORY = "mail/";
    private static final String TEMPLATE_EXTENSION = ".ftlh";

    private final Configuration configuration;

    public EmailTemplateRenderer(Configuration configuration) {
        this.configuration = configuration;
    }

    public String render(String templateName, Map<String, ?> model) {
        try {
            Template template = this.configuration.getTemplate(
                    TEMPLATE_DIRECTORY + templateName + TEMPLATE_EXTENSION,
                    StandardCharsets.UTF_8.name()
            );
            return FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
        } catch (IOException | TemplateException exception) {
            throw new EmailDeliveryException(exception);
        }
    }
}
