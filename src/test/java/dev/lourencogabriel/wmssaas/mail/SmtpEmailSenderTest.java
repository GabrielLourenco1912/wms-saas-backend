package dev.lourencogabriel.wmssaas.mail;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    private MimeMessage mimeMessage;
    private SmtpEmailSender sender;

    @BeforeEach
    void setUp() {
        this.mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        this.sender = new SmtpEmailSender(this.mailSender, " no-reply@example.com ");
        when(this.mailSender.createMimeMessage()).thenReturn(this.mimeMessage);
    }

    @Test
    void shouldSendUtf8TextAndHtmlAlternatives() throws Exception {
        EmailMessage message = new EmailMessage(
                " learner@example.com ",
                "Código de acesso\r\nBcc: attacker@example.com",
                "Olá! Seu código é 123456.",
                "<html><body><strong>Olá!</strong> Seu código é 123456.</body></html>"
        );

        this.sender.send(message);
        this.mimeMessage.saveChanges();

        verify(this.mailSender).send(this.mimeMessage);
        assertEquals("Código de acesso  Bcc: attacker@example.com", this.mimeMessage.getSubject());
        assertEquals(
                "learner@example.com",
                this.mimeMessage.getAllRecipients()[0].toString()
        );
        assertTrue(this.mimeMessage.isMimeType("multipart/*"));
        assertTrue(containsMimeType(this.mimeMessage, "multipart/alternative"));
        assertEquals(
                "Olá! Seu código é 123456.",
                findContent(this.mimeMessage, "text/plain")
        );
        assertEquals(
                "<html><body><strong>Olá!</strong> Seu código é 123456.</body></html>",
                findContent(this.mimeMessage, "text/html")
        );
    }

    @Test
    void shouldRejectInvalidRecipientAddress() {
        EmailMessage message = new EmailMessage(
                "invalid-address",
                "Subject",
                "Text",
                "<p>HTML</p>"
        );

        EmailDeliveryException exception = assertThrows(
                EmailDeliveryException.class,
                () -> this.sender.send(message)
        );

        assertNotNull(exception.getCause());
    }

    private String findContent(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            return part.getContent().toString();
        }
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                String found = findContent(multipart.getBodyPart(index), mimeType);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean containsMimeType(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            return true;
        }
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                if (containsMimeType(multipart.getBodyPart(index), mimeType)) {
                    return true;
                }
            }
        }
        return false;
    }
}
