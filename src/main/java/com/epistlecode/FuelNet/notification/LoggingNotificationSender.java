package com.epistlecode.FuelNet.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Fallback used when no SMTP server is configured: writes the notification to
 * the application log so the flow can still be demonstrated end-to-end.
 */
@Component
@ConditionalOnMissingBean(EmailNotificationSender.class)
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public boolean send(String to, String subject, String body) {
        log.info("[ALERT → {}] {}\n{}", to, subject, body);
        return false;
    }

    @Override
    public String channel() {
        return "log";
    }
}
