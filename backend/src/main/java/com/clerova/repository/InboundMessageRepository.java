package com.clerova.repository;

import com.clerova.domain.InboundMessage;
import com.clerova.domain.InboundMessageStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InboundMessageRepository
        extends JpaRepository<InboundMessage, UUID> {

    boolean existsByOrganizationIdAndDedupeKey(UUID organizationId, String dedupeKey);

    List<InboundMessage> findByOrganizationIdOrderByReceivedAtDesc(
            UUID organizationId,
            Pageable pageable
    );

    Optional<InboundMessage> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<InboundMessage> findByWorkItemIdOrderByReceivedAtAsc(UUID workItemId);

    /** Earlier messages this email may be replying to. */
    List<InboundMessage> findByOrganizationIdAndMessageIdHeaderInAndWorkItemIdIsNotNull(
            UUID organizationId,
            Collection<String> messageIds
    );

    Optional<InboundMessage> findFirstByOrganizationIdAndStatusReasonOrderByReceivedAtDesc(
            UUID organizationId,
            String statusReason
    );

    @Query("""
            select m.id from InboundMessage m
            where m.status = :status
              and (m.nextAttemptAt is null or m.nextAttemptAt <= :now)
            order by m.receivedAt
            """)
    List<UUID> findDueIds(
            @Param("status") InboundMessageStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    /**
     * Claims a message for processing. Returns 0 if another worker
     * already claimed it.
     */
    @Modifying
    @Query("""
            update InboundMessage m
            set m.status = com.clerova.domain.InboundMessageStatus.PROCESSING,
                m.attempts = m.attempts + 1,
                m.version = m.version + 1
            where m.id = :id
              and m.status = com.clerova.domain.InboundMessageStatus.RECEIVED
            """)
    int claim(@Param("id") UUID id);

    /** Messages left in PROCESSING by a crashed worker. */
    @Modifying
    @Query("""
            update InboundMessage m
            set m.status = com.clerova.domain.InboundMessageStatus.RECEIVED,
                m.version = m.version + 1
            where m.status = com.clerova.domain.InboundMessageStatus.PROCESSING
              and m.updatedAt < :cutoff
            """)
    int releaseStale(@Param("cutoff") Instant cutoff);
}
