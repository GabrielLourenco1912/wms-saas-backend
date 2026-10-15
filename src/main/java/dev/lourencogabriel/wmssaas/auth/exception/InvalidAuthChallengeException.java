package dev.lourencogabriel.wmssaas.auth.exception;

import org.springframework.security.core.AuthenticationException;

public class InvalidAuthChallengeException extends AuthenticationException {

    public InvalidAuthChallengeException() {
        super("Invalid or expired authentication code");
    }
}
