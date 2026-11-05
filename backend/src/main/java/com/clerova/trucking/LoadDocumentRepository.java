package com.clerova.trucking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadDocumentRepository extends JpaRepository<LoadDocument, UUID> {

    Optional<LoadDocument> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<LoadDocument> findByLoadIdOrderByCreatedAtAsc(UUID loadId);

    List<LoadDocument> findByOrganizationIdAndLoadIdIsNullOrderByCreatedAtDesc(UUID organizationId);

    long countByOrganizationIdAndCreatedAtAfter(UUID organizationId, Instant after);
}
