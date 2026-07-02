package com.gemono.backend.service;

import com.resend.*;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
// This bean will only load if resend.mock.enabled is false or missing
@ConditionalOnProperty(name = "resend.mock.enabled", havingValue = "false", matchIfMissing = true)
public class ResendEmailService implements EmailService {

    @Value("${resend.api.key}")
    private String apiKey;

    @Value("${resend.from.email}")
    private String fromEmail;

    @Value("${resend.from.name}")
    private String fromName;

    @Override
    public void sendMagicLink(String toEmail, String magicUrl, int expiryMinutes) {
        Resend resend = new Resend(apiKey);

        String html = buildMagicLinkHtml(toEmail, magicUrl, expiryMinutes);

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromName + " <" + fromEmail + ">")
                .to(toEmail)
                .subject("Your Gemono login link")
                .html(html)
                .build();

        try {
            CreateEmailResponse response = resend.emails().send(params);
            log.info("Magic link email sent to {} — id: {}", toEmail, response.getId());
        } catch (ResendException e) {
            log.error("Failed to send magic link to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send login email. Please try again.");
        }
    }

    private String buildMagicLinkHtml(String email, String url, int expiryMinutes) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            </head>
            <body style="margin:0;padding:0;background:#0c0c0c;font-family:'Yantramanav', sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#0c0c0c;padding:40px 0;">
                <tr>
                  <td align="center">
                    <table width="480" cellpadding="0" cellspacing="0"
                           style="background:#141414;border:1px solid #242424;border-radius:12px;padding:40px 36px;">
                      <tr>
                        <td style="text-align:center;padding-bottom:28px;">
                          <span style="font-family:'Doto', monospace;font-size:18px;letter-spacing:0.12em;color:#f5f5f5;font-weight:400;">
                            GEMONO
                          </span>
                        </td>
                      </tr>
                      <tr>
                        <td style="color:#e8e8e8;font-size:15px;line-height:1.65;padding-bottom:24px;">
                          <p style="margin:0 0 12px;">Hi,</p>
                          <p style="margin:0 0 12px;">
                            Click the button below to sign in to Gemono.
                            This link will expire in <strong style="color:#f5f5f5;">%d minutes</strong>.
                          </p>
                          <p style="margin:0;color:#6b6b6b;font-size:13px;">
                            If you didn't request this, you can safely ignore this email.
                          </p>
                        </td>
                      </tr>
                      <tr>
                        <td style="text-align:center;padding:8px 0 28px;">
                          <a href="%s"
                             style="display:inline-block;background:#f5f5f5;color:#0c0c0c;
                                    text-decoration:none;font-family:'Yantramanav', sans-serif;font-size:14px;
                                    font-weight:500;padding:12px 32px;border-radius:6px;
                                    letter-spacing:0.02em;">
                            Sign in to Gemono
                          </a>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(expiryMinutes, url, url);
    }
}