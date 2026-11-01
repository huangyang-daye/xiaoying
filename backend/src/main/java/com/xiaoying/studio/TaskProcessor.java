package com.xiaoying.studio;

import java.nio.file.Files;
import org.springframework.stereotype.Service;
import com.xiaoying.studio.Models.*;
import static com.xiaoying.studio.Models.STAGES;

@Service
public class TaskProcessor {
    private final TaskLifecycle lifecycle; private final StudioMapper db; private final FileStorage storage;
    public TaskProcessor(TaskLifecycle lifecycle,StudioMapper db,FileStorage storage) { this.lifecycle=lifecycle; this.db=db; this.storage=storage; }
    public void runNext() {
        Task t=lifecycle.claimNext(); if(t==null) return;
        try {
            for(Stage stage:STAGES) {
                Thread.sleep(switch(stage) { case MATERIAL_CHECK -> t.checkDelayMs; case MOCK_GENERATION -> t.generationDelayMs; default -> t.assemblyDelayMs; });
                if(stage.name().equals(t.failAtStage.name())) throw new ApiException(500,"SIMULATED_STAGE_FAILURE","模拟失败："+stage.name());
                String resultKey=null; Long resultSize=null;
                switch(stage) {
                    case MATERIAL_CHECK -> {
                        for(Asset a:db.taskAssets(t.id)) if(!Files.isRegularFile(storage.path(a.storageKey)) || Files.size(storage.path(a.storageKey))!=a.sizeBytes)
                            throw new ApiException(500,"MATERIAL_UNAVAILABLE","制作所需素材缺失或已损坏");
                    }
                    case MOCK_GENERATION -> {
                        if(!Files.isRegularFile(storage.path("samples/"+t.sampleKey+".png"))) throw new ApiException(500,"GENERATION_FAILED","预置生成图片不可用");
                    }
                    case RESULT_ASSEMBLY -> { resultKey=storage.assemble(t); resultSize=Files.size(storage.path(resultKey)); }
                    default -> throw new IllegalStateException("Not an executable stage");
                }
                lifecycle.completeStep(t.id,stage,resultKey,resultSize);
            }
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt(); lifecycle.fail(t.id,"RESTART_INTERRUPTED","后台执行中断，请重新提交任务");
        } catch(ApiException e) { lifecycle.fail(t.id,e.code,e.getMessage()); }
        catch(Exception e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error("Task execution failed: {}",t.id,e);
            lifecycle.fail(t.id,"EXECUTION_ERROR","后台制作失败，请稍后重新提交");
        }
    }
}
