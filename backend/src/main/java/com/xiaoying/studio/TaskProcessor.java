package com.xiaoying.studio;

import java.nio.file.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.xiaoying.studio.Models.*;
import static com.xiaoying.studio.Models.STAGES;

@Service
public class TaskProcessor {
    private static final Logger log = LoggerFactory.getLogger(TaskProcessor.class);
    private final java.util.concurrent.ConcurrentMap<String,String> active=new java.util.concurrent.ConcurrentHashMap<>();
    private final TaskLifecycle lifecycle; private final StudioMapper db; private final FileStorage storage;
    public TaskProcessor(TaskLifecycle lifecycle,StudioMapper db,FileStorage storage) { this.lifecycle=lifecycle; this.db=db; this.storage=storage; }
    public void heartbeat() {
        active.forEach((id,token)->{
            try { if(!lifecycle.renew(id,token)) active.remove(id,token); }
            catch(Exception e) { log.warn("任务 {} 心跳暂不可用，租约保护仍生效",id); }
        });
    }
    public void runNext() {
        Task t=lifecycle.claimNext(); if(t==null) return;
        active.put(t.id,t.leaseToken);
        log.info("任务 {} 开始第 {} 次执行，从阶段 {} 恢复", t.id,t.attemptCount,t.currentStage);
        try {
            for(int index=STAGES.indexOf(t.currentStage);index<STAGES.size();index++) {
                Stage stage=STAGES.get(index);
                if(!lifecycle.renew(t.id,t.leaseToken)) throw new TaskLifecycle.LostLeaseException();
                int delay=switch(stage) { case MATERIAL_CHECK -> t.checkDelayMs; case MOCK_GENERATION -> t.generationDelayMs; default -> t.assemblyDelayMs; };
                log.info("任务 {} 开始阶段 {}，等待 {} ms", t.id, stage, delay);
                Thread.sleep(delay);
                if(stage.name().equals(t.failAtStage.name())) throw new ApiException(500,"SIMULATED_STAGE_FAILURE","模拟失败："+stage.name());
                if(stage.name().equals(t.transientFailAtStage.name()) && t.attemptCount<=t.transientFailureCount)
                    throw new ApiException(503,"SIMULATED_TRANSIENT_FAILURE","模拟暂时失败："+stage.name());
                byte[] content=null;
                switch(stage) {
                    case MATERIAL_CHECK -> {
                        for(Asset a:db.taskAssets(t.id)) if(!Files.isRegularFile(storage.path(a.storageKey)) || Files.size(storage.path(a.storageKey))!=a.sizeBytes)
                            throw new ApiException(500,"MATERIAL_UNAVAILABLE","制作所需素材缺失或已损坏");
                    }
                    case MOCK_GENERATION -> {
                        if(Models.text(t.sampleResultUrl)==null && !Files.isRegularFile(storage.path("samples/"+t.sampleKey+".png"))) throw new ApiException(500,"GENERATION_FAILED","预置生成图片不可用");
                    }
                    case RESULT_ASSEMBLY -> {
                        if(Models.text(t.sampleResultUrl)==null) content=Files.readAllBytes(storage.path("samples/"+t.sampleKey+".png"));
                    }
                    default -> throw new IllegalStateException("Not an executable stage");
                }
                lifecycle.completeStep(t.id,t.leaseToken,stage,content);
                log.info("任务 {} 结束阶段 {}", t.id, stage);
            }
            log.info("任务 {} 后台执行结束，状态 SUCCEEDED", t.id);
        } catch(InterruptedException e) {
            try { lifecycle.recordFailure(t.id,t.leaseToken,"EXECUTION_INTERRUPTED","后台执行中断",true); }
            finally { Thread.currentThread().interrupt(); }
        } catch(TaskLifecycle.LostLeaseException e) { log.info("任务 {} 执行权已失效，停止旧工作线程",t.id); }
        catch(ApiException e) { lifecycle.recordFailure(t.id,t.leaseToken,e.code,e.getMessage(),"SIMULATED_TRANSIENT_FAILURE".equals(e.code)); }
        catch(Exception e) {
            log.error("任务 {} 阶段执行出现未预期错误", t.id, e);
            boolean retryable=e instanceof org.springframework.dao.TransientDataAccessException
                || e instanceof org.springframework.dao.RecoverableDataAccessException
                || e instanceof org.springframework.jdbc.CannotGetJdbcConnectionException
                || e instanceof java.io.IOException;
            lifecycle.recordFailure(t.id,t.leaseToken,"EXECUTION_ERROR","后台制作暂不可用",retryable);
        } finally { active.remove(t.id,t.leaseToken); }
    }
}
