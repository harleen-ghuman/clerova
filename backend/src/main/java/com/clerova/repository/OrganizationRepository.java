package com.clerova.repository;

import com.clerova.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository
        extends JpaRepository<Organization, UUID> {

    Optional<Organization> findByInboundEmailToken(String inboundEmailToken);

    boolean existsByInboundEmailToken(String inboundEmailToken);

    Optional<Organization> findByStripeCustomerId(String stripeCustomerId);

    long countByFoundingCustomerTrue();

    /**
     * Atomically counts one AI request against the organization's monthly
     * limit, resetting the count when a new month starts.
     *
     * @return 1 if the request was counted, 0 if the limit is reached
     */
    @Modifying
    @Query("""
            update Organization o
            set o.aiUsageCount = case
                    when o.aiUsagePeriod = :period then o.aiUsageCount + 1
                    else 1
                end,
                o.aiUsagePeriod = :period
            where o.id = :id
              and o.aiMonthlyLimit > 0
              and (o.aiUsagePeriod is null
                   or o.aiUsagePeriod <> :period
                   or o.aiUsageCount < o.aiMonthlyLimit)
            """)
    int tryConsumeAiRequest(
            @Param("id") UUID id,
            @Param("period") String period
    );
}
