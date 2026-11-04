package com.clerova.trucking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadReplyRepository extends JpaRepository<LoadReply, UUID> {

    List<LoadReply> findByLoadIdOrderByCreatedAtAsc(UUID loadId);

    Optional<LoadReply> findByIdAndLoadId(UUID id, UUID loadId);

    long countByLoadIdAndStatus(UUID loadId, ReplyStatus status);

    @Query("select r.loadId, count(r) from LoadReply r where r.loadId in :loadIds and r.status = :status group by r.loadId")
    List<Object[]> countByLoad(@Param("loadIds") Collection<UUID> loadIds, @Param("status") ReplyStatus status);

    /** An older draft is replaced by the newest one. */
    @Modifying
    @Query("update LoadReply r set r.status = com.clerova.trucking.ReplyStatus.DISMISSED"
            + " where r.loadId = :loadId and r.status = com.clerova.trucking.ReplyStatus.DRAFT")
    int dismissDrafts(@Param("loadId") UUID loadId);
}
