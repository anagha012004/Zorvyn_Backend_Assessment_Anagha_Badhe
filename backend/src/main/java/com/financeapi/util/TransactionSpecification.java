package com.financeapi.util;

import com.financeapi.domain.Transaction;
import com.financeapi.domain.Transaction.TransactionType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TransactionSpecification {

    public static Specification<Transaction> filter(TransactionType type, Long categoryId,
                                                     LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (type != null) predicates.add(cb.equal(root.get("type"), type));
            if (categoryId != null) predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("date"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("date"), to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
