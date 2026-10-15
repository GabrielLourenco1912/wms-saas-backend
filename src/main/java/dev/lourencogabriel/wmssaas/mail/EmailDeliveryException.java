package dev.lourencogabriel.wmssaas.mail;

public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(Throwable cause) {
        super("Email delivery is temporarily unavailable", cause);
    }
}
