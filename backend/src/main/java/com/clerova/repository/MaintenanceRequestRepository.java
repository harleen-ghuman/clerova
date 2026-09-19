package com.clerova.repository;

import com.clerova.domain.MaintenanceRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceRequestRepository
        extends JpaRepository<MaintenanceRequest, UUID> {

    Optional<MaintenanceRequest> findByProposedActionId(
            UUID proposedActionId
    );

    List<MaintenanceRequest> findByWorkItemIdOrderByCreatedAtDesc(
            UUID workItemId
    );
}
