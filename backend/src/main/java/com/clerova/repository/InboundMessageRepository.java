package com.clerova.repository;

import com.clerova.domain.InboundMessage;
import com.clerova.domain.InboundMessageStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
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

    long countByOrganizationIdAndReceivedAtAfter(UUID organizationId, Instant after);

    long countByWorkItemIdAndReceivedAtAfter(UUID workItemId, Instant after);

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
     * already claimed it. Stamps the time, so a message that waited a long
     * time isn't taken for a stale claim (releaseStale) while it runs.
     */
    @Modifying
    @Query("""
            update InboundMessage m
            set m.status = com.clerova.domain.InboundMessageStatus.PROCESSING,
                m.attempts = m.attempts + 1,
                m.updatedAt = :now,
                m.version = m.version + 1
            where m.id = :id
              and m.status = com.clerova.domain.InboundMessageStatus.RECEIVED
            """)
    int claim(@Param("id") UUID id, @Param("now") Instant now);

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

    /**
     * Emails waiting for next month's requests (or a retry) become due now,
     * e.g. after an upgrade.
     */
    @Modifying
    @Transactional
    @Query("""
            update InboundMessage m
            set m.nextAttemptAt = :now,
                m.version = m.version + 1
            where m.organizationId = :organizationId
              and m.status = com.clerova.domain.InboundMessageStatus.RECEIVED
              and m.nextAttemptAt > :now
            """)
    int releaseWaiting(@Param("organizationId") UUID organizationId, @Param("now") Instant now);

    /** Attachment names, read without loading the message's collection. */
    @Query("select a.fileName from InboundAttachment a where a.inboundMessage.id = :messageId")
    List<String> findAttachmentNames(@Param("messageId") UUID messageId);
}
