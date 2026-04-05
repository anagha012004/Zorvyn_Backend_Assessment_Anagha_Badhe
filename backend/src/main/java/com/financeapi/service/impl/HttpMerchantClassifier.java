package com.financeapi.service.impl;

import com.financeapi.service.MerchantClassifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * Calls an external merchant classification API.
 * Activate with: merchant.classifier=http
 * Configure endpoint with: merchant.classifier.api-url=https://your-api/classify
 *
 * Expected API contract:
 *   POST {api-url}  body: {"notes": "..."}
 *   Response:       {"merchant": "Swiggy"}  or 404/empty
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "merchant.classifier", havingValue = "http")
public class HttpMerchantClassifier implements MerchantClassifier {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${merchant.classifier.api-url}")
    private String apiUrl;

    @Override
    @SuppressWarnings("unchecked")
    public Optional<String> classify(String notes) {
        if (notes == null) return Optional.empty();
        try {
            Map<String, String> response = restTemplate.postForObject(
                    apiUrl, Map.of("notes", notes), Map.class);
            if (response != null && response.containsKey("merchant")) {
                return Optional.of(response.get("merchant"));
            }
        } catch (Exception e) {
            log.warn("[HttpMerchantClassifier] Classification failed for notes='{}': {}", notes, e.getMessage());
        }
        return Optional.empty();
    }
}
