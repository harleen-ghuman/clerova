package com.clerova.repository;

import com.clerova.domain.WorkItem;
import com.clerova.domain.WorkItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemRepository
        extends JpaRepository<WorkItem, UUID> {

    List<WorkItem> findByOrganizationIdAndStatusNotInOrderByCreatedAtDesc(
            UUID organizationId,
            Collection<WorkItemStatus> statuses
    );

    List<WorkItem> findTop100ByOrganizationIdAndStatusInAndUpdatedAtAfterOrderByUpdatedAtDesc(
            UUID organizationId,
            Collection<WorkItemStatus> statuses,
            Instant updatedAfter
    );

    long countByOrganizationIdAndCreatedAtAfter(UUID organizationId, Instant after);

    long countByOrganizationId(UUID organizationId);

    Optional<WorkItem> findByIdAndOrganizationId(
            UUID id,
            UUID organizationId
    );

    /** @return 0 if the limit is already reached */
    @Transactional
    @Modifying
    @Query("update WorkItem w set w.suggestionsUsed = w.suggestionsUsed + 1"
            + " where w.id = :id and w.suggestionsUsed < :max")
    int useSuggestion(@Param("id") UUID id, @Param("max") int max);

    /** Gives back a suggestion whose AI call failed. */
    @Transactional
    @Modifying
    @Query("update WorkItem w set w.suggestionsUsed = w.suggestionsUsed - 1"
            + " where w.id = :id and w.suggestionsUsed > 0")
    int refundSuggestion(@Param("id") UUID id);

    /** A new email, either way, starts a fresh set of suggestions. */
    @Transactional
    @Modifying
    @Query("update WorkItem w set w.suggestionsUsed = 0"
            + " where w.id = :id and w.suggestionsUsed <> 0")
    int resetSuggestions(@Param("id") UUID id);
}
