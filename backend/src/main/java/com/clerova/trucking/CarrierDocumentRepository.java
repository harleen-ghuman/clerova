package com.clerova.trucking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarrierDocumentRepository extends JpaRepository<CarrierDocument, UUID> {

    Optional<CarrierDocument> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<CarrierDocument> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndCreatedAtAfter(UUID organizationId, Instant after);
}
