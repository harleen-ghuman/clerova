package com.clerova.repository;

import com.clerova.domain.WorkItemEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkItemEventRepository
        extends JpaRepository<WorkItemEvent, UUID> {

    List<WorkItemEvent> findByWorkItemIdOrderByCreatedAtAsc(UUID workItemId);
}
