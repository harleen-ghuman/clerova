package com.clerova.service;

import com.clerova.domain.WorkItemEvent;
import com.clerova.domain.WorkItemEventType;
import com.clerova.repository.WorkItemEventRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Records the audit trail for work items: what happened and who did it.
 */
@Service
public class WorkItemEventService {

    private final WorkItemEventRepository workItemEventRepository;

    public WorkItemEventService(WorkItemEventRepository workItemEventRepository) {
        this.workItemEventRepository = workItemEventRepository;
    }

    /**
     * @param actorUserId the user who acted, or null when Clerova acted on
     *                    its own (for example when an email arrived)
     */
    public void record(
            UUID organizationId,
            UUID workItemId,
            WorkItemEventType type,
            String detail,
            UUID actorUserId
    ) {
        WorkItemEvent event = new WorkItemEvent();

        event.setOrganizationId(organizationId);
        event.setWorkItemId(workItemId);
        event.setType(type);
        event.setDetail(detail);
        event.setActorUserId(actorUserId);

        workItemEventRepository.save(event);
    }

    public List<WorkItemEvent> getEvents(UUID workItemId) {
        return workItemEventRepository.findByWorkItemIdOrderByCreatedAtAsc(workItemId);
    }
}
