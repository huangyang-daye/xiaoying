package com.xiaoying.studio;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xiaoying.studio.Models.*;
import static com.xiaoying.studio.Models.STAGES;

@Service
public class TaskLifecycle {
    private static final Logger log = LoggerFactory.getLogger(TaskLifecycle.class);
    private final StudioMapper db;
    public TaskLifecycle(StudioMapper db) { this.db=db; }
    @Transactional
    public Task claimNext() {
        String id=db.nextQueued();
        if(id==null) return null;
        log.info("任务 {} 开始认领", id);
        if(db.claim(id)!=1) { log.info("任务 {} 认领结束，未获得执行权", id); return null; }
        Step first=db.steps(id).get(0); first.status=StepStatus.RUNNING; first.startedAt=Instant.now(); db.updateStep(first);
        log.info("任务 {} 认领结束，状态 RUNNING，阶段 MATERIAL_CHECK", id);
        return db.task(id);
    }
    @Transactional
    public void completeStep(String id,Stage stage,String resultKey,Long resultSize) {
        Task t=db.task(id);
        if(t.status!=Status.RUNNING || t.currentStage!=stage) throw new IllegalStateException("Unexpected task transition");
        var steps=db.steps(id); int index=STAGES.indexOf(stage); Instant now=Instant.now();
        Step current=steps.get(index); current.status=StepStatus.SUCCEEDED; current.finishedAt=now; db.updateStep(current);
        if(index==STAGES.size()-1) {
            boolean linked=Models.text(t.sampleResultUrl)!=null;
            if(!linked && (resultKey==null || resultSize==null)) throw new IllegalStateException("Result is required");
            t.status=Status.SUCCEEDED; t.currentStage=Stage.COMPLETED; t.resultKey=linked?null:resultKey; t.resultSize=resultSize==null?0L:resultSize; t.finishedAt=now;
        } else {
            Step next=steps.get(index+1); next.status=StepStatus.RUNNING; next.startedAt=now; db.updateStep(next); t.currentStage=next.stage;
        }
        t.updatedAt=now; if(db.updateTask(t)!=1) throw new IllegalStateException("Concurrent task update");
    }
    @Transactional
    public void fail(String id,String code,String message) {
        Task t=db.task(id);
        if(t==null || t.status!=Status.RUNNING) { log.info("任务 {} 跳过失败记录，当前状态 {}", id, t==null?"缺失":t.status); return; }
        log.info("任务 {} 开始记录失败，阶段 {}，代码 {}", id, t.currentStage, code);
        Instant now=Instant.now(); t.status=Status.FAILED; t.failureStage=t.currentStage;
        t.errorCode=code; t.errorMessage=message; t.finishedAt=now; t.updatedAt=now;
        for(Step s:db.steps(id)) {
            if(s.status==StepStatus.RUNNING) { s.status=StepStatus.FAILED; s.errorCode=code; s.errorMessage=message; s.finishedAt=now; db.updateStep(s); }
            else if(s.status==StepStatus.PENDING) { s.status=StepStatus.SKIPPED; s.finishedAt=now; db.updateStep(s); }
        }
        if(db.updateTask(t)!=1) throw new IllegalStateException("Concurrent task update");
        log.info("任务 {} 失败记录结束，状态 FAILED，阶段 {}", id, t.failureStage);
    }
}
