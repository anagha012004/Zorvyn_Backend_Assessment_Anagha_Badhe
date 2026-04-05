package com.financeapi.service.impl;

import com.financeapi.domain.MerchantTag;
import com.financeapi.domain.Transaction;
import com.financeapi.repository.MerchantTagRepository;
import com.financeapi.service.MerchantClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantTagRepository merchantTagRepository;
    private final MerchantClassifier merchantClassifier;

    public void extractAndTag(Transaction transaction) {
        merchantClassifier.classify(transaction.getNotes()).ifPresent(name -> {
            MerchantTag tag = new MerchantTag();
            tag.setTransaction(transaction);
            tag.setMerchantName(name);
            merchantTagRepository.save(tag);
        });
    }

    public Optional<MerchantTag> findByTransactionId(Long transactionId) {
        return merchantTagRepository.findByTransactionId(transactionId);
    }

    public List<Map<String, Object>> getTopMerchants(String period) {
        LocalDate from = "monthly".equalsIgnoreCase(period)
                ? LocalDate.now().withDayOfMonth(1)
                : LocalDate.now().minusDays(7);
        List<Object[]> rows = merchantTagRepository.topMerchantsSince(from);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("merchant", row[0]);
            entry.put("totalSpend", row[1]);
            result.add(entry);
        }
        return result;
    }
}
