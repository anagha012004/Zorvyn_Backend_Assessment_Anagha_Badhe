package com.financeapi.service.impl;

import com.financeapi.domain.MerchantTag;
import com.financeapi.domain.Transaction;
import com.financeapi.repository.MerchantTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantTagRepository merchantTagRepository;

    // keyword -> canonical merchant name
    private static final Map<String, String> MERCHANT_MAP = new LinkedHashMap<>();
    static {
        MERCHANT_MAP.put("swiggy", "Swiggy");
        MERCHANT_MAP.put("zomato", "Zomato");
        MERCHANT_MAP.put("amazon", "Amazon");
        MERCHANT_MAP.put("flipkart", "Flipkart");
        MERCHANT_MAP.put("uber", "Uber");
        MERCHANT_MAP.put("ola", "Ola");
        MERCHANT_MAP.put("netflix", "Netflix");
        MERCHANT_MAP.put("spotify", "Spotify");
        MERCHANT_MAP.put("airtel", "Airtel");
        MERCHANT_MAP.put("jio", "Jio");
        MERCHANT_MAP.put("blinkit", "Blinkit");
        MERCHANT_MAP.put("zepto", "Zepto");
        MERCHANT_MAP.put("bigbasket", "BigBasket");
        MERCHANT_MAP.put("dunzo", "Dunzo");
        MERCHANT_MAP.put("paytm", "Paytm");
        MERCHANT_MAP.put("phonepe", "PhonePe");
        MERCHANT_MAP.put("gpay", "Google Pay");
    }

    public void extractAndTag(Transaction transaction) {
        if (transaction.getNotes() == null) return;
        String notes = transaction.getNotes().toLowerCase();
        for (Map.Entry<String, String> entry : MERCHANT_MAP.entrySet()) {
            if (notes.contains(entry.getKey())) {
                MerchantTag tag = new MerchantTag();
                tag.setTransaction(transaction);
                tag.setMerchantName(entry.getValue());
                merchantTagRepository.save(tag);
                return;
            }
        }
        // Fallback: extract first capitalized word sequence as merchant
        Matcher m = Pattern.compile("\\b([A-Z][a-zA-Z]+(?:\\s[A-Z][a-zA-Z]+)?)\\b")
                           .matcher(transaction.getNotes());
        if (m.find()) {
            MerchantTag tag = new MerchantTag();
            tag.setTransaction(transaction);
            tag.setMerchantName(m.group(1));
            merchantTagRepository.save(tag);
        }
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
