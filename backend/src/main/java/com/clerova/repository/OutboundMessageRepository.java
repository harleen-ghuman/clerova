package com.clerova.repository;

import com.clerova.domain.OutboundMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

public interface OutboundMessageRepository
        extends JpaRepository<OutboundMessage, UUID> {

    List<OutboundMessage> findByWorkItemIdOrderBySentAtAsc(UUID workItemId);

    List<OutboundMessage> findByOrganizationIdAndSentAtAfter(UUID organizationId, Instant after);

    boolean existsByProposedActionId(UUID proposedActionId);
}
