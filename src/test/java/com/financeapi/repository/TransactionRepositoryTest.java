package com.financeapi.repository;

import com.financeapi.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransactionRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired TransactionRepository transactionRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired CategoryRepository categoryRepository;

    private User user;
    private Category category;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();

        Role role = new Role();
        role.setName(Role.RoleName.VIEWER);
        role = roleRepository.save(role);

        user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hash");
        user.setFullName("Test User");
        user.setRoles(Set.of(role));
        user = userRepository.save(user);

        category = new Category();
        category.setName("Food");
        category = categoryRepository.save(category);
    }

    @Test
    void save_and_findById() {
        Transaction t = new Transaction();
        t.setAmount(BigDecimal.valueOf(150));
        t.setType(Transaction.TransactionType.EXPENSE);
        t.setDate(LocalDate.now());
        t.setCreatedBy(user);
        t.setCategory(category);
        Transaction saved = transactionRepository.save(t);

        assertThat(transactionRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void softDelete_hiddenFromActiveQuery() {
        Transaction t = new Transaction();
        t.setAmount(BigDecimal.valueOf(200));
        t.setType(Transaction.TransactionType.INCOME);
        t.setDate(LocalDate.now());
        t.setCreatedBy(user);
        t.setDeleted(true);
        transactionRepository.save(t);

        var page = transactionRepository.findByDeletedFalse(PageRequest.of(0, 10));
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void sumIncome_and_sumExpenses() {
        Transaction income = new Transaction();
        income.setAmount(BigDecimal.valueOf(1000));
        income.setType(Transaction.TransactionType.INCOME);
        income.setDate(LocalDate.now());
        income.setCreatedBy(user);
        transactionRepository.save(income);

        Transaction expense = new Transaction();
        expense.setAmount(BigDecimal.valueOf(500));
        expense.setType(Transaction.TransactionType.EXPENSE);
        expense.setDate(LocalDate.now());
        expense.setCreatedBy(user);
        transactionRepository.save(expense);

        assertThat(transactionRepository.sumIncome()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(transactionRepository.sumExpenses()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    void idempotencyKey_returnsExistingTransaction() {
        Transaction t = new Transaction();
        t.setAmount(BigDecimal.valueOf(300));
        t.setType(Transaction.TransactionType.EXPENSE);
        t.setDate(LocalDate.now());
        t.setCreatedBy(user);
        t.setIdempotencyKey("unique-key-123");
        transactionRepository.save(t);

        assertThat(transactionRepository.findByIdempotencyKey("unique-key-123")).isPresent();
        assertThat(transactionRepository.findByIdempotencyKey("other-key")).isEmpty();
    }
}
