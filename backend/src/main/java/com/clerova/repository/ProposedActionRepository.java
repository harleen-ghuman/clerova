package com.clerova.repository;

import com.clerova.domain.ProposedAction;
import com.clerova.domain.WorkItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProposedActionRepository
        extends JpaRepository<ProposedAction, UUID> {

    List<ProposedAction> findByWorkItemIdOrderByCreatedAtDesc(
            UUID workItemId
    );

    Optional<ProposedAction> findFirstByWorkItemIdOrderByCreatedAtDesc(
            UUID workItemId
    );

    Optional<ProposedAction> findByIdAndWorkItemOrganizationId(
            UUID id,
            UUID organizationId
    );

    @Query("""
            select pa from ProposedAction pa
            where pa.workItem.organizationId = :organizationId
              and pa.workItem.status not in :statuses
            order by pa.createdAt desc
            """)
    List<ProposedAction> findForOpenWorkItemsOrderByCreatedAtDesc(
            @Param("organizationId") UUID organizationId,
            @Param("statuses") Collection<WorkItemStatus> statuses
    );
}
