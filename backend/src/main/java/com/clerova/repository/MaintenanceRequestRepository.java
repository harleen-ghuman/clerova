package com.clerova.repository;

import com.clerova.domain.MaintenanceRequest;
import com.clerova.domain.MaintenanceRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceRequestRepository
        extends JpaRepository<MaintenanceRequest, UUID> {

    List<MaintenanceRequest> findByOrganizationIdOrderByCreatedAtDesc(
            UUID organizationId
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

    boolean existsByWorkItemIdAndStatusIn(
            UUID workItemId,
            Collection<MaintenanceRequestStatus> statuses
    );
}
