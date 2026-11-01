package com.xiaoying.studio;

import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import com.xiaoying.studio.Models.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:scheduling;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa","spring.datasource.password=","app.storage-root=target/test-storage-scheduling",
    "app.worker.enabled=true","app.worker.interval-ms=50"})
class BackendSchedulingTest {
    @Autowired AssetService assets;
    @Autowired TaskService tasks;
    @Autowired StudioMapper db;
    @Autowired FileStorage storage;

    @Test void realSchedulerCompletesWithoutQueryOrManualWorkerInvocation() throws Exception {
        var file=new MockMultipartFile("file","sample.png","image/png",Files.readAllBytes(storage.path("samples/pet.png")));
        var asset=assets.upload("demo-a","demo-pet",file);
        var task=tasks.create("demo-a",new CreateTask("demo-pet",List.of(asset.assetId()),new Simulation(Failure.NONE,0,0,0)));
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while(db.task(task.id).status!=Status.SUCCEEDED && System.nanoTime()<deadline) Thread.sleep(25);
        assertEquals(Status.SUCCEEDED,db.task(task.id).status);
        assertEquals(3,db.steps(task.id).stream().filter(s->s.status==StepStatus.SUCCEEDED).count());
    }
}
