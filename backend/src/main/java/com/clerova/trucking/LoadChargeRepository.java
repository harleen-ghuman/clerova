package com.clerova.trucking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadChargeRepository extends JpaRepository<LoadCharge, UUID> {

    List<LoadCharge> findByLoadIdOrderByCreatedAtAsc(UUID loadId);

    Optional<LoadCharge> findByIdAndLoadId(UUID id, UUID loadId);
}
