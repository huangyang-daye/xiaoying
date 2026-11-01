package com.xiaoying.studio;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.xiaoying.studio.Models.*;

@RestController
public class ApiController {
    private final CatalogService catalog; private final AssetService assets; private final TaskService tasks;
    private final FileStorage storage; private final boolean demoEnabled;
    public ApiController(CatalogService catalog,AssetService assets,TaskService tasks,FileStorage storage,@Value("${app.demo-enabled}") boolean demoEnabled) {
        this.catalog=catalog; this.assets=assets; this.tasks=tasks; this.storage=storage; this.demoEnabled=demoEnabled;
    }
    private User user(HttpServletRequest request) { User u=viewer(request); if(u==null) throw new ApiException(401,"UNAUTHENTICATED","请先选择演示身份"); return u; }
    private User viewer(HttpServletRequest request) {
        HttpSession session=request.getSession(false);
        return session!=null && session.getAttribute("user") instanceof User u?u:null;
    }
    @PostMapping("/api/session/demo") public Map<String,User> login(@Valid @RequestBody SessionRequest body,HttpServletRequest request) {
        if(!demoEnabled) throw new ApiException(403,"DEMO_DISABLED","当前环境不提供演示身份");
        User u=switch(body.demoKey()) { case "demo-a" -> new User("demo-a","演示用户 A"); case "demo-b" -> new User("demo-b","演示用户 B"); default -> throw ApiException.invalid("demoKey 仅支持 demo-a 或 demo-b"); };
        HttpSession old=request.getSession(false); if(old!=null) old.invalidate();
        request.getSession(true).setAttribute("user",u); return Map.of("user",u);
    }
    @GetMapping("/api/session") public Map<String,User> session(HttpServletRequest request) { return Map.of("user",user(request)); }
    @PostMapping("/api/session/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) { HttpSession s=request.getSession(false); if(s!=null) s.invalidate(); }
    @GetMapping("/api/categories") public List<Category> categories() { return catalog.categories(); }
    @GetMapping("/api/templates") public Page<TemplateView> templates(@RequestParam(required=false) String categoryId,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String scope,HttpServletRequest request) {
        User u=viewer(request); return catalog.list(categoryId,page,pageSize,u==null?null:u.id(),scope);
    }
    @GetMapping("/api/templates/{id}") public TemplateView template(@PathVariable String id,HttpServletRequest request) { User u=viewer(request); return catalog.detail(id,u==null?null:u.id()); }
    @PostMapping("/api/templates") @ResponseStatus(HttpStatus.CREATED)
    public TemplateView createTemplate(@RequestBody TemplateDraft body,HttpServletRequest request) { return catalog.create(user(request).id(),body); }
    @PutMapping("/api/templates/{id}")
    public TemplateView updateTemplate(@PathVariable String id,@RequestBody TemplateDraft body,HttpServletRequest request) { return catalog.update(id,body,user(request).id()); }
    @PostMapping(value="/api/assets",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public AssetView upload(@RequestParam String templateId,@RequestPart MultipartFile file,HttpServletRequest request) { return assets.upload(user(request).id(),templateId,file); }
    @PostMapping("/api/tasks") public ResponseEntity<?> create(@Valid @RequestBody CreateTask body,HttpServletRequest request) {
        Task t=tasks.create(user(request).id(),body);
        return ResponseEntity.accepted().header("Location","/api/tasks/"+t.id).body(Map.of("taskId",t.id,"status",t.status,"currentStage",t.currentStage,"queryUrl","/api/tasks/"+t.id));
    }
    @GetMapping("/api/tasks/{id}") public TaskView task(@PathVariable String id,HttpServletRequest request) { return tasks.detail(id,user(request).id()); }
    @GetMapping("/api/tasks") public Page<TaskView> history(HttpServletRequest request,@RequestParam(required=false) List<Status> status,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) { return tasks.history(user(request).id(),status,page,pageSize); }
    @GetMapping("/api/tasks/active") public Page<TaskView> active(HttpServletRequest request,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) { return tasks.history(user(request).id(),List.of(Status.QUEUED,Status.RUNNING),page,pageSize); }
    @GetMapping("/files/assets/{id}") public ResponseEntity<?> asset(@PathVariable String id,HttpServletRequest request) throws IOException {
        Asset a=assets.require(id,user(request).id()); return file(a.storageKey,a.mimeType,false);
    }
    @GetMapping("/files/results/{id}") public ResponseEntity<?> result(@PathVariable String id,HttpServletRequest request) throws IOException {
        Task t=tasks.owned(id,user(request).id()); if(t.status!=Status.SUCCEEDED || t.resultKey==null) throw ApiException.missing("RESULT");
        return file(t.resultKey,"image/png",false);
    }
    @GetMapping("/files/samples/{key}") public ResponseEntity<?> sample(@PathVariable String key,HttpServletRequest request) throws IOException {
        User u=viewer(request); Boolean pub=catalog.sampleIsPublic(key,u==null?null:u.id());
        if(pub==null) throw ApiException.missing("RESULT");
        return file("samples/"+key+".png","image/png",pub);
    }
    private ResponseEntity<?> file(String key,String mime,boolean publicSample) throws IOException {
        Path path=storage.path(key); if(!Files.isRegularFile(path)) throw ApiException.missing("RESULT");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime)).contentLength(Files.size(path))
            .header("Cache-Control",publicSample?"public, max-age=3600":"private, no-store").body(new FileSystemResource(path));
    }
}
