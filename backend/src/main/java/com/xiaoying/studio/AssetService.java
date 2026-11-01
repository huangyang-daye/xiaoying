package com.xiaoying.studio;

import java.time.Instant;
import java.util.UUID;
import java.nio.file.Files;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.xiaoying.studio.Models.*;

@Service
public class AssetService {
    private final StudioMapper db; private final CatalogService catalog; private final FileStorage storage;
    public AssetService(StudioMapper db,CatalogService catalog,FileStorage storage) { this.db=db; this.catalog=catalog; this.storage=storage; }
    public AssetView upload(String owner,String templateId,MultipartFile file) {
        String mime=storage.inspect(file,catalog.require(templateId));
        Asset a=new Asset(); a.id=UUID.randomUUID().toString(); a.ownerId=owner; a.mimeType=mime;
        String name=file.getOriginalFilename()==null?"image":file.getOriginalFilename().replaceAll("[\\p{Cntrl}/\\\\]","_");
        a.originalName=name.substring(0,Math.min(255,name.length())); a.sizeBytes=file.getSize(); a.createdAt=Instant.now();
        a.storageKey="uploads/"+a.id+(mime.equals("image/png")?".png":".jpg");
        storage.save(file,a.storageKey);
        try { db.insertAsset(a); } catch(RuntimeException e) { storage.delete(a.storageKey); throw e; }
        return new AssetView(a.id,"/files/assets/"+a.id,mime,a.sizeBytes,a.createdAt);
    }
    public Asset require(String id,String owner) {
        Asset a=db.asset(id,owner);
        if(a==null || !Files.isRegularFile(storage.path(a.storageKey))) throw ApiException.missing("ASSET");
        return a;
    }
}
