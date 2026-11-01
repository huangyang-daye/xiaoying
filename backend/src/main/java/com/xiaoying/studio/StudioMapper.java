package com.xiaoying.studio;

import java.util.List;
import org.apache.ibatis.annotations.*;
import com.xiaoying.studio.Models.*;

@Mapper
public interface StudioMapper {
    @Select("SELECT * FROM template_category ORDER BY sort_order,id") List<Category> categories();
    @Select("SELECT * FROM template ORDER BY id") List<Template> templates();
    @Select("SELECT * FROM template WHERE id=#{id}") Template template(String id);
    @Insert("""
        INSERT INTO template(id,category_id,title,description,required_image_count,accepted_mime_types,max_file_size_bytes,sample_key,cover_url,sample_result_url,owner_id)
        VALUES(#{id},#{categoryId},#{title},#{description},#{requiredImageCount},#{acceptedMimeTypes},#{maxFileSizeBytes},#{sampleKey},#{coverUrl},#{sampleResultUrl},#{ownerId})
        """) void insertTemplate(Template t);
    @Update("""
        UPDATE template SET category_id=#{categoryId},title=#{title},description=#{description},required_image_count=#{requiredImageCount},
        accepted_mime_types=#{acceptedMimeTypes},max_file_size_bytes=#{maxFileSizeBytes},cover_url=#{coverUrl},sample_result_url=#{sampleResultUrl}
        WHERE id=#{id}
        """) int updateTemplate(Template t);
    @Insert("INSERT INTO asset VALUES(#{id},#{ownerId},#{originalName},#{storageKey},#{mimeType},#{sizeBytes},#{createdAt})") void insertAsset(Asset a);
    @Select("SELECT * FROM asset WHERE id=#{id} AND owner_id=#{owner}") Asset asset(@Param("id") String id,@Param("owner") String owner);
    @Insert("""
        INSERT INTO production_task(id,owner_id,template_id,template_title,sample_key,sample_result_url,status,current_stage,
        fail_at_stage,check_delay_ms,generation_delay_ms,assembly_delay_ms,version,created_at,updated_at)
        VALUES(#{id},#{ownerId},#{templateId},#{templateTitle},#{sampleKey},#{sampleResultUrl},#{status},#{currentStage},
        #{failAtStage},#{checkDelayMs},#{generationDelayMs},#{assemblyDelayMs},#{version},#{createdAt},#{updatedAt})
        """) void insertTask(Task t);
    @Insert("INSERT INTO task_asset VALUES(#{task},#{asset},#{slot})") void linkAsset(@Param("task") String task,@Param("asset") String asset,@Param("slot") int slot);
    @Insert("INSERT INTO task_step(task_id,stage,sequence_no,status) VALUES(#{task},#{stage},#{seq},'PENDING')")
    void insertStep(@Param("task") String task,@Param("stage") Stage stage,@Param("seq") int seq);
    @Select("SELECT * FROM production_task WHERE id=#{id}") Task task(String id);
    @Select("SELECT * FROM task_step WHERE task_id=#{id} ORDER BY sequence_no") List<Step> steps(String id);
    @Select("SELECT a.* FROM asset a JOIN task_asset ta ON a.id=ta.asset_id WHERE ta.task_id=#{id} ORDER BY ta.slot_index") List<Asset> taskAssets(String id);
    @Select("SELECT id FROM production_task WHERE status='QUEUED' ORDER BY created_at,id LIMIT 1") String nextQueued();
    @Update("UPDATE production_task SET status='RUNNING',current_stage='MATERIAL_CHECK',started_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6),version=version+1 WHERE id=#{id} AND status='QUEUED'") int claim(String id);
    @Update("""
        UPDATE production_task SET status=#{status},current_stage=#{currentStage},failure_stage=#{failureStage},
        error_code=#{errorCode},error_message=#{errorMessage},result_key=#{resultKey},result_size=#{resultSize},
        finished_at=#{finishedAt},updated_at=#{updatedAt},version=version+1 WHERE id=#{id} AND status='RUNNING'
        """) int updateTask(Task t);
    @Update("""
        UPDATE task_step SET status=#{status},started_at=#{startedAt},finished_at=#{finishedAt},
        error_code=#{errorCode},error_message=#{errorMessage} WHERE task_id=#{taskId} AND stage=#{stage}
        """) void updateStep(Step s);
    @Select("SELECT * FROM production_task WHERE status='RUNNING'") List<Task> interrupted();
    @Select("""
        <script>SELECT * FROM production_task WHERE owner_id=#{owner}
        <if test='statuses != null and !statuses.isEmpty()'>AND status IN
        <foreach collection='statuses' item='s' open='(' separator=',' close=')'>#{s}</foreach></if>
        ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}</script>
        """) List<Task> history(@Param("owner") String owner,@Param("statuses") List<Status> statuses,@Param("limit") int limit,@Param("offset") long offset);
    @Select("""
        <script>SELECT COUNT(*) FROM production_task WHERE owner_id=#{owner}
        <if test='statuses != null and !statuses.isEmpty()'>AND status IN
        <foreach collection='statuses' item='s' open='(' separator=',' close=')'>#{s}</foreach></if></script>
        """) long taskCount(@Param("owner") String owner,@Param("statuses") List<Status> statuses);
}
