package com.clerova.email;

import com.clerova.domain.InboundMessage;
import com.clerova.domain.InboundMessageStatus;
import com.clerova.domain.Organization;
import com.clerova.dto.EmailSettingsResponse;
import com.clerova.dto.InboundMessageResponse;
import com.clerova.exception.NotFoundException;
import com.clerova.repository.InboundMessageRepository;
import com.clerova.repository.OrganizationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class InboxService {

    private final EmailProperties emailProperties;
    private final InboundAddressService inboundAddressService;
    private final InboundMessageRepository inboundMessageRepository;
    private final OrganizationRepository organizationRepository;

    public InboxService(
            EmailProperties emailProperties,
            InboundAddressService inboundAddressService,
            InboundMessageRepository inboundMessageRepository,
            OrganizationRepository organizationRepository
    ) {
        this.emailProperties = emailProperties;
        this.inboundAddressService = inboundAddressService;
        this.inboundMessageRepository = inboundMessageRepository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional(readOnly = true)
    public List<InboundMessageResponse> getRecentMessages(UUID organizationId, int limit) {
        return inboundMessageRepository
                .findByOrganizationIdOrderByReceivedAtDesc(
                        organizationId,
                        PageRequest.of(0, Math.max(1, Math.min(limit, 200)))
                )
                .stream()
                .map(InboundMessageResponse::from)
                .toList();
    }

    /**
     * Queues an ignored or failed email to be processed again. Ignored
     * emails are processed as real requests this time.
     */
    @Transactional
    public InboundMessageResponse reprocess(UUID organizationId, UUID messageId) {
        InboundMessage message = inboundMessageRepository
                .findByIdAndOrganizationId(messageId, organizationId)
                .orElseThrow(() -> new NotFoundException("Email not found: " + messageId));

        if (message.getStatus() != InboundMessageStatus.IGNORED
                && message.getStatus() != InboundMessageStatus.FAILED) {
            throw new IllegalStateException("Only ignored or failed emails can be processed again");
        }

        message.setAutomated(false);
        message.setStatus(InboundMessageStatus.RECEIVED);
        message.setStatusReason(null);
        message.setAttempts(0);
        message.setNextAttemptAt(null);

        return InboundMessageResponse.from(inboundMessageRepository.save(message));
    }

    @Transactional
    public EmailSettingsResponse getSettings(UUID organizationId) {
        if (!emailProperties.isInboundEnabled()) {
            return new EmailSettingsResponse(false, false, null, null);
        }

        Organization organization = organizationRepository.findById(organizationId).orElseThrow();
        String token = inboundAddressService.getOrCreateToken(organization);

        EmailSettingsResponse.GmailConfirmation confirmation = inboundMessageRepository
                .findFirstByOrganizationIdAndStatusReasonOrderByReceivedAtDesc(
                        organizationId,
                        InboundEmailProcessor.GMAIL_CONFIRMATION_REASON
                )
                .map(message -> new EmailSettingsResponse.GmailConfirmation(
                        message.getReceivedAt(),
                        message.getSubject(),
                        message.getTextBody()
                ))
                .orElse(null);

        return new EmailSettingsResponse(
                true,
                emailProperties.isSendingEnabled(),
                emailProperties.forwardingAddress(token),
                confirmation
        );
    }
}
