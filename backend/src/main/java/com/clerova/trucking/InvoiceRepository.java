package com.clerova.trucking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Invoice> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<Invoice> findByLoadIdOrderByCreatedAtDesc(UUID loadId);

    boolean existsByOrganizationIdAndNumber(UUID organizationId, String number);
}
