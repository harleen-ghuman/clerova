package com.clerova.email;

public interface EmailSender {

    /**
     * Sends the email and returns the provider's message id.
     *
     * @throws EmailSendException if the provider rejects the email
     */
    String send(OutgoingEmail email);
}
