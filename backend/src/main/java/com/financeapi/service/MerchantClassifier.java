package com.financeapi.service;

import java.util.Optional;

public interface MerchantClassifier {
    /**
     * Resolve a canonical merchant name from raw transaction notes.
     * Returns empty if no merchant can be identified.
     */
    Optional<String> classify(String notes);
}
