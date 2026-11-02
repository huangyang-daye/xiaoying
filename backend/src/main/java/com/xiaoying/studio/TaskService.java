package com.xiaoying.studio;

import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.xiaoying.studio.Models.*;
import static com.xiaoying.studio.Models.STAGES;

@Service
public class TaskService {
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private final StudioMapper db; private final CatalogService catalog; private final AssetService assets;
    private final boolean simulationEnabled; private final int checkDelay,generationDelay,assemblyDelay;
    public TaskService(StudioMapper db,CatalogService catalog,AssetService assets,
        @Value("${app.simulation-enabled}") boolean simulationEnabled,@Value("${app.check-delay-ms}") int checkDelay,
        @Value("${app.generation-delay-ms}") int generationDelay,@Value("${app.assembly-delay-ms}") int assemblyDelay) {
        this.db=db; this.catalog=catalog; this.assets=assets; this.simulationEnabled=simulationEnabled;
        this.checkDelay=delay(checkDelay,0); this.generationDelay=delay(generationDelay,0); this.assemblyDelay=delay(assemblyDelay,0);
    }
    private static int delay(Integer value,int fallback) {
        int result=value==null?fallback:value;
        if(result<0 || result>30000) throw ApiException.invalid("阶段耗时须为 0 至 30000 毫秒");
        return result;
    }
    @Transactional
    public Task create(String owner,CreateTask request) {
        log.info("开始受理制作任务，用户 {}，模板 {}", owner, request.templateId());
        try { return accept(owner, request); }
        catch(RuntimeException e) { log.info("制作任务受理结束，未创建任务：{}", e.getMessage()); throw e; }
    }
    private Task accept(String owner,CreateTask request) {
        if(db.lockAccount(owner)==null) throw new ApiException(401,"UNAUTHENTICATED","账户已失效");
        Template template=catalog.require(request.templateId(),owner);
        var ids=request.assetIds();
        if(ids.size()!=template.requiredImageCount || new HashSet<>(ids).size()!=ids.size()) throw new ApiException(400,"INVALID_ASSET_COUNT","图片数量须符合模板要求且不可重复");
        for(String id:ids) {
            Asset a=assets.require(id,owner);
            if(a.sizeBytes>template.maxFileSizeBytes || !List.of(template.acceptedMimeTypes.split(",")).contains(a.mimeType)) throw ApiException.invalid("素材不符合所选模板要求");
        }
        Simulation s=request.simulation();
        if(s!=null && !simulationEnabled) throw new ApiException(403,"SIMULATION_DISABLED","当前环境不接受模拟控制参数");
        Task t=new Task(); t.id=UUID.randomUUID().toString(); t.ownerId=owner; t.templateId=template.id; t.templateTitle=template.title;
        t.sampleKey=template.sampleKey; t.sampleResultUrl=Models.text(template.sampleResultUrl);
        t.status=Status.QUEUED; t.currentStage=Stage.NOT_STARTED;
        t.failAtStage=s==null || s.failAtStage()==null?Failure.NONE:s.failAtStage();
        t.checkDelayMs=delay(s==null?null:s.materialCheckDelayMs(),checkDelay);
        t.generationDelayMs=delay(s==null?null:s.mockGenerationDelayMs(),generationDelay);
        t.assemblyDelayMs=delay(s==null?null:s.resultAssemblyDelayMs(),assemblyDelay);
        t.createdAt=Instant.now(); t.updatedAt=t.createdAt; db.insertTask(t);
        for(int i=0;i<ids.size();i++) db.linkAsset(t.id,ids.get(i),i);
        for(int i=0;i<STAGES.size();i++) db.insertStep(t.id,STAGES.get(i),i+1);
        log.info("制作任务 {} 受理完成，状态 QUEUED，阶段 NOT_STARTED", t.id);
        return t;
    }
    public Task owned(String id,String owner) { Task t=db.task(id); if(t==null || !owner.equals(t.ownerId)) throw ApiException.missing("TASK"); return t; }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public TaskView detail(String id,String owner) { return view(owned(id,owner)); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<TaskView> history(String owner,List<Status> statuses,int page,int size) {
        CatalogService.validatePage(page,size);
        return new Page<>(db.history(owner,statuses,size,(long)(page-1)*size).stream().map(this::view).toList(),db.taskCount(owner,statuses),page,size);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public byte[] resultBytes(String id,String owner) {
        Task t=owned(id,owner);
        if(t.status!=Status.SUCCEEDED) throw ApiException.missing("RESULT");
        return db.resultContent(id);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<TaskView> active(String owner,String before,int page,int size) {
        CatalogService.validatePage(page,size);
        Task anchor=before==null?null:owned(before,owner);
        var rows=db.activeAfter(owner,anchor,size+1,anchor==null?(long)(page-1)*size:0);
        boolean more=rows.size()>size;
        var items=rows.subList(0,Math.min(size,rows.size()));
        return new Page<>(items.stream().map(this::view).toList(),db.taskCount(owner,List.of(Status.QUEUED,Status.RUNNING)),
            page,size,more?items.get(items.size()-1).id:null);
    }
    private static String imageType(String url) {
        String path=url; int query=path.indexOf('?'); if(query>=0) path=path.substring(0,query);
        int hash=path.indexOf('#'); if(hash>=0) path=path.substring(0,hash);
        String lower=path.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if(lower.endsWith(".webp")) return "image/webp";
        if(lower.endsWith(".gif")) return "image/gif";
        return "image/png";
    }
    private TaskView view(Task t) {
        String linked=Models.text(t.sampleResultUrl);
        ResultView result=t.status!=Status.SUCCEEDED?null:linked!=null
            ?new ResultView(t.id,linked,imageType(linked),t.resultSize==null?0:t.resultSize)
            :new ResultView(t.id,"/files/results/"+t.id,"image/png",t.resultSize);
        return new TaskView(t.id,t.templateId,t.templateTitle,t.status,t.currentStage,t.failureStage,t.errorCode,t.errorMessage,result,
            t.version,t.createdAt,t.updatedAt,t.startedAt,t.finishedAt,db.steps(t.id));
    }
}
