package com.clerova.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Email intake and sending settings.
 */
@Component
public class EmailProperties {

    private final String inboundDomain;
    private final String webhookUsername;
    private final String webhookPassword;
    private final String postmarkServerToken;

    public EmailProperties(
            @Value("${app.email.inbound-domain}") String inboundDomain,
            @Value("${app.email.webhook-username}") String webhookUsername,
            @Value("${app.email.webhook-password}") String webhookPassword,
            @Value("${app.email.postmark-server-token}") String postmarkServerToken
    ) {
        this.inboundDomain = inboundDomain.trim().toLowerCase();
        this.webhookUsername = webhookUsername;
        this.webhookPassword = webhookPassword;
        this.postmarkServerToken = postmarkServerToken;
    }

    /** Domain whose addresses receive forwarded mail, e.g. in.clerova-ai.com. */
    public String getInboundDomain() {
        return inboundDomain;
    }

    public boolean isInboundEnabled() {
        return !inboundDomain.isBlank() && !webhookPassword.isBlank();
    }

    public String getWebhookUsername() {
        return webhookUsername;
    }

    public String getWebhookPassword() {
        return webhookPassword;
    }

    public String getPostmarkServerToken() {
        return postmarkServerToken;
    }

    public boolean isSendingEnabled() {
        return isInboundEnabled() && !postmarkServerToken.isBlank();
    }

    public String forwardingAddress(String token) {
        return token + "@" + inboundDomain;
    }

    /** Reply-to address that routes replies back to a specific work item. */
    public String replyAddress(String token, java.util.UUID workItemId) {
        return token + "+" + WorkItemMailboxHash.of(workItemId) + "@" + inboundDomain;
    }
}
