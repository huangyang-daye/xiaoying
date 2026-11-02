package com.xiaoying.studio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TaskExecutionPolicy {
    public final int maxAttempts, baseDelayMs, maxDelayMs, leaseMs;
    public TaskExecutionPolicy(@Value("${app.worker.max-attempts:3}") int maxAttempts,
        @Value("${app.worker.retry-base-delay-ms:1000}") int baseDelayMs,
        @Value("${app.worker.retry-max-delay-ms:30000}") int maxDelayMs,
        @Value("${app.worker.lease-ms:120000}") int leaseMs,
        @Value("${app.worker.heartbeat-interval-ms:1000}") int heartbeatMs) {
        if(heartbeatMs<100 || heartbeatMs>=leaseMs/2 || maxAttempts<1 || maxAttempts>6 || baseDelayMs<1 || maxDelayMs<baseDelayMs || maxDelayMs>300000 || leaseMs<3000 || leaseMs>3600000)
            throw new IllegalArgumentException("Invalid task retry/lease policy");
        this.maxAttempts=maxAttempts; this.baseDelayMs=baseDelayMs; this.maxDelayMs=maxDelayMs; this.leaseMs=leaseMs;
    }
    public static long delay(Models.Task task) {
        return Math.min(task.retryMaxDelayMs,(long)task.retryBaseDelayMs*(1L<<Math.min(Math.max(0,task.attemptCount-1),20)));
    }
}
