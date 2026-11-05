package com.clerova.trucking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadRepository extends JpaRepository<Load, UUID> {

    @Query("select l from Load l left join fetch l.broker"
            + " where l.organizationId = :organizationId order by l.createdAt desc")
    List<Load> findForOrganization(@Param("organizationId") UUID organizationId);

    Optional<Load> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** The same load again, like a revised rate con. */
    Optional<Load> findFirstByOrganizationIdAndBrokerAndLoadNumber(UUID organizationId, Broker broker, String loadNumber);

    long countByBroker(Broker broker);
}
