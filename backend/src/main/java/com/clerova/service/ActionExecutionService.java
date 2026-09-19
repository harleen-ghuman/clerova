package com.clerova.service;

import com.clerova.domain.MaintenanceRequest;
import com.clerova.domain.ProposedAction;
import com.clerova.repository.MaintenanceRequestRepository;
import org.springframework.stereotype.Service;

@Service
public class ActionExecutionService {

    private final MaintenanceRequestRepository maintenanceRequestRepository;

    public ActionExecutionService(
            MaintenanceRequestRepository maintenanceRequestRepository
    ) {
        this.maintenanceRequestRepository =
                maintenanceRequestRepository;
    }

    public MaintenanceRequest executeMaintenanceRequest(
            ProposedAction proposedAction
    ) {

        return maintenanceRequestRepository
                .findByProposedActionId(proposedAction.getId())
                .orElseGet(() -> {
                    MaintenanceRequest request =
                            new MaintenanceRequest();

                    request.setProposedAction(proposedAction);
                    request.setWorkItem(proposedAction.getWorkItem());

                    request.setOrganizationId(
                            proposedAction
                                    .getWorkItem()
                                    .getOrganizationId()
                    );

                    request.setAddress(
                            proposedAction
                                    .getWorkItem()
                                    .getAddress()
                    );

                    request.setDescription(
                            proposedAction
                                    .getWorkItem()
                                    .getSummary()
                    );

                    request.setPriority(
                            proposedAction
                                    .getWorkItem()
                                    .getPriority()
                    );

                    return maintenanceRequestRepository.save(
                            request
                    );
                });
    }
}
