package com.financeapi.config;

import com.financeapi.dto.request.TransactionRequest;
import com.financeapi.domain.Transaction.TransactionType;
import com.financeapi.repository.CategoryRepository;
import com.financeapi.repository.UserRepository;
import com.financeapi.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionSimulator {

    private final TransactionService transactionService;
    private final UserRepository     userRepository;
    private final CategoryRepository categoryRepository;

    private final Random rng = new Random();

    // Realistic transaction templates: {notes, categoryName, minAmt, maxAmt, type}
    private static final List<Object[]> TEMPLATES = List.of(
        new Object[]{"Swiggy dinner order",       "Food",          150,   800,  "EXPENSE"},
        new Object[]{"Zomato lunch",               "Food",          100,   500,  "EXPENSE"},
        new Object[]{"BigBasket grocery",          "Food",          300,  1500,  "EXPENSE"},
        new Object[]{"Uber cab ride",              "Transport",      80,   400,  "EXPENSE"},
        new Object[]{"Ola ride",                   "Transport",      60,   300,  "EXPENSE"},
        new Object[]{"Petrol refill BPCL",         "Transport",    1500,  4000,  "EXPENSE"},
        new Object[]{"Netflix subscription",       "Entertainment", 199,   649,  "EXPENSE"},
        new Object[]{"BookMyShow tickets",         "Entertainment", 400,  1800,  "EXPENSE"},
        new Object[]{"Spotify premium",            "Entertainment", 119,   199,  "EXPENSE"},
        new Object[]{"Electricity bill BESCOM",    "Utilities",     800,  3000,  "EXPENSE"},
        new Object[]{"Airtel broadband",           "Utilities",     499,  1299,  "EXPENSE"},
        new Object[]{"Apollo pharmacy",            "Healthcare",    200,  3000,  "EXPENSE"},
        new Object[]{"Amazon order",               "Other",         299,  5000,  "EXPENSE"},
        new Object[]{"Freelance payment received", "Other",        2000, 15000,  "INCOME"},
        new Object[]{"Salary credit",              "Salary",      50000, 90000,  "INCOME"}
    );

    /**
     * Fires every 15 seconds. Picks a random user and a random transaction template,
     * calls the real TransactionService so anomaly detection, DNA fingerprinting,
     * velocity scoring, merchant tagging, SSE broadcast, and audit logging all run.
     */
    @Scheduled(fixedDelay = 7200000)
    public void simulateTransaction() {
        try {
            // Pick a random active user (skip if none exist yet)
            var users = userRepository.findAll().stream()
                .filter(u -> u.isActive())
                .toList();
            if (users.isEmpty()) return;

            var user = users.get(rng.nextInt(users.size()));

            // Pick a random template
            Object[] tpl = TEMPLATES.get(rng.nextInt(TEMPLATES.size()));
            String notes    = (String) tpl[0];
            String catName  = (String) tpl[1];
            int    minAmt   = (int)    tpl[2];
            int    maxAmt   = (int)    tpl[3];
            String typeStr  = (String) tpl[4];

            // Resolve category ID
            Long catId = categoryRepository.findAll().stream()
                .filter(c -> c.getName().equals(catName))
                .findFirst()
                .map(c -> c.getId())
                .orElse(null);

            // Build request
            TransactionRequest req = new TransactionRequest();
            req.setAmount(BigDecimal.valueOf(minAmt + rng.nextInt(maxAmt - minAmt + 1)));
            req.setType(TransactionType.valueOf(typeStr));
            req.setCategoryId(catId);
            req.setDate(LocalDate.now());
            req.setNotes(notes);

            // Determine Spring role for SecurityContext
            String springRole = user.getRoles().stream()
                .map(r -> "ROLE_" + r.getName().name())
                .filter(r -> r.equals("ROLE_ADMIN") || r.equals("ROLE_ANALYST"))
                .findFirst()
                .orElse("ROLE_ANALYST");

            // Set SecurityContext so @PreAuthorize and AuditAspect work
            var auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null,
                List.of(new SimpleGrantedAuthority(springRole))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
            try {
                transactionService.create(req, null, user.getEmail());
                log.debug("[Simulator] Created: {} ₹{} for {}", notes, req.getAmount(), user.getEmail());
            } finally {
                SecurityContextHolder.clearContext();
            }

        } catch (Exception e) {
            log.warn("[Simulator] Skipped tick: {}", e.getMessage());
        }
    }
}
