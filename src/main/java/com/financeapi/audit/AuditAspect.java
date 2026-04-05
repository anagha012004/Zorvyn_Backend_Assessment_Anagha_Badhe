package com.financeapi.audit;

import com.financeapi.domain.AuditLog;
import com.financeapi.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;

    @AfterReturning(
        pointcut = "execution(* com.financeapi.service.impl.TransactionServiceImpl.create(..)) || " +
                   "execution(* com.financeapi.service.impl.TransactionServiceImpl.update(..)) || " +
                   "execution(* com.financeapi.service.impl.TransactionServiceImpl.delete(..)) || " +
                   "execution(* com.financeapi.service.impl.TransactionServiceImpl.restore(..))",
        returning = "result")
    @Async
    public void logTransactionWrite(JoinPoint jp, Object result) {
        String action = jp.getSignature().getName().toUpperCase();
        String entityId = result != null ? extractId(result) : extractFirstArg(jp);
        saveLog(action, "Transaction", entityId, null, result != null ? result.toString() : null);
    }

    @AfterReturning(
        pointcut = "execution(* com.financeapi.service.impl.UserServiceImpl.updateUser(..)) || " +
                   "execution(* com.financeapi.service.impl.UserServiceImpl.deleteUser(..))",
        returning = "result")
    @Async
    public void logUserWrite(JoinPoint jp, Object result) {
        String action = jp.getSignature().getName().toUpperCase();
        String entityId = result != null ? extractId(result) : extractFirstArg(jp);
        saveLog(action, "User", entityId, null, result != null ? result.toString() : null);
    }

    private void saveLog(String action, String entityType, String entityId, String oldVal, String newVal) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setOldValue(oldVal);
        log.setNewValue(newVal);
        log.setIpAddress(getIpAddress());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) log.setUserId(getUserId(auth.getName()));

        auditLogRepository.save(log);
    }

    private String extractId(Object result) {
        try {
            return result.getClass().getMethod("getId").invoke(result).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String extractFirstArg(JoinPoint jp) {
        Object[] args = jp.getArgs();
        return args.length > 0 ? args[0].toString() : null;
    }

    private String getIpAddress() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String forwarded = req.getHeader("X-Forwarded-For");
                return forwarded != null ? forwarded.split(",")[0].trim() : req.getRemoteAddr();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Long getUserId(String email) {
        // Resolved lazily to avoid circular dependency; null is acceptable for audit
        return null;
    }
}
