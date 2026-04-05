package com.financeapi.service.impl;

import com.financeapi.service.MerchantClassifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "merchant.classifier", havingValue = "dictionary", matchIfMissing = true)
public class DictionaryMerchantClassifier implements MerchantClassifier {

    private static final Map<String, String> MERCHANT_MAP = new LinkedHashMap<>();
    static {
        MERCHANT_MAP.put("swiggy",     "Swiggy");
        MERCHANT_MAP.put("zomato",     "Zomato");
        MERCHANT_MAP.put("amazon",     "Amazon");
        MERCHANT_MAP.put("flipkart",   "Flipkart");
        MERCHANT_MAP.put("uber",       "Uber");
        MERCHANT_MAP.put("ola",        "Ola");
        MERCHANT_MAP.put("netflix",    "Netflix");
        MERCHANT_MAP.put("spotify",    "Spotify");
        MERCHANT_MAP.put("airtel",     "Airtel");
        MERCHANT_MAP.put("jio",        "Jio");
        MERCHANT_MAP.put("blinkit",    "Blinkit");
        MERCHANT_MAP.put("zepto",      "Zepto");
        MERCHANT_MAP.put("bigbasket",  "BigBasket");
        MERCHANT_MAP.put("dunzo",      "Dunzo");
        MERCHANT_MAP.put("paytm",      "Paytm");
        MERCHANT_MAP.put("phonepe",    "PhonePe");
        MERCHANT_MAP.put("gpay",       "Google Pay");
        MERCHANT_MAP.put("bookmyshow", "BookMyShow");
        MERCHANT_MAP.put("apollo",     "Apollo Pharmacy");
        MERCHANT_MAP.put("bpcl",       "BPCL");
        MERCHANT_MAP.put("bescom",     "BESCOM");
    }

    @Override
    public Optional<String> classify(String notes) {
        if (notes == null) return Optional.empty();
        String lower = notes.toLowerCase();
        for (Map.Entry<String, String> entry : MERCHANT_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) return Optional.of(entry.getValue());
        }
        // Fallback: first capitalized word sequence
        Matcher m = Pattern.compile("\\b([A-Z][a-zA-Z]+(?:\\s[A-Z][a-zA-Z]+)?)\\b").matcher(notes);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
