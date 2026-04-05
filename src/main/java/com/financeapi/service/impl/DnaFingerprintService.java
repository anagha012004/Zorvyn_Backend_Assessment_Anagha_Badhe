package com.financeapi.service.impl;

import com.financeapi.domain.Transaction;
import com.financeapi.domain.TransactionDna;
import com.financeapi.repository.TransactionDnaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DnaFingerprintService {

    private final TransactionDnaRepository dnaRepository;

    /**
     * Returns true if a matching DNA was seen in the last 5 minutes (possible duplicate).
     * Always stores the new DNA record regardless.
     */
    public boolean checkAndStore(Transaction saved, Long userId, BigDecimal amount,
                                  Long categoryId, LocalDateTime createdAt) {
        String hash = computeHash(userId, amount, categoryId, createdAt);
        boolean isDuplicate = dnaRepository.existsByDnaHashSince(hash, LocalDateTime.now().minusMinutes(5));

        TransactionDna dna = new TransactionDna();
        dna.setDnaHash(hash);
        dna.setTransaction(saved);
        dnaRepository.save(dna);

        return isDuplicate;
    }

    private String computeHash(Long userId, BigDecimal amount, Long categoryId, LocalDateTime createdAt) {
        // Hour-of-day bucket: group by 4-hour windows (0,4,8,12,16,20)
        int hourBucket = (createdAt.getHour() / 4) * 4;
        String input = userId + "|" + amount.toPlainString() + "|" + categoryId + "|" + hourBucket;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
