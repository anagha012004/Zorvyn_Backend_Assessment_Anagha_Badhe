package com.financeapi.config;

import com.financeapi.domain.*;
import com.financeapi.dto.request.TransactionRequest;
import com.financeapi.repository.*;
import com.financeapi.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final TransactionService     transactionService;
    private final CacheManager           cacheManager;
    private final PasswordEncoder        passwordEncoder;
    private final RedisConnectionFactory redisConnectionFactory;

    @Bean
    public ApplicationRunner seedData() {
        return args -> {
            evictCaches();
            fixAdminPassword();
            seedUser("analyst@finance.com", "Alice Analyst", Role.RoleName.ANALYST);
            seedUser("viewer@finance.com",  "Bob Viewer",    Role.RoleName.VIEWER);

            if (transactionRepository.findByIdempotencyKey("seed-admin-salary-1").isEmpty()) {
                seedTransactions();
            }

            seedBudgets();
            evictCaches();
        };
    }

    private void fixAdminPassword() {
        userRepository.findByEmail("admin@finance.com").ifPresent(admin -> {
            if (!passwordEncoder.matches("admin123", admin.getPasswordHash())) {
                admin.setPasswordHash(passwordEncoder.encode("admin123"));
                userRepository.save(admin);
            }
        });
    }

    private void seedUser(String email, String fullName, Role.RoleName roleName) {
        userRepository.findByEmail(email).orElseGet(() -> {
            Role viewer = roleRepository.findByName(Role.RoleName.VIEWER).orElseThrow();
            Role role   = roleRepository.findByName(roleName).orElseThrow();
            User user   = new User();
            user.setEmail(email);
            user.setFullName(fullName);
            user.setPasswordHash(passwordEncoder.encode("demo1234"));
            user.setTimezone("Asia/Kolkata");
            user.setRoles(roleName == Role.RoleName.ANALYST ? Set.of(viewer, role) : Set.of(viewer));
            return userRepository.save(user);
        });
    }

    private void seedTransactions() {
        LocalDate today  = LocalDate.now();
        Map<String, Long> catIds = buildCategoryMap();

        List<Object[]> rows = List.of(
            new Object[]{"seed-admin-salary-1",  85000, "INCOME",  "Salary",        30, "Monthly salary June",       "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-salary-2",  85000, "INCOME",  "Salary",        60, "Monthly salary May",        "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-food-1",      450, "EXPENSE", "Food",           1, "Swiggy dinner order",       "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-food-2",      320, "EXPENSE", "Food",           3, "Zomato lunch",              "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-food-3",      180, "EXPENSE", "Food",           5, "Grocery BigBasket",         "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-food-4",      560, "EXPENSE", "Food",           8, "Swiggy weekend binge",      "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-food-5",      210, "EXPENSE", "Food",          12, "Zomato breakfast",          "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-food-6",      390, "EXPENSE", "Food",          16, "Swiggy order",              "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-food-7",      270, "EXPENSE", "Food",          22, "Zomato dinner",             "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-food-anomaly",12000,"EXPENSE","Food",           0, "Catering for office party", "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-trans-1",     250, "EXPENSE", "Transport",      2, "Uber cab to airport",       "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-trans-2",     120, "EXPENSE", "Transport",      4, "Ola ride",                  "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-trans-3",    3200, "EXPENSE", "Transport",     15, "Petrol refill BPCL",        "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-trans-4",     180, "EXPENSE", "Transport",     25, "Uber ride",                 "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-ent-1",       649, "EXPENSE", "Entertainment",  7, "Netflix subscription",      "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-ent-2",       199, "EXPENSE", "Entertainment",  7, "Spotify premium",           "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-ent-3",      1400, "EXPENSE", "Entertainment", 20, "Movie tickets BookMyShow",  "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-util-1",     1850, "EXPENSE", "Utilities",     10, "Electricity bill BESCOM",   "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-util-2",      999, "EXPENSE", "Utilities",     10, "Airtel broadband",          "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-health-1",   2200, "EXPENSE", "Healthcare",    18, "Apollo pharmacy",           "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-other-1",    3499, "EXPENSE", "Other",          6, "Amazon order headphones",   "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-admin-other-2",     799, "EXPENSE", "Other",         22, "Amazon order books",        "admin@finance.com",   "ROLE_ADMIN"},
            new Object[]{"seed-analyst-salary-1", 72000,"INCOME",  "Salary",        30, "Salary June",               "analyst@finance.com", "ROLE_ANALYST"},
            new Object[]{"seed-analyst-food-1",    380, "EXPENSE", "Food",           2, "Swiggy order",              "analyst@finance.com", "ROLE_ANALYST"},
            new Object[]{"seed-analyst-food-2",    290, "EXPENSE", "Food",           9, "Zomato lunch",              "analyst@finance.com", "ROLE_ANALYST"},
            new Object[]{"seed-analyst-trans-1",   150, "EXPENSE", "Transport",      3, "Uber ride",                 "analyst@finance.com", "ROLE_ANALYST"},
            new Object[]{"seed-analyst-ent-1",     649, "EXPENSE", "Entertainment",  7, "Netflix subscription",      "analyst@finance.com", "ROLE_ANALYST"},
            new Object[]{"seed-analyst-util-1",   1200, "EXPENSE", "Utilities",     11, "Electricity bill BESCOM",   "analyst@finance.com", "ROLE_ANALYST"}
        );

        for (Object[] r : rows) {
            String idempotencyKey = (String) r[0];
            String email          = (String) r[6];
            String springRole     = (String) r[7];

            TransactionRequest req = new TransactionRequest();
            req.setAmount(BigDecimal.valueOf(((Number) r[1]).longValue()));
            req.setType(Transaction.TransactionType.valueOf((String) r[2]));
            req.setCategoryId(catIds.get((String) r[3]));
            req.setDate(today.minusDays((int) r[4]));
            req.setNotes((String) r[5]);

            try {
                runAs(email, springRole, () ->
                    transactionService.create(req, idempotencyKey, email)
                );
            } catch (Exception e) {
                log.warn("Seed transaction skipped [{}]: {}", idempotencyKey, e.getMessage());
            }
        }
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
    }

    private Map<String, Long> buildCategoryMap() {
        Map<String, Long> map = new HashMap<>();
        categoryRepository.findAll().forEach(c -> map.put(c.getName(), c.getId()));
        return map;
    }

    private void evictCaches() {
        try {
            redisConnectionFactory.getConnection().serverCommands().flushDb();
            log.info("[DataInitializer] Redis flushed on startup");
        } catch (Exception e) {
            log.warn("[DataInitializer] Redis flush skipped: {}", e.getMessage());
        }
        // Also clear in-memory caches
        try {
            var dashCache  = cacheManager.getCache("dashboard-summary");
            var trendCache = cacheManager.getCache("monthly-trends");
            if (dashCache  != null) dashCache.clear();
            if (trendCache != null) trendCache.clear();
        } catch (Exception e) {
            log.warn("[DataInitializer] Cache eviction skipped: {}", e.getMessage());
        }
    }

    private void runAs(String email, String springRole, Runnable action) {
        var auth = new UsernamePasswordAuthenticationToken(
            email, null, List.of(new SimpleGrantedAuthority(springRole))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
