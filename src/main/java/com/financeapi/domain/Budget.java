package com.financeapi.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "budgets", uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "month_year"}))
@Getter @Setter @NoArgsConstructor
public class Budget {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "month_year", nullable = false, length = 7)
    private String monthYear;

    @Column(name = "amount_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountLimit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
