package com.gemono.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
// This bean will only load if resend.mock.enabled is explicitly set to true
@ConditionalOnProperty(name = "resend.mock.enabled", havingValue = "true")
public class MockResendEmailService implements EmailService {

    @Override
    public void sendMagicLink(String toEmail, String magicUrl, int expiryMinutes) {
        log.info("=======================================================================");
        log.info("[MOCK EMAIL] Intercepted request to send email to: {}", toEmail);
        log.info("[MOCK EMAIL] Expiry window: {} minutes", expiryMinutes);
        log.info("[MOCK EMAIL] Simulation Login Link: {}", magicUrl);
        log.info("=======================================================================");
    }
}