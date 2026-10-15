package dev.lourencogabriel.wmssaas.auth.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "api.security.account-lock",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AccountLockScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountLockScheduler.class);

    private final AccountSecurityService accountSecurityService;

    @Scheduled(fixedDelayString = "${api.security.account-lock.unlock-interval:60000}")
    public void unlockExpiredAccounts() {
        int unlocked = this.accountSecurityService.unlockExpiredAccounts();
        if (unlocked > 0) {
            LOGGER.info("Automatically unlocked {} user account(s)", unlocked);
        }
    }
}
