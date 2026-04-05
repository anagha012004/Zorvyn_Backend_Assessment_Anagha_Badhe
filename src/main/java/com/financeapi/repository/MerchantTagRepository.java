package com.financeapi.repository;

import com.financeapi.domain.MerchantTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MerchantTagRepository extends JpaRepository<MerchantTag, Long> {

    Optional<MerchantTag> findByTransactionId(Long transactionId);

    @Query("SELECT m.merchantName, COALESCE(SUM(m.transaction.amount), 0) as total " +
           "FROM MerchantTag m WHERE m.transaction.deleted = false " +
           "AND m.transaction.date >= :from " +
           "GROUP BY m.merchantName ORDER BY total DESC")
    List<Object[]> topMerchantsSince(@Param("from") LocalDate from);
}
