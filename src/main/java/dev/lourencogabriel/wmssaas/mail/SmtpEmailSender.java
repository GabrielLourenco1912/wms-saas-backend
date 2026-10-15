package dev.lourencogabriel.wmssaas.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailSender(
            JavaMailSender mailSender,
            @Value("${api.mail.from}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(EmailMessage message) {
        if (!StringUtils.hasText(this.fromAddress)) {
            throw new EmailDeliveryException(
                    new IllegalStateException("The sender email is not configured")
            );
        }
        if (message == null || !StringUtils.hasText(message.recipient())) {
            throw new EmailDeliveryException(
                    new IllegalArgumentException("The recipient email is required")
            );
        }

        try {
            MimeMessage mimeMessage = this.mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    true,
                    StandardCharsets.UTF_8.name()
            );
            helper.setValidateAddresses(true);
            helper.setFrom(this.fromAddress.trim());
            helper.setTo(message.recipient().trim());
            helper.setSubject(sanitizeSubject(message.subject()));
            helper.setText(
                    message.textBody() == null ? "" : message.textBody(),
                    message.htmlBody() == null ? "" : message.htmlBody()
            );

            this.mailSender.send(mimeMessage);
        } catch (MessagingException | MailException exception) {
            throw new EmailDeliveryException(exception);
        }
    }

    private String sanitizeSubject(String subject) {
        if (!StringUtils.hasText(subject)) {
            return "WMS";
        }
        return subject.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
