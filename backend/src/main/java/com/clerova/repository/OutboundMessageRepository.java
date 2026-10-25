package com.clerova.repository;

import com.clerova.domain.OutboundMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboundMessageRepository
        extends JpaRepository<OutboundMessage, UUID> {

    List<OutboundMessage> findByWorkItemIdOrderBySentAtAsc(UUID workItemId);

    boolean existsByProposedActionId(UUID proposedActionId);
}
