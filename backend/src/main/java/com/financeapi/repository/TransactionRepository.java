package com.financeapi.repository;

import com.financeapi.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Page<Transaction> findByDeletedFalse(Pageable pageable);

    Page<Transaction> findByDeletedTrue(Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = 'INCOME' AND t.deleted = false")
    BigDecimal sumIncome();

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = 'EXPENSE' AND t.deleted = false")
    BigDecimal sumExpenses();

    @Query("SELECT t.category.name, COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.deleted = false AND t.category IS NOT NULL GROUP BY t.category.name")
    List<Object[]> sumByCategory();

    @Query(value = "SELECT TO_CHAR(date, 'YYYY-MM') as month, COALESCE(SUM(amount), 0) as total, type " +
           "FROM transactions WHERE is_deleted = false AND date >= CURRENT_DATE - INTERVAL '365 days' " +
           "GROUP BY TO_CHAR(date, 'YYYY-MM'), type ORDER BY 1", nativeQuery = true)
    List<Object[]> monthlyTrends();

    @Query("SELECT AVG(t.amount) FROM Transaction t WHERE t.createdBy.id = :userId " +
           "AND t.category.id = :categoryId AND t.deleted = false")
    Optional<BigDecimal> avgAmountByUserAndCategory(@Param("userId") Long userId,
                                                     @Param("categoryId") Long categoryId);

    @Query(value = "SELECT STDDEV(CAST(amount AS double precision)) FROM transactions " +
           "WHERE created_by = :userId AND category_id = :categoryId AND is_deleted = false", nativeQuery = true)
    Optional<Double> stddevAmountByUserAndCategory(@Param("userId") Long userId,
                                                    @Param("categoryId") Long categoryId);

    // Velocity: sum of amounts for a user in the last N hours
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.createdBy.id = :userId AND t.deleted = false AND t.createdAt >= :since")
    BigDecimal sumAmountByUserSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    // Forecast: daily expense totals per category for a user over the last N days
    @Query(value = "SELECT category_id, date, COALESCE(SUM(amount), 0) " +
           "FROM transactions WHERE created_by = :userId AND type = 'EXPENSE' " +
           "AND is_deleted = false AND date >= :from " +
           "GROUP BY category_id, date ORDER BY date", nativeQuery = true)
    List<Object[]> dailyExpenseByCategory(@Param("userId") Long userId, @Param("from") LocalDate from);

    // Audit history: all versions of a transaction from audit_logs
    @Query(value = "SELECT action, old_value, new_value, timestamp FROM audit_logs " +
           "WHERE entity_type = 'Transaction' AND entity_id = :entityId ORDER BY timestamp",
           nativeQuery = true)
    List<Object[]> findAuditHistory(@Param("entityId") String entityId);
}
