package com.financeapi.repository;

import com.financeapi.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByMonthYear(String monthYear);

    Optional<Budget> findByCategoryIdAndMonthYear(Long categoryId, String monthYear);

    @Query(value = "SELECT COALESCE(SUM(t.amount), 0) FROM transactions t " +
           "WHERE t.category_id = :categoryId AND t.type = 'EXPENSE' AND t.is_deleted = false " +
           "AND TO_CHAR(t.date, 'YYYY-MM') = :monthYear", nativeQuery = true)
    java.math.BigDecimal sumSpendByCategoryAndMonth(@Param("categoryId") Long categoryId,
                                                     @Param("monthYear") String monthYear);
}
