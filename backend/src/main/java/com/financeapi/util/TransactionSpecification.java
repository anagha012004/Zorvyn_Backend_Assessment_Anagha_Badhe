package com.financeapi.util;

import com.financeapi.domain.Transaction;
import com.financeapi.domain.Transaction.TransactionType;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TransactionSpecification {

    public static Specification<Transaction> filter(TransactionType type, Long categoryId,
                                                     LocalDate from, LocalDate to, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));

            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("date"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("date"), to));
            }

            // Only join category when we actually need to filter or search by it
            if (categoryId != null || (search != null && !search.isBlank())) {
                var categoryJoin = root.join("category", JoinType.LEFT);
                if (categoryId != null) {
                    predicates.add(cb.equal(categoryJoin.get("id"), categoryId));
                }
                if (search != null && !search.isBlank()) {
                    String pattern = "%" + search.toLowerCase() + "%";
                    predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("notes"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(categoryJoin.get("name"), "")), pattern)
                    ));
                }
            } else if (search != null && !search.isBlank()) {
                // search on notes only when no category join needed
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("notes"), "")), pattern));
            }

            // Only set distinct on the data query, not the count query
            // Hibernate 6 throws SemanticException if distinct is set on count queries
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Transaction> filter(TransactionType type, Long categoryId,
                                                     LocalDate from, LocalDate to) {
        return filter(type, categoryId, from, to, null);
    }
}
