package com.clerova.trucking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrokerRepository extends JpaRepository<Broker, UUID> {

    Optional<Broker> findFirstByOrganizationIdAndMcNumber(UUID organizationId, String mcNumber);

    Optional<Broker> findFirstByOrganizationIdAndMcNumberIsNullAndNameIgnoreCase(UUID organizationId, String name);

    List<Broker> findByOrganizationIdOrderByNameAsc(UUID organizationId);
}
