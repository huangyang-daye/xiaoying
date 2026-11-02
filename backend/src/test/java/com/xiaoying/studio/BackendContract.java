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
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired TaskService tasks;
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
        try(var dispatcher=new TaskScheduler(processor,lifecycle,db,2)) { dispatcher.recover(); }
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
    @Test void nicknameUpdateAcceptsConfiguredBrowserOrigin() throws Exception {
        try {
            mvc.perform(patch("/api/account").session(a).header("Origin","http://localhost:3000").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("displayName","演示甲")))).andExpect(status().isOk()).andExpect(jsonPath("$.user.displayName").value("演示甲"));
            mvc.perform(patch("/api/account").session(a).header("Origin","http://evil.example").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("displayName","被拒绝")))).andExpect(status().isForbidden());
            mvc.perform(get("/api/session").session(a)).andExpect(jsonPath("$.user.displayName").value("演示甲"));
        } finally { jdbc.update("UPDATE app_user SET display_name=? WHERE id='demo-a'","演示用户 A"); }
    }
    @Test void accountsRegisterSignInUpdateAndDelete() throws Exception {
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
            "email","not-an-email","username","studio-maker","password","secret-pass")))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
            "email","maker@example.com","username","demo-a","password","secret-pass")))).andExpect(status().isBadRequest());
        try {
            var created=mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                "email","Maker@Example.com","username","Studio-Maker","password","secret-pass","displayName","创作者"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user.username").value("studio-maker"))
                .andExpect(jsonPath("$.user.email").value("maker@example.com")).andExpect(jsonPath("$.user.displayName").value("创作者"))
                .andExpect(jsonPath("$.user.avatarUrl").value(nullValue())).andReturn();
            String userId=json.readTree(created.getResponse().getContentAsByteArray()).get("user").get("id").asText();
            MockHttpSession session=(MockHttpSession)created.getRequest().getSession(false);
            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                "email","other@example.com","username","studio-maker","password","secret-pass")))).andExpect(status().isConflict());
            mvc.perform(post("/api/session/logout").session(session)).andExpect(status().isNoContent());
            mvc.perform(post("/api/session/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                "login","studio-maker","password","wrong-pass")))).andExpect(status().isUnauthorized());
            session=(MockHttpSession)mvc.perform(post("/api/session/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                "login","maker@example.com","password","secret-pass")))).andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(userId)).andReturn().getRequest().getSession(false);
            mvc.perform(patch("/api/account").session(session).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("displayName","新昵称"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.displayName").value("新昵称"));
            mvc.perform(get("/api/session").session(session)).andExpect(jsonPath("$.user.displayName").value("新昵称"));
            mvc.perform(post("/api/account/password").session(session).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                "currentPassword","secret-pass","newPassword","short")))).andExpect(status().isBadRequest());
            mvc.perform(post("/api/account/password").session(session).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                "currentPassword","secret-pass","newPassword","new-secret-9")))).andExpect(status().isOk());
            mvc.perform(post("/api/session/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                "login","studio-maker","password","secret-pass")))).andExpect(status().isUnauthorized());
            mvc.perform(multipart("/api/account/avatar").file(new MockMultipartFile("file","face.png","image/png",image())).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.avatarUrl").value("/files/avatars/"+userId));
            mvc.perform(get("/files/avatars/"+userId).session(b)).andExpect(status().isNotFound());
            mvc.perform(get("/files/avatars/"+userId).session(session)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
            mvc.perform(post("/api/templates").session(session).contentType(MediaType.APPLICATION_JSON).content(templateBody("maker-card","创作者卡片",null,null))).andExpect(status().isCreated());
            String asset=json.readTree(mvc.perform(multipart("/api/assets").file(new MockMultipartFile("file","test.png","image/png",image())).param("templateId","demo-pet").session(session))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray()).get("assetId").asText();
            String taskId=json.readTree(mvc.perform(post("/api/tasks").session(session).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                "templateId","demo-pet","assetIds",List.of(asset),"simulation",Map.of("failAtStage","NONE","materialCheckDelayMs",0,"mockGenerationDelayMs",0,"resultAssemblyDelayMs",0)))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsByteArray()).get("taskId").asText();
            processor.runNext();
            mvc.perform(post("/api/account/delete").session(session).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("password","secret-pass")))).andExpect(status().isBadRequest());
            mvc.perform(get("/api/tasks/"+taskId).session(session)).andExpect(status().isOk());
            mvc.perform(post("/api/account/delete").session(session).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("password","new-secret-9")))).andExpect(status().isNoContent());
            mvc.perform(get("/api/session").session(session)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/tasks/"+taskId).session(b)).andExpect(status().isNotFound());
            mvc.perform(get("/api/templates/maker-card")).andExpect(status().isNotFound());
            mvc.perform(post("/api/session/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                "login","studio-maker","password","new-secret-9")))).andExpect(status().isUnauthorized());
            assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username='studio-maker'",Long.class));
            assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM production_task WHERE id=?",Long.class,taskId));
        } finally { removeAccount("studio-maker"); jdbc.update("DELETE FROM template WHERE id='maker-card'"); }
    }
    void removeAccount(String username) {
        for(String id:jdbc.queryForList("SELECT id FROM app_user WHERE username=?",String.class,username)) {
            jdbc.update("DELETE FROM task_step WHERE task_id IN (SELECT id FROM production_task WHERE owner_id=?)",id);
            jdbc.update("DELETE FROM task_asset WHERE task_id IN (SELECT id FROM production_task WHERE owner_id=?)",id);
            jdbc.update("DELETE FROM production_task WHERE owner_id=?",id);
            jdbc.update("DELETE FROM asset WHERE owner_id=?",id);
            jdbc.update("DELETE FROM template WHERE owner_id=?",id);
            jdbc.update("DELETE FROM app_user WHERE id=?",id);
        }
    }
    @Test void uncommittedAcceptanceIsInvisibleAndRollbackRemovesAllRows() throws Exception {
        String asset=upload(a);
        var executor=Executors.newSingleThreadExecutor();
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        try {
            assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{
                Task created=tasks.create("demo-a",new CreateTask("demo-pet",List.of(asset),new Simulation(Failure.NONE,0,0,0)));
                try { assertNull(executor.submit(lifecycle::claimNext).get(5,TimeUnit.SECONDS)); }
                catch(Exception e) { throw new AssertionError(e); }
                assertNotNull(db.task(created.id));
                throw new IllegalStateException("force acceptance rollback");
            }));
            assertEquals(0,db.taskCount("demo-a",null));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM task_step",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM task_asset",Integer.class));
        } finally { executor.shutdownNow(); }
    }
    @Test void brokenTransitionRollsBackStepAndResultWrites() throws Exception {
        String id=create(Failure.NONE,0); lifecycle.claimNext();
        lifecycle.completeStep(id,Stage.MATERIAL_CHECK,null);
        lifecycle.completeStep(id,Stage.MOCK_GENERATION,null);
        long version=db.task(id).version;
        assertThrows(IllegalStateException.class,()->lifecycle.completeStep(id,Stage.RESULT_ASSEMBLY,null));
        assertEquals(StepStatus.RUNNING,db.steps(id).get(2).status);
        assertEquals(version,db.task(id).version);
        assertNull(db.resultContent(id));
        // A failing SQL write after updating the step must roll that step back too.
        db.insertResult(id,image());
        assertThrows(org.springframework.dao.DataAccessException.class,()->lifecycle.completeStep(id,Stage.RESULT_ASSEMBLY,new byte[]{1}));
        assertEquals(StepStatus.RUNNING,db.steps(id).get(2).status);
        assertEquals(Status.RUNNING,db.task(id).status);
        assertEquals(version,db.task(id).version);
        jdbc.update("DELETE FROM task_result WHERE task_id=?",id);
        lifecycle.completeStep(id,Stage.RESULT_ASSEMBLY,image());
        assertArrayEquals(image(),db.resultContent(id));
        assertEquals(Status.SUCCEEDED,db.task(id).status);
    }
    @Test void concurrentSuccessAndFailureCannotMixTerminalRecords() throws Exception {
        String id=create(Failure.NONE,0); lifecycle.claimNext();
        lifecycle.completeStep(id,Stage.MATERIAL_CHECK,null);
        lifecycle.completeStep(id,Stage.MOCK_GENERATION,null);
        byte[] content=image(); var barrier=new CyclicBarrier(2); var executor=Executors.newFixedThreadPool(2);
        try {
            var completed=executor.submit(()->{ barrier.await(); try { lifecycle.completeStep(id,Stage.RESULT_ASSEMBLY,content); } catch(IllegalStateException expected) { } return null; });
            var failed=executor.submit(()->{ barrier.await(); lifecycle.fail(id,"TEST_FAILURE","concurrent failure"); return null; });
            completed.get(5,TimeUnit.SECONDS); failed.get(5,TimeUnit.SECONDS);
            Task stored=db.task(id); Step last=db.steps(id).get(2);
            if(stored.status==Status.SUCCEEDED) {
                assertEquals(Stage.COMPLETED,stored.currentStage); assertNull(stored.failureStage);
                assertEquals(StepStatus.SUCCEEDED,last.status); assertArrayEquals(content,db.resultContent(id));
            } else {
                assertEquals(Status.FAILED,stored.status); assertEquals(Stage.RESULT_ASSEMBLY,stored.failureStage);
                assertEquals(StepStatus.FAILED,last.status); assertNull(db.resultContent(id));
            }
        } finally { executor.shutdownNow(); }
    }
    @Test void manyWorkersClaimEachTaskExactlyOnce() throws Exception {
        var ids=new HashSet<String>(); for(int i=0;i<8;i++) ids.add(create(Failure.NONE,0));
        var executor=Executors.newFixedThreadPool(8); var start=new CountDownLatch(1);
        try {
            List<Future<Task>> claims=new ArrayList<>();
            for(int i=0;i<8;i++) claims.add(executor.submit(()->{ start.await();
                for(int attempt=0;attempt<100;attempt++) { Task t=lifecycle.claimNext(); if(t!=null) return t; Thread.sleep(10); }
                return null; }));
            start.countDown(); var claimed=new HashSet<String>();
            for(var claim:claims) { Task t=claim.get(10,TimeUnit.SECONDS); assertNotNull(t); assertTrue(claimed.add(t.id)); }
            assertEquals(ids,claimed);
            for(String id:ids) { assertEquals(Status.RUNNING,db.task(id).status); assertEquals(1,db.task(id).version); }
        } finally { executor.shutdownNow(); }
    }

    @Test void activeCursorDoesNotSkipTasksWhenEarlierPageFinishes() throws Exception {
        String oldest=create(Failure.NONE,0), middle=create(Failure.NONE,0), newest=create(Failure.NONE,0);
        var first=tasks.active("demo-a",null,1,2);
        assertEquals(List.of(newest,middle),first.items().stream().map(TaskView::id).toList());
        assertEquals(middle,first.nextCursor());
        // Force the previously returned tasks to finish before the next request.
        jdbc.update("UPDATE production_task SET status='RUNNING' WHERE id IN (?,?)",middle,newest);
        lifecycle.fail(middle,"TEST","finished during pagination"); lifecycle.fail(newest,"TEST","finished during pagination");
        var next=tasks.active("demo-a",first.nextCursor(),2,2);
        assertEquals(List.of(oldest),next.items().stream().map(TaskView::id).toList());
        assertNull(next.nextCursor());
    }

    @Test void rolledBackAccountDeletionPreservesAssetsAndTaskResults() throws Exception {
        String id=create(Failure.NONE,0); processor.runNext();
        Asset asset=db.taskAssets(id).get(0); byte[] content=db.resultContent(id);
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{
            new AccountService(db,files).delete("demo-a",AccountService.DEMO_PASSWORD);
            throw new IllegalStateException("rollback deletion");
        }));
        assertNotNull(db.account("demo-a")); assertEquals(Status.SUCCEEDED,db.task(id).status);
        assertArrayEquals(content,db.resultContent(id)); assertTrue(Files.isRegularFile(files.path(asset.storageKey)));
    }

}
