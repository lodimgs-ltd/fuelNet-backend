package com.epistlecode.FuelNet.notification;

/** Delivers a plain-text notification to an email address. */
public interface NotificationSender {

    /** @return true if the message was actually delivered to a mail server, false if only logged */
    boolean send(String to, String subject, String body);

    /** Human-readable channel name, shown in the admin UI. */
    String channel();
}
