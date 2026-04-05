package com.financeapi.service;

import com.financeapi.domain.*;
import com.financeapi.dto.request.TransactionRequest;
import com.financeapi.dto.response.TransactionResponse;
import com.financeapi.exception.ResourceNotFoundException;
import com.financeapi.repository.CategoryRepository;
import com.financeapi.repository.TransactionRepository;
import com.financeapi.repository.UserRepository;
import com.financeapi.repository.AuditLogRepository;
import com.financeapi.service.impl.DnaFingerprintService;
import com.financeapi.service.impl.MerchantService;
import com.financeapi.service.impl.SseEmitterRegistry;
import com.financeapi.service.impl.TransactionServiceImpl;
import com.financeapi.service.impl.VelocityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock TransactionRepository transactionRepository;
    @Mock UserRepository userRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock VelocityService velocityService;
    @Mock DnaFingerprintService dnaFingerprintService;
    @Mock MerchantService merchantService;
    @Mock SseEmitterRegistry sseEmitterRegistry;

    @InjectMocks TransactionServiceImpl transactionService;

    private User user;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setFullName("Test User");

        transaction = new Transaction();
        transaction.setId(1L);
        transaction.setAmount(BigDecimal.valueOf(100));
        transaction.setType(Transaction.TransactionType.EXPENSE);
        transaction.setDate(LocalDate.now());
        transaction.setCreatedBy(user);
        transaction.setDeleted(false);
    }

    @Test
    void create_withoutIdempotencyKey_savesTransaction() {
        TransactionRequest req = new TransactionRequest();
        req.setAmount(BigDecimal.valueOf(100));
        req.setType(Transaction.TransactionType.EXPENSE);
        req.setDate(LocalDate.now());

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(transactionRepository.save(any())).thenReturn(transaction);
        when(dnaFingerprintService.checkAndStore(any(), any(), any(), any(), any())).thenReturn(false);
        when(velocityService.computeAndUpdate(any(), any())).thenReturn(10.0);
        when(merchantService.findByTransactionId(any())).thenReturn(Optional.empty());

        TransactionResponse response = transactionService.create(req, null, "user@example.com");

        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void create_withExistingIdempotencyKey_returnsCachedResponse() {
        TransactionRequest req = new TransactionRequest();
        req.setAmount(BigDecimal.valueOf(100));
        req.setType(Transaction.TransactionType.EXPENSE);
        req.setDate(LocalDate.now());

        when(transactionRepository.findByIdempotencyKey("key-abc")).thenReturn(Optional.of(transaction));

        TransactionResponse response = transactionService.create(req, "key-abc", "user@example.com");

        assertThat(response.getId()).isEqualTo(1L);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void getById_deletedTransaction_throwsNotFound() {
        transaction.setDeleted(true);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> transactionService.getById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_softDeletesTransaction() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any())).thenReturn(transaction);

        transactionService.delete(1L, "user@example.com");

        assertThat(transaction.isDeleted()).isTrue();
        verify(transactionRepository).save(transaction);
    }

    @Test
    void restore_deletedTransaction_setsDeletedFalse() {
        transaction.setDeleted(true);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any())).thenReturn(transaction);

        TransactionResponse response = transactionService.restore(1L, "user@example.com");

        assertThat(transaction.isDeleted()).isFalse();
        assertThat(response).isNotNull();
    }

    @Test
    void create_userNotFound_throwsResourceNotFoundException() {
        TransactionRequest req = new TransactionRequest();
        req.setAmount(BigDecimal.valueOf(50));
        req.setType(Transaction.TransactionType.INCOME);
        req.setDate(LocalDate.now());

        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.create(req, null, "ghost@example.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
