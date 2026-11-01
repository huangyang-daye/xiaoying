package com.xiaoying.studio;

import com.xiaoying.studio.Models.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class CatalogService {
    private final StudioMapper db;
    public CatalogService(StudioMapper db) { this.db=db; }
    public Template require(String id) { Template t=db.template(id); if(t==null) throw ApiException.missing("TEMPLATE"); return t; }
    public List<Category> categories() { return db.categories(); }
    public TemplateView detail(String id) { return view(require(id)); }
    public Page<TemplateView> list(String category,int page,int size) {
        validatePage(page,size);
        if(category!=null && categories().stream().noneMatch(c->c.id.equals(category))) throw ApiException.invalid("分类不存在");
        var all=db.templates().stream().filter(t->category==null || t.categoryId.equals(category)).toList();
        return new Page<>(all.stream().skip((long)(page-1)*size).limit(size).map(this::view).toList(),all.size(),page,size);
    }
    private TemplateView view(Template t) { return new TemplateView(t.id,t.categoryId,t.title,t.description,"/files/samples/"+t.sampleKey,t.requiredImageCount,List.of(t.acceptedMimeTypes.split(",")),t.maxFileSizeBytes); }
    public static void validatePage(int page,int size) { if(page<1 || size<1 || size>50) throw ApiException.invalid("page 必须大于 0，pageSize 范围为 1 至 50"); }
}
