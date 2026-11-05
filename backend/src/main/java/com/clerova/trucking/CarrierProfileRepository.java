package com.clerova.trucking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CarrierProfileRepository extends JpaRepository<CarrierProfile, UUID> {

    /** Locks the row, so two invoices made at once get one number each and a load gets one invoice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CarrierProfile c where c.organizationId = :organizationId")
    Optional<CarrierProfile> findForUpdate(@Param("organizationId") UUID organizationId);
}
