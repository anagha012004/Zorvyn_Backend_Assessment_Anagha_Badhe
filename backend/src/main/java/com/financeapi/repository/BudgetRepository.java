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

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.category.id = :categoryId AND t.type = 'EXPENSE' AND t.deleted = false " +
           "AND FUNCTION('TO_CHAR', t.date, 'YYYY-MM') = :monthYear")
    java.math.BigDecimal sumSpendByCategoryAndMonth(@Param("categoryId") Long categoryId,
                                                     @Param("monthYear") String monthYear);
}
