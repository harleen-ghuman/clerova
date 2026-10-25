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

    @Query("""
            select pa from ProposedAction pa
            where pa.workItem.status not in :statuses
            order by pa.createdAt desc
            """)
    List<ProposedAction> findForWorkItemsNotInStatusesOrderByCreatedAtDesc(
            @Param("statuses") Collection<WorkItemStatus> statuses
    );
}
