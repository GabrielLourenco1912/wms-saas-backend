package dev.lourencogabriel.wmssaas.mail;

public record EmailMessage(
        String recipient,
        String subject,
        String textBody,
        String htmlBody
) {
}
