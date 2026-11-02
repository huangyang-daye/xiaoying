package com.xiaoying.studio;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.xiaoying.studio.Models.*;
import static com.xiaoying.studio.Models.STAGES;

/** Every transition locks the parent row before touching steps or results. */
@Service
public class TaskLifecycle {
    private static final Logger log=LoggerFactory.getLogger(TaskLifecycle.class);
    private final StudioMapper db;
    public TaskLifecycle(StudioMapper db) { this.db=db; }
    @Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
    public Task claimNext() {
        String id=db.nextQueued(); // Current locking read; other workers skip locked rows.
        if(id==null) return null;
        log.info("任务 {} 开始认领",id);
        if(db.claim(id)!=1) throw new IllegalStateException("Task claim lost");
        Step first=db.steps(id).get(0);
        if(first.status!=StepStatus.PENDING) throw new IllegalStateException("Invalid initial step");
        first.status=StepStatus.RUNNING; first.startedAt=Instant.now(); save(first);
        log.info("任务 {} 认领结束，状态 RUNNING，阶段 MATERIAL_CHECK",id);
        return db.task(id);
    }
    @Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
    public void completeStep(String id,Stage stage,byte[] content) {
        Task t=db.lockTask(id);
        if(t==null || t.status!=Status.RUNNING || t.currentStage!=stage)
            throw new IllegalStateException("Unexpected task transition");
        int index=STAGES.indexOf(stage);
        if(index<0) throw new IllegalStateException("Not an executable stage");
        var steps=db.steps(id); Instant now=Instant.now();
        Step current=steps.get(index);
        if(current.status!=StepStatus.RUNNING) throw new IllegalStateException("Step is not running");
        current.status=StepStatus.SUCCEEDED; current.finishedAt=now; save(current);
        if(index==STAGES.size()-1) {
            boolean linked=Models.text(t.sampleResultUrl)!=null;
            if(!linked) {
                if(content==null || content.length==0) throw new IllegalStateException("Result is required");
                if(db.insertResult(id,content)!=1) throw new IllegalStateException("Result insert failed");
            }
            t.status=Status.SUCCEEDED; t.currentStage=Stage.COMPLETED;
            t.resultKey=null; t.resultSize=linked?0L:(long)content.length; t.finishedAt=now;
        } else {
            Step next=steps.get(index+1);
            if(next.status!=StepStatus.PENDING) throw new IllegalStateException("Next step is not pending");
            next.status=StepStatus.RUNNING; next.startedAt=now; save(next); t.currentStage=next.stage;
        }
        t.updatedAt=now; update(t);
    }
    @Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
    public void fail(String id,String code,String message) {
        Task t=db.lockTask(id);
        if(t==null || t.status!=Status.RUNNING) { log.info("任务 {} 跳过失败记录",id); return; }
        log.info("任务 {} 开始记录失败，阶段 {}，代码 {}",id,t.currentStage,code);
        Instant now=Instant.now(); t.status=Status.FAILED; t.failureStage=t.currentStage;
        t.errorCode=code; t.errorMessage=message; t.finishedAt=now; t.updatedAt=now;
        for(Step s:db.steps(id)) {
            if(s.status==StepStatus.RUNNING) {
                s.status=StepStatus.FAILED; s.errorCode=code; s.errorMessage=message; s.finishedAt=now; save(s);
            } else if(s.status==StepStatus.PENDING) { s.status=StepStatus.SKIPPED; s.finishedAt=now; save(s); }
        }
        update(t);
        log.info("任务 {} 失败记录结束，阶段 {}",id,t.failureStage);
    }
    private void save(Step s) { if(db.updateStep(s)!=1) throw new IllegalStateException("Missing task step"); }
    private void update(Task t) { if(db.updateTask(t)!=1) throw new IllegalStateException("Concurrent task update"); }
}
