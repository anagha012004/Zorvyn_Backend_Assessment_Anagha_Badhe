package com.financeapi.config;

import com.financeapi.domain.*;
import com.financeapi.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository         userRepository;
    private final RoleRepository         roleRepository;
    private final CategoryRepository     categoryRepository;
    private final TransactionRepository  transactionRepository;
    private final BudgetRepository       budgetRepository;
    private final CacheManager           cacheManager;
    private final PasswordEncoder        passwordEncoder;
    private final RedisConnectionFactory redisConnectionFactory;

    @Bean
    public ApplicationRunner seedData() {
        return args -> {
            log.info("[DataInitializer] Starting seed...");
            try {
                evictCaches();
                fixPasswords();
                seedUser("analyst@finance.com", "Alice Analyst", Role.RoleName.ANALYST, "analyst123");
                seedUser("viewer@finance.com",  "Bob Viewer",    Role.RoleName.VIEWER,  "viewer123");

                if (transactionRepository.findByIdempotencyKey("seed-admin-salary-1").isEmpty()) {
                    log.info("[DataInitializer] Seeding transactions...");
                    seedTransactions();
                } else {
                    log.info("[DataInitializer] Transactions already seeded, skipping.");
                }

                seedBudgets();
                evictCaches();
                log.info("[DataInitializer] Seed complete.");
            } catch (Exception e) {
                log.error("[DataInitializer] Seed failed: {}", e.getMessage(), e);
            }
        };
    }

    private void fixPasswords() {
        fix("admin@finance.com",   "admin123");
        fix("analyst@finance.com", "analyst123");
        fix("viewer@finance.com",  "viewer123");
    }

    private void fix(String email, String plainPassword) {
        userRepository.findByEmail(email).ifPresent(u -> {
            if (!passwordEncoder.matches(plainPassword, u.getPasswordHash())) {
                u.setPasswordHash(passwordEncoder.encode(plainPassword));
                userRepository.save(u);
                log.info("[DataInitializer] Fixed password for {}", email);
            }
        });
    }

    private void seedUser(String email, String fullName, Role.RoleName roleName, String password) {
        if (userRepository.findByEmail(email).isPresent()) return;
        Role viewer = roleRepository.findByName(Role.RoleName.VIEWER).orElseThrow();
        Role role   = roleRepository.findByName(roleName).orElseThrow();
        User user   = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setTimezone("Asia/Kolkata");
        user.setRoles(roleName == Role.RoleName.ANALYST ? Set.of(viewer, role) : Set.of(viewer));
        userRepository.save(user);
        log.info("[DataInitializer] Created user: {}", email);
    }

    private void seedTransactions() {
        LocalDate today = LocalDate.now();
        Map<String, Long> catIds = buildCategoryMap();
        Map<String, Long> userIds = buildUserMap();

        // {idempotencyKey, amount, type, categoryName, daysAgo, notes, userEmail}
        List<Object[]> rows = List.of(
            new Object[]{"seed-admin-salary-1",  85000, "INCOME",  "Salary",        30, "Monthly salary June",       "admin@finance.com"},
            new Object[]{"seed-admin-salary-2",  85000, "INCOME",  "Salary",        60, "Monthly salary May",        "admin@finance.com"},
            new Object[]{"seed-admin-food-1",      450, "EXPENSE", "Food",           1, "Swiggy dinner order",       "admin@finance.com"},
            new Object[]{"seed-admin-food-2",      320, "EXPENSE", "Food",           3, "Zomato lunch",              "admin@finance.com"},
            new Object[]{"seed-admin-food-3",      180, "EXPENSE", "Food",           5, "Grocery BigBasket",         "admin@finance.com"},
            new Object[]{"seed-admin-food-4",      560, "EXPENSE", "Food",           8, "Swiggy weekend binge",      "admin@finance.com"},
            new Object[]{"seed-admin-food-5",      210, "EXPENSE", "Food",          12, "Zomato breakfast",          "admin@finance.com"},
            new Object[]{"seed-admin-food-6",      390, "EXPENSE", "Food",          16, "Swiggy order",              "admin@finance.com"},
            new Object[]{"seed-admin-food-7",      270, "EXPENSE", "Food",          22, "Zomato dinner",             "admin@finance.com"},
            new Object[]{"seed-admin-food-anomaly",12000,"EXPENSE","Food",           0, "Catering for office party", "admin@finance.com"},
            new Object[]{"seed-admin-trans-1",     250, "EXPENSE", "Transport",      2, "Uber cab to airport",       "admin@finance.com"},
            new Object[]{"seed-admin-trans-2",     120, "EXPENSE", "Transport",      4, "Ola ride",                  "admin@finance.com"},
            new Object[]{"seed-admin-trans-3",    3200, "EXPENSE", "Transport",     15, "Petrol refill BPCL",        "admin@finance.com"},
            new Object[]{"seed-admin-trans-4",     180, "EXPENSE", "Transport",     25, "Uber ride",                 "admin@finance.com"},
            new Object[]{"seed-admin-ent-1",       649, "EXPENSE", "Entertainment",  7, "Netflix subscription",      "admin@finance.com"},
            new Object[]{"seed-admin-ent-2",       199, "EXPENSE", "Entertainment",  7, "Spotify premium",           "admin@finance.com"},
            new Object[]{"seed-admin-ent-3",      1400, "EXPENSE", "Entertainment", 20, "Movie tickets BookMyShow",  "admin@finance.com"},
            new Object[]{"seed-admin-util-1",     1850, "EXPENSE", "Utilities",     10, "Electricity bill BESCOM",   "admin@finance.com"},
            new Object[]{"seed-admin-util-2",      999, "EXPENSE", "Utilities",     10, "Airtel broadband",          "admin@finance.com"},
            new Object[]{"seed-admin-health-1",   2200, "EXPENSE", "Healthcare",    18, "Apollo pharmacy",           "admin@finance.com"},
            new Object[]{"seed-admin-other-1",    3499, "EXPENSE", "Other",          6, "Amazon order headphones",   "admin@finance.com"},
            new Object[]{"seed-admin-other-2",     799, "EXPENSE", "Other",         22, "Amazon order books",        "admin@finance.com"},
            new Object[]{"seed-analyst-salary-1", 72000,"INCOME",  "Salary",        30, "Salary June",               "analyst@finance.com"},
            new Object[]{"seed-analyst-food-1",    380, "EXPENSE", "Food",           2, "Swiggy order",              "analyst@finance.com"},
            new Object[]{"seed-analyst-food-2",    290, "EXPENSE", "Food",           9, "Zomato lunch",              "analyst@finance.com"},
            new Object[]{"seed-analyst-trans-1",   150, "EXPENSE", "Transport",      3, "Uber ride",                 "analyst@finance.com"},
            new Object[]{"seed-analyst-ent-1",     649, "EXPENSE", "Entertainment",  7, "Netflix subscription",      "analyst@finance.com"},
            new Object[]{"seed-analyst-util-1",   1200, "EXPENSE", "Utilities",     11, "Electricity bill BESCOM",   "analyst@finance.com"}
        );

        for (Object[] r : rows) {
            try {
                String email = (String) r[6];
                Long userId  = userIds.get(email);
                Long catId   = catIds.get((String) r[3]);
                if (userId == null || catId == null) continue;

                Transaction t = new Transaction();
                t.setAmount(BigDecimal.valueOf(((Number) r[1]).longValue()));
                t.setType(Transaction.TransactionType.valueOf((String) r[2]));
                t.setDate(today.minusDays((int) r[4]));
                t.setNotes((String) r[5]);
                t.setIdempotencyKey((String) r[0]);

                User user = new User();
                user.setId(userId);
                t.setCreatedBy(user);

                Category cat = new Category();
                cat.setId(catId);
                t.setCategory(cat);

                transactionRepository.save(t);
            } catch (Exception e) {
                log.warn("[DataInitializer] Skipped [{}]: {}", r[0], e.getMessage());
            }
        }
        log.info("[DataInitializer] Transactions seeded.");
    }

    private void seedBudgets() {
        String monthYear = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        User admin = userRepository.findByEmail("admin@finance.com").orElseThrow();

        Map<String, BigDecimal> limits = Map.of(
            "Food",          BigDecimal.valueOf(5000),
            "Transport",     BigDecimal.valueOf(4000),
            "Entertainment", BigDecimal.valueOf(2000),
            "Utilities",     BigDecimal.valueOf(3000),
            "Healthcare",    BigDecimal.valueOf(3000),
            "Other",         BigDecimal.valueOf(5000)
        );

        limits.forEach((catName, limit) ->
            categoryRepository.findAll().stream()
                .filter(c -> c.getName().equals(catName))
                .findFirst()
                .ifPresent(category -> {
                    if (budgetRepository.findByCategoryIdAndMonthYear(category.getId(), monthYear).isEmpty()) {
                        Budget b = new Budget();
                        b.setCategory(category);
                        b.setMonthYear(monthYear);
                        b.setAmountLimit(limit);
                        b.setCreatedBy(admin);
                        budgetRepository.save(b);
                    }
                })
        );
        log.info("[DataInitializer] Budgets seeded for {}.", monthYear);
    }

    private Map<String, Long> buildCategoryMap() {
        Map<String, Long> map = new HashMap<>();
        categoryRepository.findAll().forEach(c -> map.put(c.getName(), c.getId()));
        return map;
    }

    private Map<String, Long> buildUserMap() {
        Map<String, Long> map = new HashMap<>();
        userRepository.findAll().forEach(u -> map.put(u.getEmail(), u.getId()));
        return map;
    }

    private void evictCaches() {
        try {
            redisConnectionFactory.getConnection().serverCommands().flushDb();
            log.info("[DataInitializer] Redis flushed.");
        } catch (Exception e) {
            log.warn("[DataInitializer] Redis flush skipped: {}", e.getMessage());
        }
        try {
            var c1 = cacheManager.getCache("dashboard-summary");
            var c2 = cacheManager.getCache("monthly-trends");
            if (c1 != null) c1.clear();
            if (c2 != null) c2.clear();
        } catch (Exception e) {
            log.warn("[DataInitializer] Cache eviction skipped: {}", e.getMessage());
        }
    }
}
