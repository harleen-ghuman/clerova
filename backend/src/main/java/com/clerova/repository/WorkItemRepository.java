package com.clerova.repository;

import com.clerova.domain.WorkItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkItemRepository extends JpaRepository<WorkItem, UUID> {
}
