package com.clerova.repository;

import com.clerova.domain.MaintenanceRequest;
import com.clerova.domain.MaintenanceRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

public interface MaintenanceRequestRepository
        extends JpaRepository<MaintenanceRequest, UUID> {

    List<MaintenanceRequest> findByOrganizationIdOrderByCreatedAtDesc(
            UUID organizationId
    );

    /** Open and in-progress jobs, plus jobs finished since the given time. */
    @Query("""
            select m from MaintenanceRequest m
            where m.organizationId = :organizationId
              and (m.status in :active or m.updatedAt > :finishedAfter)
            order by m.createdAt desc
            """)
    List<MaintenanceRequest> findBoard(
            @Param("organizationId") UUID organizationId,
            @Param("active") Collection<MaintenanceRequestStatus> active,
            @Param("finishedAfter") Instant finishedAfter
    );

    Optional<MaintenanceRequest> findByIdAndOrganizationId(
            UUID id,
            UUID organizationId
    );

    Optional<MaintenanceRequest> findByProposedActionId(
            UUID proposedActionId
    );

    List<MaintenanceRequest> findByWorkItemIdAndOrganizationIdOrderByCreatedAtDesc(
            UUID workItemId,
            UUID organizationId
    );

    long countByOrganizationIdAndStatusAndUpdatedAtAfter(
            UUID organizationId,
            MaintenanceRequestStatus status,
            Instant after
    );

    boolean existsByWorkItemIdAndStatusIn(
            UUID workItemId,
            Collection<MaintenanceRequestStatus> statuses
    );
}
