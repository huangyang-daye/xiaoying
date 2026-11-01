package com.xiaoying.studio;

import com.xiaoying.studio.Models.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private static final List<String> MIME_TYPES=List.of("image/jpeg","image/png");
    private final StudioMapper db; private final FileStorage storage;
    public CatalogService(StudioMapper db,FileStorage storage) { this.db=db; this.storage=storage; }
    public Template require(String id,String viewer) { Template t=db.template(id); if(t==null || !visible(t,viewer)) throw ApiException.missing("TEMPLATE"); return t; }
    public List<Category> categories() { return db.categories(); }
    public TemplateView detail(String id,String viewer) { return view(require(id,viewer)); }
    public Page<TemplateView> list(String category,int page,int size,String viewer,String scope) {
        validatePage(page,size);
        if(category!=null && categories().stream().noneMatch(c->c.id.equals(category))) throw ApiException.invalid("分类不存在");
        String mode=scope==null || scope.isBlank()?"all":scope;
        if(!mode.equals("all") && !mode.equals("public") && !mode.equals("mine")) throw ApiException.invalid("scope 仅支持 all、public 或 mine");
        var all=db.templates().stream().filter(t->category==null || t.categoryId.equals(category)).filter(t->switch(mode) {
            case "public" -> t.ownerId==null;
            case "mine" -> viewer!=null && viewer.equals(t.ownerId);
            default -> visible(t,viewer);
        }).toList();
        return new Page<>(all.stream().skip((long)(page-1)*size).limit(size).map(this::view).toList(),all.size(),page,size);
    }
    public Boolean sampleIsPublic(String key,String viewer) {
        Boolean pub=null;
        for(Template t:db.templates()) if(key.equals(t.sampleKey) && visible(t,viewer)) pub=Boolean.TRUE.equals(pub) || t.ownerId==null;
        return pub;
    }
    private static boolean visible(Template t,String viewer) { return t.ownerId==null || (viewer!=null && viewer.equals(t.ownerId)); }
    private TemplateView view(Template t) {
        String cover=Models.text(t.coverUrl);
        if(cover==null) cover="/files/samples/"+t.sampleKey;
        return new TemplateView(t.id,t.categoryId,t.title,t.description,cover,t.requiredImageCount,List.of(t.acceptedMimeTypes.split(",")),t.maxFileSizeBytes,Models.text(t.coverUrl),Models.text(t.sampleResultUrl),t.ownerId);
    }
    public TemplateView create(String owner,TemplateDraft draft) {
        Template t=normalize(draft,true); t.ownerId=owner;
        if(db.template(t.id)!=null) throw new ApiException(409,"TEMPLATE_EXISTS","模板编号已存在");
        ensureSample(t.sampleKey);
        try { db.insertTemplate(t); }
        catch(DuplicateKeyException e) { throw new ApiException(409,"TEMPLATE_EXISTS","模板编号已存在"); }
        return view(t);
    }
    public TemplateView update(String id,TemplateDraft draft,String viewer) {
        Template existing=require(id,viewer);
        Template t=normalize(draft,false); t.id=existing.id; t.sampleKey=existing.sampleKey;
        ensureSample(t.sampleKey);
        if(db.updateTemplate(t)!=1) throw ApiException.missing("TEMPLATE");
        return view(require(id,viewer));
    }
    private void ensureSample(String key) {
        try { storage.ensureSample(key); }
        catch(IOException e) { throw new ApiException(503,"STORAGE_UNAVAILABLE","示例图生成失败"); }
    }
    private Template normalize(TemplateDraft draft,boolean creating) {
        if(draft==null) throw ApiException.invalid("请填写模板内容");
        Template t=new Template();
        if(creating) {
            String id=Models.text(draft.id());
            if(id==null || !id.matches("[a-z0-9][a-z0-9-]{0,62}")) throw ApiException.invalid("模板编号须为 1 至 63 位小写字母、数字或连字符，并以字母或数字开头");
            if(id.equals("manage")) throw ApiException.invalid("模板编号不能使用 manage");
            t.id=id; t.sampleKey=id;
        }
        String title=Models.text(draft.title());
        if(title==null || title.length()>80) throw ApiException.invalid("标题须为 1 至 80 个字符");
        String description=draft.description()==null?"":draft.description().trim();
        if(description.length()>1000) throw ApiException.invalid("描述不能超过 1000 个字符");
        String category=Models.text(draft.categoryId());
        if(category==null || categories().stream().noneMatch(c->c.id.equals(category))) throw ApiException.invalid("请选择已有分类");
        if(draft.requiredImageCount()==null || draft.requiredImageCount()<1 || draft.requiredImageCount()>3) throw ApiException.invalid("所需图片数量须为 1 至 3");
        if(draft.acceptedMimeTypes()==null || draft.acceptedMimeTypes().isEmpty()) throw ApiException.invalid("请至少选择一种图片类型");
        var mimes=draft.acceptedMimeTypes().stream().map(Models::text).filter(Objects::nonNull).distinct().toList();
        if(mimes.size()!=draft.acceptedMimeTypes().size() || mimes.stream().anyMatch(mime->!MIME_TYPES.contains(mime))) throw ApiException.invalid("图片类型仅支持 image/jpeg 和 image/png，且不可重复");
        if(draft.maxFileSizeBytes()==null || draft.maxFileSizeBytes()<1 || draft.maxFileSizeBytes()>10485760) throw ApiException.invalid("单张图片大小上限须为 1 至 10485760 字节");
        t.categoryId=category; t.title=title; t.description=description; t.requiredImageCount=draft.requiredImageCount();
        t.acceptedMimeTypes=MIME_TYPES.stream().filter(mimes::contains).collect(Collectors.joining(","));
        t.maxFileSizeBytes=draft.maxFileSizeBytes(); t.coverUrl=link(draft.coverUrl()); t.sampleResultUrl=link(draft.sampleResultUrl());
        return t;
    }
    private static String link(String value) {
        String text=Models.text(value);
        if(text!=null && text.length()>2048) throw ApiException.invalid("图片链接不能超过 2048 个字符");
        return text;
    }
    public static void validatePage(int page,int size) { if(page<1 || size<1 || size>50) throw ApiException.invalid("page 必须大于 0，pageSize 范围为 1 至 50"); }
}
