package com.financeapi.service.impl;

import com.financeapi.domain.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAlertService {

    private final JavaMailSender mailSender;

    @Value("${alerts.email.from:noreply@zorvyn.finance}")
    private String from;

    @Value("${alerts.email.high-value-threshold:10000}")
    private BigDecimal highValueThreshold;

    @Async
    public void sendAnomalyAlert(String recipientEmail, Transaction t, String detail) {
        send(recipientEmail,
                "⚠️ Anomaly Detected on Your Account",
                "An unusual transaction was flagged:\n\n" +
                "Amount: ₹" + t.getAmount() + "\n" +
                "Category: " + (t.getCategory() != null ? t.getCategory().getName() : "N/A") + "\n" +
                "Date: " + t.getDate() + "\n" +
                "Detail: " + detail + "\n\n" +
                "If this was not you, please contact support immediately.");
    }

    @Async
    public void sendHighValueAlert(String recipientEmail, Transaction t) {
        if (t.getAmount().compareTo(highValueThreshold) < 0) return;
        send(recipientEmail,
                "💰 High-Value Transaction Alert",
                "A high-value transaction was recorded:\n\n" +
                "Amount: ₹" + t.getAmount() + "\n" +
                "Category: " + (t.getCategory() != null ? t.getCategory().getName() : "N/A") + "\n" +
                "Date: " + t.getDate() + "\n\n" +
                "If this was not authorised, please contact support immediately.");
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("[EmailAlert] Failed to send alert to {}: {}", to, e.getMessage());
        }
    }
}
