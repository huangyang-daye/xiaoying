package com.xiaoying.studio;

import com.fasterxml.jackson.databind.*;
import com.xiaoying.studio.Models.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

abstract class BackendContract {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TaskProcessor processor;
    @Autowired TaskLifecycle lifecycle;
    @Autowired StudioMapper db;
    @Autowired FileStorage files;
    MockHttpSession a,b;

    @BeforeEach void reset() throws Exception {
        jdbc.update("DELETE FROM task_step"); jdbc.update("DELETE FROM task_asset");
        jdbc.update("DELETE FROM production_task"); jdbc.update("DELETE FROM asset");
        a=login("demo-a"); b=login("demo-b");
    }
    MockHttpSession login(String key) throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/session/demo").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of("demoKey",key)))).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    byte[] image() throws Exception { return Files.readAllBytes(files.path("samples/pet.png")); }
    String upload(MockHttpSession session) throws Exception {
        var response=mvc.perform(multipart("/api/assets").file(new MockMultipartFile("file","test.png","image/png",image()))
            .param("templateId","demo-pet").session(session)).andExpect(status().isCreated()).andReturn();
        return json.readTree(response.getResponse().getContentAsByteArray()).get("assetId").asText();
    }
    byte[] body(String asset,Failure fail,int delay) throws Exception {
        return json.writeValueAsBytes(Map.of("templateId","demo-pet","assetIds",List.of(asset),"simulation",Map.of(
            "failAtStage",fail.name(),"materialCheckDelayMs",0,"mockGenerationDelayMs",delay,"resultAssemblyDelayMs",0)));
    }
    String create(Failure failure,int delay) throws Exception {
        var response=mvc.perform(post("/api/tasks").session(a).contentType(MediaType.APPLICATION_JSON).content(body(upload(a),failure,delay)))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("QUEUED")).andReturn();
        return json.readTree(response.getResponse().getContentAsByteArray()).get("taskId").asText();
    }
    JsonNode detail(String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/tasks/"+id).session(a)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    @Test void imageLinksComeFromDatabaseWhenPresent() throws Exception {
        try {
            mvc.perform(get("/api/templates/demo-pet")).andExpect(jsonPath("$.coverUrl").value("/files/samples/pet"));
            jdbc.update("UPDATE template SET cover_url='  ', sample_result_url='' WHERE id='demo-pet'");
            mvc.perform(get("/api/templates/demo-pet")).andExpect(jsonPath("$.coverUrl").value("/files/samples/pet"));
            jdbc.update("UPDATE template SET cover_url=?, sample_result_url=? WHERE id='demo-pet'",
                "https://cdn.example/covers/pet.png","https://cdn.example/results/pet.jpg");
            mvc.perform(get("/api/templates/demo-pet")).andExpect(jsonPath("$.coverUrl").value("https://cdn.example/covers/pet.png"));
            String linked=create(Failure.NONE,0); processor.runNext();
            JsonNode task=detail(linked);
            assertEquals("https://cdn.example/results/pet.jpg",task.get("result").get("url").asText());
            assertEquals("image/jpeg",task.get("result").get("mimeType").asText());
            mvc.perform(get("/files/results/"+linked).session(a)).andExpect(status().isNotFound());
            jdbc.update("UPDATE template SET cover_url=NULL, sample_result_url=NULL WHERE id='demo-pet'");
            mvc.perform(get("/api/templates/demo-pet")).andExpect(jsonPath("$.coverUrl").value("/files/samples/pet"));
            assertEquals(task,detail(linked));
            String local=create(Failure.NONE,0); processor.runNext();
            assertEquals("/files/results/"+local,detail(local).get("result").get("url").asText());
            mvc.perform(get("/files/results/"+local).session(a)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        } finally { jdbc.update("UPDATE template SET cover_url=NULL, sample_result_url=NULL WHERE id='demo-pet'"); }
    }
    byte[] templateBody(String id,String title,String cover,String result) throws Exception {
        var body=new LinkedHashMap<String,Object>();
        body.put("id",id); body.put("categoryId","creative"); body.put("title",title); body.put("description","自行添加的模板");
        body.put("requiredImageCount",1); body.put("acceptedMimeTypes",List.of("image/png")); body.put("maxFileSizeBytes",5242880);
        body.put("coverUrl",cover); body.put("sampleResultUrl",result);
        return json.writeValueAsBytes(body);
    }
    @Test void templatesCanBeCreatedAndUpdated() throws Exception {
        mvc.perform(post("/api/templates").contentType(MediaType.APPLICATION_JSON).content(templateBody("custom-card","自定义卡片",null,null))).andExpect(status().isUnauthorized());
        try {
            mvc.perform(post("/api/templates").session(a).contentType(MediaType.APPLICATION_JSON).content(templateBody("Bad ID","自定义卡片",null,null))).andExpect(status().isBadRequest());
            mvc.perform(post("/api/templates").session(a).contentType(MediaType.APPLICATION_JSON).content(templateBody("custom-card","自定义卡片",null,null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ownerId").value("demo-a")).andExpect(jsonPath("$.coverUrl").value("/files/samples/custom-card")).andExpect(jsonPath("$.storedCoverUrl").value(nullValue()));
            mvc.perform(get("/api/templates/custom-card")).andExpect(status().isNotFound());
            mvc.perform(get("/api/templates/custom-card").session(b)).andExpect(status().isNotFound());
            mvc.perform(put("/api/templates/custom-card").session(b).contentType(MediaType.APPLICATION_JSON).content(templateBody("custom-card","别人的标题",null,null))).andExpect(status().isNotFound());
            mvc.perform(get("/api/templates").param("categoryId","creative")).andExpect(jsonPath("$.total").value(2));
            mvc.perform(get("/api/templates").param("scope","mine").session(a)).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value("custom-card"));
            mvc.perform(get("/api/templates/demo-postcard")).andExpect(jsonPath("$.ownerId").value(nullValue()));
            mvc.perform(get("/files/samples/custom-card")).andExpect(status().isNotFound());
            mvc.perform(get("/files/samples/custom-card").session(b)).andExpect(status().isNotFound());
            mvc.perform(get("/files/samples/custom-card").session(a)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
            mvc.perform(multipart("/api/assets").file(new MockMultipartFile("file","test.png","image/png",image())).param("templateId","custom-card").session(b)).andExpect(status().isNotFound());
            mvc.perform(put("/api/templates/custom-card").session(a).contentType(MediaType.APPLICATION_JSON).content(templateBody("custom-card","新标题","https://cdn.example/cover.png","https://cdn.example/result.jpg")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("新标题")).andExpect(jsonPath("$.coverUrl").value("https://cdn.example/cover.png")).andExpect(jsonPath("$.sampleResultUrl").value("https://cdn.example/result.jpg"));
            mvc.perform(put("/api/templates/custom-card").session(a).contentType(MediaType.APPLICATION_JSON).content(templateBody("custom-card","自定义卡片","","")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.coverUrl").value("/files/samples/custom-card")).andExpect(jsonPath("$.storedCoverUrl").value(nullValue()));
            mvc.perform(post("/api/templates").session(a).contentType(MediaType.APPLICATION_JSON).content(templateBody("custom-card","自定义卡片",null,null))).andExpect(status().isConflict());
            mvc.perform(put("/api/templates/missing").session(a).contentType(MediaType.APPLICATION_JSON).content(templateBody("missing","自定义卡片",null,null))).andExpect(status().isNotFound());
            String asset=json.readTree(mvc.perform(multipart("/api/assets").file(new MockMultipartFile("file","test.png","image/png",image())).param("templateId","custom-card").session(a))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray()).get("assetId").asText();
            String id=json.readTree(mvc.perform(post("/api/tasks").session(a).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                "templateId","custom-card","assetIds",List.of(asset),"simulation",Map.of("failAtStage","NONE","materialCheckDelayMs",0,"mockGenerationDelayMs",0,"resultAssemblyDelayMs",0)))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsByteArray()).get("taskId").asText();
            processor.runNext();
            assertEquals("/files/results/"+id,detail(id).get("result").get("url").asText());
        } finally {
            jdbc.update("DELETE FROM task_step"); jdbc.update("DELETE FROM task_asset"); jdbc.update("DELETE FROM production_task"); jdbc.update("DELETE FROM asset");
            jdbc.update("DELETE FROM template WHERE id='custom-card'");
        }
    }
    @Test void catalogAndValidation() throws Exception {
        mvc.perform(get("/api/categories")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/templates").param("categoryId","creative")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/templates/missing")).andExpect(status().isNotFound());
        mvc.perform(get("/api/templates").param("pageSize","0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/assets").file(new MockMultipartFile("file","fake.png","image/png","invalid".getBytes()))
            .param("templateId","demo-pet").session(a)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/assets").file(new MockMultipartFile("file","huge.png","image/png",new byte[5242881]))
            .param("templateId","demo-pet").session(a)).andExpect(status().isPayloadTooLarge());
        String asset=upload(a);
        mvc.perform(post("/api/tasks").session(b).contentType(MediaType.APPLICATION_JSON).content(body(asset,Failure.NONE,0))).andExpect(status().isNotFound());
        mvc.perform(get("/files/assets/"+asset).session(b)).andExpect(status().isNotFound());
        mvc.perform(post("/api/tasks").session(a).contentType(MediaType.APPLICATION_JSON).content(body(asset,Failure.NONE,-1))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/tasks").session(a).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of("templateId","demo-postcard","assetIds",List.of(asset,asset))))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/tasks").session(a).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of("templateId","demo-postcard","assetIds",List.of(asset))))).andExpect(status().isBadRequest());
        assertEquals(0,db.taskCount("demo-a",null));
        mvc.perform(get("/api/no-such-route")).andExpect(status().isNotFound());
        mvc.perform(post("/api/tasks").session(a).contentType(MediaType.APPLICATION_JSON)
            .content(new String(body(asset,Failure.NONE,0),java.nio.charset.StandardCharsets.UTF_8).replace("\"mockGenerationDelayMs\":0","\"mockGenerationDelayMs\":0.5"))).andExpect(status().isBadRequest());
    }
    @Test void successfulPipelineIsPersistentReadOnlyAndOwned() throws Exception {
        String id=create(Failure.NONE,0);
        assertEquals("QUEUED",detail(id).get("status").asText());
        assertEquals("QUEUED",detail(id).get("status").asText());
        processor.runNext();
        JsonNode task=detail(id);
        assertEquals("SUCCEEDED",task.get("status").asText());
        assertEquals("COMPLETED",task.get("currentStage").asText());
        List<Step> steps=db.steps(id);
        assertEquals(Models.STAGES,steps.stream().map(s->s.stage).toList());
        for(int i=0;i<steps.size();i++) {
            assertEquals(StepStatus.SUCCEEDED,steps.get(i).status);
            assertFalse(steps.get(i).finishedAt.isBefore(steps.get(i).startedAt));
            if(i>0) assertFalse(steps.get(i).startedAt.isBefore(steps.get(i-1).finishedAt));
        }
        mvc.perform(get(task.get("result").get("url").asText()).session(a)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get("/api/tasks/"+id).session(b)).andExpect(status().isNotFound());
        mvc.perform(get("/files/results/"+id).session(b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/tasks").session(b)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/tasks/active").session(a)).andExpect(jsonPath("$.total").value(0));
        a=login("demo-a");
        assertEquals(task,detail(id)); // New session reads the same durable result.
        lifecycle.fail(id,"TEST","must not change terminal state");
        processor.runNext();
        assertEquals(task,detail(id));
    }
    @ParameterizedTest @EnumSource(value=Failure.class,names="NONE",mode=EnumSource.Mode.EXCLUDE)
    void failureStopsFollowingSteps(Failure failure) throws Exception {
        String id=create(failure,0); processor.runNext();
        JsonNode task=detail(id);
        assertEquals("FAILED",task.get("status").asText());
        assertEquals(failure.name(),task.get("failureStage").asText());
        assertEquals("SIMULATED_STAGE_FAILURE",task.get("errorCode").asText());
        assertTrue(task.get("result").isNull());
        int failureIndex=Models.STAGES.indexOf(Stage.valueOf(failure.name()));
        var steps=db.steps(id);
        for(int i=0;i<3;i++) assertEquals(i<failureIndex?StepStatus.SUCCEEDED:i==failureIndex?StepStatus.FAILED:StepStatus.SKIPPED,steps.get(i).status);
        mvc.perform(get("/files/results/"+id).session(a)).andExpect(status().isNotFound());
        assertEquals(task,detail(id));
    }
    @Test void queuedTasksAreIndependentAndRequestsDoNotWaitForGeneration() throws Exception {
        String first=create(Failure.NONE,800);
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {
            Future<?> running=executor.submit(processor::runNext);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(db.task(first).currentStage!=Stage.MOCK_GENERATION && System.nanoTime()<deadline) Thread.sleep(10);
            assertEquals(Status.RUNNING,db.task(first).status);
            String second=create(Failure.MATERIAL_CHECK,0);
            assertNotEquals(first,second);
            assertEquals(Status.QUEUED,db.task(second).status);
            running.get(5,TimeUnit.SECONDS);
            processor.runNext();
            assertEquals(Status.SUCCEEDED,db.task(first).status);
            assertEquals(Status.FAILED,db.task(second).status);
        } finally { executor.shutdownNow(); }
    }
    @Test void missingMaterialAndRestartHaveExplicitFailures() throws Exception {
        String id=create(Failure.NONE,0);
        files.delete(db.taskAssets(id).get(0).storageKey);
        processor.runNext();
        assertEquals("MATERIAL_UNAVAILABLE",db.task(id).errorCode);
        String interrupted=create(Failure.NONE,0);
        assertEquals(interrupted,lifecycle.claimNext().id);
        new TaskScheduler(processor,lifecycle,db).recover();
        assertEquals("RESTART_INTERRUPTED",db.task(interrupted).errorCode);
        assertEquals(StepStatus.SKIPPED,db.steps(interrupted).get(1).status);
    }
    @Test void concurrentClaimsOnlyHaveOneWinner() throws Exception {
        String id=create(Failure.NONE,0);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var calls=pool.invokeAll(List.<Callable<Task>>of(lifecycle::claimNext,lifecycle::claimNext));
            int winners=0; for(var f:calls) if(f.get()!=null) winners++;
            assertEquals(1,winners); assertEquals(Status.RUNNING,db.task(id).status);
        } finally { pool.shutdownNow(); }
    }
}
