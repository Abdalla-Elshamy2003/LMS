package com.manarah.notification.channel;

/**
 * Port for an out-of-band delivery channel (§4). Real integrations (WhatsApp Business API, an SMS
 * gateway, SMTP, FCM) implement this. Until credentials are configured, the bundled implementations
 * log the intent and mark delivery as stubbed — they never claim a real message was sent.
 */
public interface ExternalMessageSender {

    /** Channel name this sender handles: WHATSAPP, SMS, EMAIL, PUSH. */
    String channel();

    /** Attempt delivery; return true if actually dispatched to a live provider. */
    boolean send(String recipient, String title, String body);
}
