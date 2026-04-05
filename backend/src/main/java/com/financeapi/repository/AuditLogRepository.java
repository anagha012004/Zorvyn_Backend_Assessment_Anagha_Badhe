package com.financeapi.repository;

import com.financeapi.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.timestamp ASC")
    List<AuditLog> findHistory(@Param("entityType") String entityType, @Param("entityId") String entityId);
}
