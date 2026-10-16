package dev.lourencogabriel.wmssaas.mail;

import dev.lourencogabriel.wmssaas.auth.model.AuthChallengePurpose;
import freemarker.template.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplateRendererTest {

    private EmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_34);
        configuration.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "/templates/");
        configuration.setDefaultEncoding(StandardCharsets.UTF_8.name());
        this.renderer = new EmailTemplateRenderer(configuration);
    }

    @ParameterizedTest
    @EnumSource(AuthChallengePurpose.class)
    void shouldRenderEveryChallengeWithReadableBrandAndUnchangedCode(AuthChallengePurpose purpose) throws Exception {
        Map<String, Object> model = model(purpose);
        String html = this.renderer.render("auth-challenge", model);

        assertTrue(html.contains(EmailBranding.NAME));
        assertTrue(html.contains(EmailBranding.DESCRIPTOR));
        assertTrue(html.contains(EmailBranding.TAGLINE));
        assertTrue(html.contains(EmailBranding.AUTHOR_NAME));
        assertTrue(html.contains("001234"));
        assertTrue(html.contains("10 minutos"));
        assertTrue(html.contains(model.get("heading").toString()));
        assertTrue(html.contains(model.get("action").toString()));
        assertTrue(html.contains("cid:wms-brand-icon"));
        assertTrue(html.contains("cid:wms-author-monogram"));
        assertFalse(html.toLowerCase(Locale.ROOT).contains("portal acadêmico"));
        assertFalse(Pattern.compile("<img[^>]+src=[\"']https?://", Pattern.CASE_INSENSITIVE).matcher(html).find());
        assertFalse(html.contains("<script"));
        exportPreviewIfRequested(purpose, html);
    }

    @Test
    void shouldEscapeUserContentAndAllTemplateSlots() {
        Map<String, Object> model = new HashMap<>(model(AuthChallengePurpose.MFA_LOGIN));
        model.put("recipientName", "<img src=x onerror=alert(1)> & Maria");
        model.put("subject", "Acesso <teste>");
        model.put("heading", "Confirme <seu acesso>");
        model.put("preheader", "Código <privado>");

        String html = this.renderer.render("auth-challenge", model);

        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt; &amp; Maria"));
        assertTrue(html.contains("<title>Acesso &lt;teste&gt;</title>"));
        assertTrue(html.contains("Confirme &lt;seu acesso&gt;"));
        assertTrue(html.contains("Código &lt;privado&gt;"));
        assertFalse(html.contains("<img src=x"));
    }

    private Map<String, Object> model(AuthChallengePurpose purpose) {
        String action = switch (purpose) {
            case EMAIL_VERIFICATION -> "confirmar seu e-mail";
            case MFA_LOGIN -> "concluir seu login";
            case PASSWORD_RESET -> "redefinir sua senha";
        };
        String heading = switch (purpose) {
            case EMAIL_VERIFICATION -> "Confirme seu e-mail";
            case MFA_LOGIN -> "Confirme seu acesso";
            case PASSWORD_RESET -> "Redefina sua senha";
        };
        return Map.of(
                "subject", heading + " no WMS",
                "preheader", "Código de demonstração para " + action + ".",
                "heading", heading,
                "recipientName", "Pessoa de demonstração",
                "action", action,
                "code", "001234",
                "expirationMinutes", 10
        );
    }

    /** Optional local preview uses only synthetic fixtures and never calls SMTP. */
    private void exportPreviewIfRequested(AuthChallengePurpose purpose, String html) throws Exception {
        String directory = System.getProperty("wms.email.preview-directory");
        if (directory == null || directory.isBlank()) return;
        for (EmailBrandAssets.InlineImage image : EmailBrandAssets.IMAGES) {
            try (var input = new ClassPathResource(image.resourcePath()).getInputStream()) {
                html = html.replace("cid:" + image.contentId(),
                        "data:image/png;base64," + Base64.getEncoder().encodeToString(input.readAllBytes()));
            }
        }
        Path target = Path.of(directory);
        Files.createDirectories(target);
        Files.writeString(target.resolve(purpose.name().toLowerCase(Locale.ROOT) + ".html"), html, StandardCharsets.UTF_8);
    }
}
