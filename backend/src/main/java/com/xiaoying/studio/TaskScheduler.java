package com.xiaoying.studio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component("productionTaskDispatcher")
@ConditionalOnProperty(name="app.worker.enabled",havingValue="true",matchIfMissing=true)
public class TaskScheduler {
    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);
    private final TaskProcessor processor; private final TaskLifecycle lifecycle; private final StudioMapper db;
    private volatile boolean ready;
    public TaskScheduler(TaskProcessor processor,TaskLifecycle lifecycle,StudioMapper db) { this.processor=processor; this.lifecycle=lifecycle; this.db=db; }
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        var interrupted=db.interrupted();
        log.info("开始重启归档，待处理 {} 个执行中任务", interrupted.size());
        for(var task:interrupted) lifecycle.fail(task.id,"RESTART_INTERRUPTED","服务重启，原执行任务已中断");
        ready=true;
        log.info("重启归档结束，调度已就绪");
    }
    @Scheduled(fixedDelayString="${app.worker.interval-ms}")
    public void tick() { if(ready) processor.runNext(); }
}
