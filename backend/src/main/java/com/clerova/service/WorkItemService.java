package com.clerova.service;

import com.clerova.ai.AiService;
import com.clerova.domain.WorkItem;
import com.clerova.dto.ExtractionResult;
import com.clerova.repository.WorkItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class WorkItemService {

    private final AiService aiService;
    private final WorkItemRepository workItemRepository;

    public WorkItemService(
            AiService aiService,
            WorkItemRepository workItemRepository
    ) {
        this.aiService = aiService;
        this.workItemRepository = workItemRepository;
    }

    public WorkItem createWorkItem(
            String organizationId,
            String message
    ) {

        ExtractionResult extraction =
                aiService.extract(message);

        WorkItem workItem = new WorkItem();

        workItem.setOrganizationId(organizationId);
        workItem.setOriginalMessage(message);

        workItem.setCategory(extraction.category);
        workItem.setPriority(extraction.priority);
        workItem.setSummary(extraction.summary);
        workItem.setAddress(extraction.address);

        return workItemRepository.save(workItem);
    }

    public List<WorkItem> getAllWorkItems() {
        return workItemRepository.findAll();
    }

    public WorkItem getWorkItem(UUID id) {
        return workItemRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("WorkItem not found: " + id)
                );
    }
}
