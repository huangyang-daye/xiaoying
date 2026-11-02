package com.xiaoying.studio;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.xiaoying.studio.Models.*;
import static com.xiaoying.studio.Models.STAGES;

@Service
public class TaskLifecycle {
    public static class LostLeaseException extends IllegalStateException {
        LostLeaseException() { super("Execution lease no longer belongs to this worker"); }
    }
    private final StudioMapper db;
    private final TaskExecutionPolicy policy;
    public TaskLifecycle(StudioMapper db,TaskExecutionPolicy policy) { this.db=db; this.policy=policy; }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public Task claimNext() {
        Instant now=Instant.now(); String id=db.nextQueued(now);
        if(id==null) return null;
        Task t=db.task(id);
        if(t.currentStage==Stage.NOT_STARTED) t.currentStage=Stage.MATERIAL_CHECK;
        int index=STAGES.indexOf(t.currentStage);
        if(index<0 || t.attemptCount>=t.maxAttempts) throw new IllegalStateException("Invalid queued checkpoint");
        var steps=db.steps(id);
        for(int i=0;i<index;i++) if(steps.get(i).status!=StepStatus.SUCCEEDED) throw new IllegalStateException("Missing checkpoint");
        Step current=steps.get(index);
        if(current.status!=StepStatus.PENDING) throw new IllegalStateException("Checkpoint is not pending");
        t.attemptCount++; t.leaseToken=UUID.randomUUID().toString(); t.leaseExpiresAt=now.plusMillis(policy.leaseMs);
        if(t.startedAt==null) t.startedAt=now;
        t.updatedAt=now;
        if(db.claim(t)!=1 || db.insertAttempt(t)!=1) throw new IllegalStateException("Task claim lost");
        current.status=StepStatus.RUNNING; current.startedAt=now; current.finishedAt=null;
        current.errorCode=null; current.errorMessage=null; current.attemptCount++; save(current);
        return db.task(id);
    }
    public boolean renew(String id,String token) {
        Instant now=Instant.now(); return db.renewLease(id,token,now,now.plusMillis(policy.leaseMs))==1;
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public void completeStep(String id,String token,Stage stage,byte[] content) {
        Task t=db.lockTask(id); Instant now=Instant.now(); requireLease(t,token,now);
        if(t.currentStage!=stage) throw new IllegalStateException("Unexpected task transition");
        int index=STAGES.indexOf(stage);
        if(index<0) throw new IllegalStateException("Not an executable stage");
        var steps=db.steps(id); Step current=steps.get(index);
        if(current.status!=StepStatus.RUNNING) throw new IllegalStateException("Step is not running");
        current.status=StepStatus.SUCCEEDED; current.finishedAt=now; save(current);
        if(index==STAGES.size()-1) {
            boolean linked=Models.text(t.sampleResultUrl)!=null;
            if(!linked) {
                if(content==null || content.length==0) throw new IllegalStateException("Result is required");
                if(db.insertResult(id,content)!=1) throw new IllegalStateException("Result insert failed");
            }
            finishAttempt(t,"SUCCEEDED",stage,now,null,null);
            t.status=Status.SUCCEEDED; t.currentStage=Stage.COMPLETED;
            t.resultKey=null; t.resultSize=linked?0L:(long)content.length; t.finishedAt=now;
            t.leaseToken=null; t.leaseExpiresAt=null;
        } else {
            Step next=steps.get(index+1);
            if(next.status!=StepStatus.PENDING) throw new IllegalStateException("Next step is not pending");
            next.status=StepStatus.RUNNING; next.startedAt=now; next.attemptCount++; save(next); t.currentStage=next.stage;
            if(db.advanceAttempt(t.id,t.attemptCount,token,next.stage)!=1) throw new IllegalStateException("Execution attempt changed");
            t.leaseExpiresAt=now.plusMillis(policy.leaseMs);
        }
        t.updatedAt=now; update(t);
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public void fail(String id,String token,String code,String message) {
        Task t=db.lockTask(id); Instant now=Instant.now();
        if(!owns(t,token,now)) return;
        finishOrRetry(t,code,message,false,now,"FAILED");
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public void recordFailure(String id,String token,String code,String message,boolean retryable) {
        Task t=db.lockTask(id); Instant now=Instant.now();
        if(!owns(t,token,now)) return;
        finishOrRetry(t,code,message,retryable,now,"RETRY_SCHEDULED");
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public void recoverExpired(String id) {
        Task t=db.lockTask(id); Instant now=Instant.now();
        if(t==null || t.status!=Status.RUNNING || (t.leaseExpiresAt!=null && t.leaseExpiresAt.isAfter(now))) return;
        finishOrRetry(t,"LEASE_EXPIRED","执行租约过期，将从未完成阶段恢复",true,now,"LEASE_EXPIRED");
    }
    private void finishOrRetry(Task t,String code,String message,boolean retryable,Instant now,String outcome) {
        boolean retry=retryable && t.attemptCount<t.maxAttempts;
        finishAttempt(t,retry?outcome:"FAILED",t.currentStage,now,code,message);
        t.lastErrorCode=code; t.lastErrorMessage=message;
        if(retry) {
            t.status=Status.QUEUED; t.nextAttemptAt=now.plusMillis(TaskExecutionPolicy.delay(t));
            Step current=db.steps(t.id).get(STAGES.indexOf(t.currentStage));
            current.status=StepStatus.PENDING; current.startedAt=null; current.finishedAt=null;
            current.errorCode=code; current.errorMessage=message; save(current);
        } else {
            t.status=Status.FAILED; t.failureStage=t.currentStage;
            t.errorCode=retryable?"RETRY_EXHAUSTED":code;
            t.errorMessage=retryable?"执行次数已达上限："+message:message;
            t.finishedAt=now; t.nextAttemptAt=null;
            for(Step s:db.steps(t.id)) {
                if(s.status==StepStatus.RUNNING) { s.status=StepStatus.FAILED; s.errorCode=t.errorCode; s.errorMessage=t.errorMessage; s.finishedAt=now; save(s); }
                else if(s.status==StepStatus.PENDING) { s.status=StepStatus.SKIPPED; s.finishedAt=now; save(s); }
            }
        }
        t.leaseToken=null; t.leaseExpiresAt=null; t.updatedAt=now; update(t);
    }
    private boolean owns(Task t,String token,Instant now) {
        return t!=null && t.status==Status.RUNNING && token!=null && Objects.equals(t.leaseToken,token)
            && t.leaseExpiresAt!=null && t.leaseExpiresAt.isAfter(now);
    }
    private void requireLease(Task t,String token,Instant now) { if(!owns(t,token,now)) throw new LostLeaseException(); }
    private void finishAttempt(Task t,String outcome,Stage stage,Instant now,String code,String message) {
        // Pre-V7 interrupted tasks have no execution attempt record.
        if(t.attemptCount>0 && db.finishAttempt(t.id,t.attemptCount,t.leaseToken,outcome,stage,now,code,message)!=1)
            throw new IllegalStateException("Execution attempt changed");
    }
    private void save(Step s) { if(db.updateStep(s)!=1) throw new IllegalStateException("Missing task step"); }
    private void update(Task t) { if(db.updateTask(t)!=1) throw new IllegalStateException("Concurrent task update"); }
}
