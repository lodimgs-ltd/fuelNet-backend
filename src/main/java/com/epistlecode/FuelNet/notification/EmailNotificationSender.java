package com.epistlecode.FuelNet.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Active only when {@code spring.mail.host} is set. */
@Component
@ConditionalOnProperty("spring.mail.host")
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final String from;

    public EmailNotificationSender(JavaMailSender mailSender,
                                   @Value("${app.mail.from:no-reply@fuelnet.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public boolean send(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
        return true;
    }

    @Override
    public String channel() {
        return "email";
    }
}
