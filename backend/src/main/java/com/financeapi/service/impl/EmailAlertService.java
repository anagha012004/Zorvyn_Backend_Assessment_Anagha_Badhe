package com.financeapi.service.impl;

import com.financeapi.domain.Transaction;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class EmailAlertService {

    private final Resend resend;
    private final String from;
    private final BigDecimal highValueThreshold;
    private final boolean enabled;

    public EmailAlertService(
            @Value("${resend.api-key:}") String apiKey,
            @Value("${alerts.email.from:noreply@zorvyn.finance}") String from,
            @Value("${alerts.email.high-value-threshold:10000}") BigDecimal highValueThreshold) {
        this.from = from;
        this.highValueThreshold = highValueThreshold;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.resend = enabled ? new Resend(apiKey) : null;
        if (!enabled) log.info("[EmailAlert] RESEND_API_KEY not set — email alerts disabled");
    }

    @Async
    public void sendAnomalyAlert(String recipientEmail, Transaction t, String detail) {
        send(recipientEmail,
                "⚠️ Anomaly Detected on Your Account",
                "<h2>Unusual Transaction Flagged</h2>" +
                "<p><b>Amount:</b> ₹" + t.getAmount() + "</p>" +
                "<p><b>Category:</b> " + (t.getCategory() != null ? t.getCategory().getName() : "N/A") + "</p>" +
                "<p><b>Date:</b> " + t.getDate() + "</p>" +
                "<p><b>Detail:</b> " + detail + "</p>" +
                "<p>If this was not you, please contact support immediately.</p>");
    }

    @Async
    public void sendHighValueAlert(String recipientEmail, Transaction t) {
        if (t.getAmount().compareTo(highValueThreshold) < 0) return;
        send(recipientEmail,
                "💰 High-Value Transaction Alert",
                "<h2>High-Value Transaction Recorded</h2>" +
                "<p><b>Amount:</b> ₹" + t.getAmount() + "</p>" +
                "<p><b>Category:</b> " + (t.getCategory() != null ? t.getCategory().getName() : "N/A") + "</p>" +
                "<p><b>Date:</b> " + t.getDate() + "</p>" +
                "<p>If this was not authorised, please contact support immediately.</p>");
    }

    private void send(String to, String subject, String html) {
        if (!enabled) return;
        try {
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(from)
                    .to(to)
                    .subject(subject)
                    .html(html)
                    .build();
            resend.emails().send(params);
        } catch (ResendException e) {
            log.warn("[EmailAlert] Failed to send to {}: {}", to, e.getMessage());
        }
    }
}
