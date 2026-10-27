package com.clerova.repository;

import com.clerova.domain.InboundAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InboundAttachmentRepository extends JpaRepository<InboundAttachment, UUID> {

    @Query("select a from InboundAttachment a"
            + " where a.id = :id and a.inboundMessage.organizationId = :organizationId")
    Optional<InboundAttachment> findInOrganization(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId
    );
}
