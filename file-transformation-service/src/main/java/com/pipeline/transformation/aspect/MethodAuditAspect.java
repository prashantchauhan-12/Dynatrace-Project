package com.pipeline.transformation.aspect;

import com.pipeline.transformation.service.BusinessEventEmitter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Aspect to intercept methods annotated with @MethodAuditable.
 * It calculates the execution time and emits a business event to Dynatrace.
 */
@Aspect
@Component
public class MethodAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(MethodAuditAspect.class);

    private final BusinessEventEmitter businessEventEmitter;

    public MethodAuditAspect(BusinessEventEmitter businessEventEmitter) {
        this.businessEventEmitter = businessEventEmitter;
    }

    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, MethodAuditable auditable) throws Throwable {
        String methodName = auditable.value().isEmpty() ? joinPoint.getSignature().getName() : auditable.value();
        long start = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - start;
            
            log.info("[METHOD_AUDIT] Method {} executed successfully in {} ms", methodName, executionTime);
            businessEventEmitter.emitMethodAuditEvent("N/A", methodName, executionTime, "SUCCESS", null);
            
            return result;
        } catch (Throwable e) {
            long executionTime = System.currentTimeMillis() - start;
            
            log.error("[METHOD_AUDIT] Method {} failed in {} ms with error: {}", methodName, executionTime, e.getMessage());
            businessEventEmitter.emitMethodAuditEvent("N/A", methodName, executionTime, "FAILED", e.getMessage());
            
            throw e;
        }
    }
}
