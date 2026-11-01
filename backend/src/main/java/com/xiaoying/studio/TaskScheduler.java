package com.xiaoying.studio;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component("productionTaskDispatcher")
@ConditionalOnProperty(name="app.worker.enabled",havingValue="true",matchIfMissing=true)
public class TaskScheduler {
    private final TaskProcessor processor; private final TaskLifecycle lifecycle; private final StudioMapper db;
    private volatile boolean ready;
    public TaskScheduler(TaskProcessor processor,TaskLifecycle lifecycle,StudioMapper db) { this.processor=processor; this.lifecycle=lifecycle; this.db=db; }
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        for(var task:db.interrupted()) lifecycle.fail(task.id,"RESTART_INTERRUPTED","服务重启，原执行任务已中断");
        ready=true;
    }
    @Scheduled(fixedDelayString="${app.worker.interval-ms}")
    public void tick() { if(ready) processor.runNext(); }
}
