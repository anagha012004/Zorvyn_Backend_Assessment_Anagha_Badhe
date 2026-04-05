package com.financeapi.repository;

import com.financeapi.domain.TransactionDna;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface TransactionDnaRepository extends JpaRepository<TransactionDna, Long> {

    @Query("SELECT COUNT(d) > 0 FROM TransactionDna d WHERE d.dnaHash = :hash AND d.createdAt >= :since")
    boolean existsByDnaHashSince(@Param("hash") String hash, @Param("since") LocalDateTime since);
}
