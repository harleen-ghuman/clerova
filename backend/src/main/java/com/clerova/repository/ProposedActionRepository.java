package com.clerova.repository;

import com.clerova.domain.ProposedAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProposedActionRepository extends JpaRepository<ProposedAction, UUID> {
    List<ProposedAction> findByWorkItemId(UUID workItemId);
}
