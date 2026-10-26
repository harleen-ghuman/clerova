package com.clerova.repository;

import com.clerova.domain.WorkItem;
import com.clerova.domain.WorkItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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

    Optional<WorkItem> findByIdAndOrganizationId(
            UUID id,
            UUID organizationId
    );
}
