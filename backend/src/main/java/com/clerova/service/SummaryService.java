package com.clerova.service;

import com.clerova.domain.MaintenanceRequestStatus;
import com.clerova.domain.OutboundMessage;
import com.clerova.domain.ProposedActionStatus;
import com.clerova.dto.WeekSummaryResponse;
import com.clerova.repository.InboundMessageRepository;
import com.clerova.repository.MaintenanceRequestRepository;
import com.clerova.repository.OutboundMessageRepository;
import com.clerova.repository.ProposedActionRepository;
import com.clerova.repository.WorkItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SummaryService {

    /** Rough time to read, sort and draft a reply to one request by hand. */
    static final int MINUTES_PER_REQUEST = 5;

    private final WorkItemRepository workItemRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final OutboundMessageRepository outboundMessageRepository;
    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final ProposedActionRepository proposedActionRepository;

    public SummaryService(
            WorkItemRepository workItemRepository,
            InboundMessageRepository inboundMessageRepository,
            OutboundMessageRepository outboundMessageRepository,
            MaintenanceRequestRepository maintenanceRequestRepository,
            ProposedActionRepository proposedActionRepository
    ) {
        this.workItemRepository = workItemRepository;
        this.inboundMessageRepository = inboundMessageRepository;
        this.outboundMessageRepository = outboundMessageRepository;
        this.maintenanceRequestRepository = maintenanceRequestRepository;
        this.proposedActionRepository = proposedActionRepository;
    }

    @Transactional(readOnly = true)
    public WeekSummaryResponse lastSevenDays(UUID organizationId) {
        Instant since = Instant.now().minus(Duration.ofDays(7));

        long requests = workItemRepository.countByOrganizationIdAndCreatedAtAfter(organizationId, since);
        List<OutboundMessage> replies =
                outboundMessageRepository.findByOrganizationIdAndSentAtAfter(organizationId, since);

        return new WeekSummaryResponse(
                requests,
                inboundMessageRepository.countByOrganizationIdAndReceivedAtAfter(organizationId, since),
                replies.size(),
                maintenanceRequestRepository.countByOrganizationIdAndStatusAndUpdatedAtAfter(
                        organizationId, MaintenanceRequestStatus.COMPLETED, since),
                medianReplyMinutes(organizationId, replies),
                requests * MINUTES_PER_REQUEST,
                MINUTES_PER_REQUEST,
                workItemRepository.countByOrganizationId(organizationId),
                proposedActionRepository.countByWorkItem_OrganizationIdAndStatusIn(
                        organizationId,
                        List.of(ProposedActionStatus.APPROVED, ProposedActionStatus.EXECUTED)
                )
        );
    }

    /** Median minutes from a request arriving to its first reply. */
    private Long medianReplyMinutes(UUID organizationId, List<OutboundMessage> replies) {
        Map<UUID, Instant> firstReply = replies.stream()
                .filter(reply -> reply.getWorkItemId() != null)
                .collect(Collectors.toMap(
                        OutboundMessage::getWorkItemId,
                        OutboundMessage::getSentAt,
                        (a, b) -> a.isBefore(b) ? a : b
                ));

        List<Long> minutes = workItemRepository.findAllById(firstReply.keySet()).stream()
                .filter(item -> organizationId.equals(item.getOrganizationId()))
                .map(item -> Duration.between(item.getCreatedAt(), firstReply.get(item.getId())).toMinutes())
                .filter(value -> value >= 0)
                .sorted(Comparator.naturalOrder())
                .toList();

        return minutes.isEmpty() ? null : minutes.get(minutes.size() / 2);
    }
}
