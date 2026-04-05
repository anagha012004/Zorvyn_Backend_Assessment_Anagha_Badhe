package com.financeapi.service.impl;

import com.financeapi.domain.User;
import com.financeapi.dto.response.RiskProfileResponse;
import com.financeapi.exception.ResourceNotFoundException;
import com.financeapi.repository.TransactionRepository;
import com.financeapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VelocityService {

    private static final double ALPHA = 0.4; // EMA smoothing for score
    private static final double HIGH_THRESHOLD = 70.0;
    private static final double MEDIUM_THRESHOLD = 40.0;

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Called on every transaction creation. Computes a new velocity score
     * based on how today's spend compares to the 7-day rolling average daily spend.
     * Returns the updated score (0–100).
     */
    @Transactional
    public double computeAndUpdate(User user, BigDecimal newAmount) {
        BigDecimal spend24h = transactionRepository.sumAmountByUserSince(
                user.getId(), LocalDateTime.now().minusHours(24));
        BigDecimal spend7d = transactionRepository.sumAmountByUserSince(
                user.getId(), LocalDateTime.now().minusDays(7));

        double dailyAvg7d = spend7d.doubleValue() / 7.0;
        double today = spend24h.doubleValue();

        // Spike ratio: how many times today's spend exceeds the daily average
        double spikeRatio = dailyAvg7d > 0 ? today / dailyAvg7d : (today > 0 ? 5.0 : 0.0);
        // Normalize to 0-100: ratio of 1 = score 20, ratio 5+ = score 100
        double rawScore = Math.min(100.0, (spikeRatio / 5.0) * 100.0);

        // EMA with previous score
        double newScore = ALPHA * rawScore + (1 - ALPHA) * user.getVelocityScore();
        user.setVelocityScore(newScore);
        userRepository.save(user);
        return newScore;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public RiskProfileResponse getRiskProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        BigDecimal spend24h = transactionRepository.sumAmountByUserSince(
                user.getId(), LocalDateTime.now().minusHours(24));
        BigDecimal spend7d = transactionRepository.sumAmountByUserSince(
                user.getId(), LocalDateTime.now().minusDays(7));

        double score = user.getVelocityScore();
        String riskLevel = score >= HIGH_THRESHOLD ? "HIGH" : score >= MEDIUM_THRESHOLD ? "MEDIUM" : "LOW";

        return new RiskProfileResponse(user.getId(), user.getEmail(), score, spend24h, spend7d, riskLevel);
    }
}
