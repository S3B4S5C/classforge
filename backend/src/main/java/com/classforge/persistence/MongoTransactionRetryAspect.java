package com.classforge.persistence;

import com.mongodb.MongoException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Retry the whole outer transaction on MongoDB write conflicts; never repeat a remote LLM call. */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MongoTransactionRetryAspect {
    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object retry(ProceedingJoinPoint call) throws Throwable {
        if (TransactionSynchronizationManager.isActualTransactionActive()) return call.proceed();
        for (int attempt = 0;; attempt++) {
            try { return call.proceed(); }
            catch (Throwable failure) {
                if (attempt >= 19 || !transientTransaction(failure)) throw failure;
                Thread.sleep(Math.min(100, 5L * (attempt + 1)));
            }
        }
    }
    private boolean transientTransaction(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongo && mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) return true;
        }
        return false;
    }
}
