package com.clerova.service;

import com.clerova.exception.ConflictException;
import com.clerova.domain.MaintenanceRequest;
import com.clerova.domain.MaintenanceRequestStatus;
import com.clerova.domain.ProposedAction;
import com.clerova.domain.WorkItem;
import com.clerova.domain.WorkItemEventType;
import com.clerova.domain.WorkItemStatus;
import com.clerova.exception.NotFoundException;
import com.clerova.repository.MaintenanceRequestRepository;
import com.clerova.repository.WorkItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ActionExecutionService {

    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemEventService workItemEventService;

    public ActionExecutionService(
            MaintenanceRequestRepository maintenanceRequestRepository,
            WorkItemRepository workItemRepository,
            WorkItemEventService workItemEventService
    ) {
        this.maintenanceRequestRepository = maintenanceRequestRepository;
        this.workItemRepository = workItemRepository;
        this.workItemEventService = workItemEventService;
    }

    public MaintenanceRequest executeMaintenanceRequest(
            ProposedAction proposedAction
    ) {
        return maintenanceRequestRepository
                .findByProposedActionId(proposedAction.getId())
                .orElseGet(() -> {
                    MaintenanceRequest request =
                            new MaintenanceRequest();

                    WorkItem workItem =
                            proposedAction.getWorkItem();

                    request.setProposedAction(proposedAction);
                    request.setWorkItem(workItem);

                    request.setOrganizationId(
                            workItem.getOrganizationId()
                    );

                    request.setAddress(
                            workItem.getAddress()
                    );

                    request.setDescription(
                            workItem.getSummary()
                    );

                    request.setPriority(
                            workItem.getPriority()
                    );

                    return maintenanceRequestRepository.save(
                            request
                    );
                });
    }

    @Transactional
    public MaintenanceRequest updateMaintenanceRequest(
            UUID organizationId,
            UUID id,
            MaintenanceRequestStatus status,
            String notes,
            UUID actorUserId
    ) {
        MaintenanceRequest request =
                maintenanceRequestRepository.findByIdAndOrganizationId(id, organizationId)
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "Maintenance request not found: " + id
                                )
                        );

        MaintenanceRequestStatus currentStatus = request.getStatus();

        boolean statusChanged =
                status != null && status != currentStatus;

        // Notes can still be saved on a finished job, e.g. a save that
        // arrives just after "Mark done"
        if (currentStatus.isTerminal() && statusChanged) {
            throw new ConflictException(
                    "This job is already done or cancelled."
            );
        }

        if (statusChanged && !currentStatus.canTransitionTo(status)) {
            throw new ConflictException(
                    "This job can't move to that step. Refresh and try again."
            );
        }

        if (statusChanged) {
            request.setStatus(status);

            workItemEventService.record(
                    organizationId,
                    request.getWorkItem().getId(),
                    WorkItemEventType.MAINTENANCE_UPDATED,
                    switch (status) {
                        case OPEN -> "Job reopened";
                        case IN_PROGRESS -> "Job started";
                        case COMPLETED -> "Job done";
                        case CANCELLED -> "Job cancelled";
                    },
                    actorUserId
            );
        }

        if (notes != null) {
            String cleaned = notes.trim().isEmpty() ? null : notes.trim();

            if (!java.util.Objects.equals(cleaned, request.getNotes())) {
                request.setNotes(cleaned);

                workItemEventService.record(
                        organizationId,
                        request.getWorkItem().getId(),
                        WorkItemEventType.MAINTENANCE_UPDATED,
                        cleaned == null ? "Job notes cleared" : "Job notes updated",
                        actorUserId
                );
            }
        }

        if (statusChanged && status.isTerminal()) {

            WorkItem workItem = request.getWorkItem();

            if (status == MaintenanceRequestStatus.COMPLETED) {
                workItem.setStatus(WorkItemStatus.COMPLETED);
            } else {
                workItem.setStatus(WorkItemStatus.CANCELLED);
            }

            workItemRepository.save(workItem);
        }

        return maintenanceRequestRepository.save(request);
    }
}