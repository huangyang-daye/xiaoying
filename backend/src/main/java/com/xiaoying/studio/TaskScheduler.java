package com.xiaoying.studio;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component("productionTaskDispatcher")
@ConditionalOnProperty(name="app.worker.enabled",havingValue="true",matchIfMissing=true)
public class TaskScheduler implements AutoCloseable {
    private static final Logger log=LoggerFactory.getLogger(TaskScheduler.class);
    private final TaskProcessor processor; private final TaskLifecycle lifecycle; private final StudioMapper db;
    private final ThreadPoolExecutor workers; private final Semaphore capacity; private final int concurrency;
    private volatile boolean ready;
    public TaskScheduler(TaskProcessor processor,TaskLifecycle lifecycle,StudioMapper db,
                         @Value("${app.worker.concurrency:4}") int concurrency) {
        if(concurrency<1 || concurrency>32) throw new IllegalArgumentException("Worker concurrency must be 1..32");
        this.processor=processor; this.lifecycle=lifecycle; this.db=db; this.concurrency=concurrency;
        capacity=new Semaphore(concurrency); AtomicInteger sequence=new AtomicInteger();
        workers=new ThreadPoolExecutor(concurrency,concurrency,0L,TimeUnit.MILLISECONDS,
            new SynchronousQueue<>(),r->new Thread(r,"production-worker-"+sequence.incrementAndGet()),
            new ThreadPoolExecutor.AbortPolicy());
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        var interrupted=db.interrupted();
        log.info("开始重启归档，待处理 {} 个执行中任务",interrupted.size());
        for(var task:interrupted) lifecycle.fail(task.id,"RESTART_INTERRUPTED","服务重启，原执行任务已中断");
        ready=true;
        log.info("重启归档结束，调度已就绪，并发上限 {}",concurrency);
    }
    @Scheduled(fixedDelayString="${app.worker.interval-ms}")
    public void tick() {
        if(!ready) return;
        for(int i=0;i<concurrency && capacity.tryAcquire();i++) {
            try {
                workers.execute(()->{
                    try { processor.runNext(); }
                    catch(Exception e) { log.error("后台工作线程执行失败，未结束任务保留在数据库中",e); }
                    finally { capacity.release(); }
                });
            } catch(RejectedExecutionException e) { capacity.release(); break; }
        }
    }
    @PreDestroy
    public void close() {
        ready=false; workers.shutdown();
        try {
            if(!workers.awaitTermination(10,TimeUnit.SECONDS)) {
                workers.shutdownNow(); workers.awaitTermination(5,TimeUnit.SECONDS);
            }
        } catch(InterruptedException e) { workers.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}
