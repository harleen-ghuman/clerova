package com.clerova.service;

import com.clerova.domain.MaintenanceRequest;
import com.clerova.domain.MaintenanceRequestStatus;
import com.clerova.domain.ProposedAction;
import com.clerova.domain.WorkItem;
import com.clerova.domain.WorkItemStatus;
import com.clerova.repository.MaintenanceRequestRepository;
import com.clerova.repository.WorkItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ActionExecutionService {

    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final WorkItemRepository workItemRepository;

    public ActionExecutionService(
            MaintenanceRequestRepository maintenanceRequestRepository,
            WorkItemRepository workItemRepository
    ) {
        this.maintenanceRequestRepository = maintenanceRequestRepository;
        this.workItemRepository = workItemRepository;
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
            UUID id,
            MaintenanceRequestStatus status,
            String notes
    ) {
        MaintenanceRequest request =
                maintenanceRequestRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Maintenance request not found: " + id
                                )
                        );

        if (status != null) {
            request.setStatus(status);
        }

        if (notes != null) {
            request.setNotes(notes.trim());
        }

        if (status == MaintenanceRequestStatus.COMPLETED ||
                status == MaintenanceRequestStatus.CANCELLED) {

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