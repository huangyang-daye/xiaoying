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
    private final TaskLifecycle lifecycle; private final StudioMapper db; private final FileStorage storage;
    public TaskProcessor(TaskLifecycle lifecycle,StudioMapper db,FileStorage storage) { this.lifecycle=lifecycle; this.db=db; this.storage=storage; }
    public void runNext() {
        Task t=lifecycle.claimNext(); if(t==null) return;
        log.info("任务 {} 开始后台执行，模板 {}", t.id, t.templateId);
        try {
            for(Stage stage:STAGES) {
                int delay=switch(stage) { case MATERIAL_CHECK -> t.checkDelayMs; case MOCK_GENERATION -> t.generationDelayMs; default -> t.assemblyDelayMs; };
                log.info("任务 {} 开始阶段 {}，等待 {} ms", t.id, stage, delay);
                Thread.sleep(delay);
                if(stage.name().equals(t.failAtStage.name())) throw new ApiException(500,"SIMULATED_STAGE_FAILURE","模拟失败："+stage.name());
                String resultKey=null; Long resultSize=null;
                switch(stage) {
                    case MATERIAL_CHECK -> {
                        for(Asset a:db.taskAssets(t.id)) if(!Files.isRegularFile(storage.path(a.storageKey)) || Files.size(storage.path(a.storageKey))!=a.sizeBytes)
                            throw new ApiException(500,"MATERIAL_UNAVAILABLE","制作所需素材缺失或已损坏");
                    }
                    case MOCK_GENERATION -> {
                        if(Models.text(t.sampleResultUrl)==null && !Files.isRegularFile(storage.path("samples/"+t.sampleKey+".png"))) throw new ApiException(500,"GENERATION_FAILED","预置生成图片不可用");
                    }
                    case RESULT_ASSEMBLY -> {
                        if(Models.text(t.sampleResultUrl)==null) { resultKey=storage.assemble(t); resultSize=Files.size(storage.path(resultKey)); }
                        else resultSize=0L;
                    }
                    default -> throw new IllegalStateException("Not an executable stage");
                }
                lifecycle.completeStep(t.id,stage,resultKey,resultSize);
                log.info("任务 {} 结束阶段 {}", t.id, stage);
            }
            log.info("任务 {} 后台执行结束，状态 SUCCEEDED", t.id);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt(); lifecycle.fail(t.id,"RESTART_INTERRUPTED","后台执行中断，请重新提交任务");
        } catch(ApiException e) { lifecycle.fail(t.id,e.code,e.getMessage()); }
        catch(Exception e) {
            log.error("任务 {} 阶段执行出现未预期错误", t.id, e);
            lifecycle.fail(t.id,"EXECUTION_ERROR","后台制作失败，请稍后重新提交");
        }
    }
}
