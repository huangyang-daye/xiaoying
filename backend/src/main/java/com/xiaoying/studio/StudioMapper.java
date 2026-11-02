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
        fail_at_stage,check_delay_ms,generation_delay_ms,assembly_delay_ms,version,created_at,updated_at,
        idempotency_key,request_hash,max_attempts,retry_base_delay_ms,retry_max_delay_ms,next_attempt_at,transient_fail_at_stage,transient_failure_count)
        VALUES(#{id},#{ownerId},#{templateId},#{templateTitle},#{sampleKey},#{sampleResultUrl},#{status},#{currentStage},
        #{failAtStage},#{checkDelayMs},#{generationDelayMs},#{assemblyDelayMs},#{version},#{createdAt},#{updatedAt},
        #{idempotencyKey},#{requestHash},#{maxAttempts},#{retryBaseDelayMs},#{retryMaxDelayMs},#{nextAttemptAt},#{transientFailAtStage},#{transientFailureCount})
        """) void insertTask(Task t);
    @Insert("INSERT INTO task_asset VALUES(#{task},#{asset},#{slot})") void linkAsset(@Param("task") String task,@Param("asset") String asset,@Param("slot") int slot);
    @Insert("INSERT INTO task_step(task_id,stage,sequence_no,status) VALUES(#{task},#{stage},#{seq},'PENDING')")
    void insertStep(@Param("task") String task,@Param("stage") Stage stage,@Param("seq") int seq);
    @Select("SELECT * FROM production_task WHERE id=#{id}") Task task(String id);
    @Select("SELECT * FROM production_task WHERE id=#{id} FOR UPDATE") Task lockTask(String id);
    @Select("SELECT * FROM production_task WHERE owner_id=#{owner} ORDER BY id FOR UPDATE") List<Task> lockOwnedTasks(String owner);
    @Select("SELECT * FROM app_user WHERE id=#{id} FOR UPDATE") Account lockAccount(String id);
    @Insert("INSERT INTO task_result(task_id,content) VALUES(#{id},#{content})")
    int insertResult(@Param("id") String id,@Param("content") byte[] content);
    @Select("SELECT content FROM task_result WHERE task_id=#{id}")
    @Results(@Result(column="content",property="content",javaType=byte[].class)) TaskResult result(String id);
    default byte[] resultContent(String id) { TaskResult row=result(id); return row==null?null:row.content; }
    @Select("SELECT * FROM task_step WHERE task_id=#{id} ORDER BY sequence_no") List<Step> steps(String id);
    @Select("SELECT a.* FROM asset a JOIN task_asset ta ON a.id=ta.asset_id WHERE ta.task_id=#{id} ORDER BY ta.slot_index") List<Asset> taskAssets(String id);
    @Select("SELECT id FROM production_task WHERE status='QUEUED' AND (next_attempt_at IS NULL OR next_attempt_at <= #{now}) ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED")
    String nextQueued(java.time.Instant now);
    @Update("""
        UPDATE production_task SET status='RUNNING',current_stage=#{currentStage},started_at=COALESCE(started_at,#{startedAt}),
        updated_at=#{updatedAt},attempt_count=#{attemptCount},lease_token=#{leaseToken},lease_expires_at=#{leaseExpiresAt},next_attempt_at=NULL,
        version=version+1 WHERE id=#{id} AND status='QUEUED' AND version=#{version}
        """) int claim(Task t);
    @Update("""
        UPDATE production_task SET status=#{status},current_stage=#{currentStage},failure_stage=#{failureStage},
        error_code=#{errorCode},error_message=#{errorMessage},result_key=#{resultKey},result_size=#{resultSize},
        finished_at=#{finishedAt},updated_at=#{updatedAt},lease_token=#{leaseToken},lease_expires_at=#{leaseExpiresAt},
        next_attempt_at=#{nextAttemptAt},last_error_code=#{lastErrorCode},last_error_message=#{lastErrorMessage},version=version+1 WHERE id=#{id} AND status='RUNNING' AND version=#{version}
        """) int updateTask(Task t);
    @Update("""
        UPDATE task_step SET status=#{status},started_at=#{startedAt},finished_at=#{finishedAt},
        error_code=#{errorCode},error_message=#{errorMessage},attempt_count=#{attemptCount} WHERE task_id=#{taskId} AND stage=#{stage}
        """) int updateStep(Step s);
    @Select("SELECT * FROM production_task WHERE owner_id=#{owner} AND idempotency_key=#{key}")
    Task taskByKey(@Param("owner") String owner,@Param("key") String key);
    @Select("SELECT id FROM production_task WHERE status='RUNNING' AND (lease_expires_at IS NULL OR lease_expires_at <= #{now}) ORDER BY id LIMIT 100")
    List<String> expiredTasks(java.time.Instant now);
    @Update("UPDATE production_task SET lease_expires_at=#{until} WHERE id=#{id} AND status='RUNNING' AND lease_token=#{token} AND lease_expires_at > #{now}")
    int renewLease(@Param("id") String id,@Param("token") String token,@Param("now") java.time.Instant now,@Param("until") java.time.Instant until);
    @Insert("INSERT INTO task_attempt(task_id,attempt_no,lease_token,stage,outcome,started_at) VALUES(#{id},#{attemptCount},#{leaseToken},#{currentStage},'RUNNING',#{updatedAt})")
    int insertAttempt(Task t);
    @Update("""
        UPDATE task_attempt SET outcome=#{outcome},stage=#{stage},finished_at=#{now},error_code=#{code},error_message=#{message}
        WHERE task_id=#{id} AND attempt_no=#{attempt} AND lease_token=#{token} AND outcome='RUNNING'
        """) int finishAttempt(@Param("id") String id,@Param("attempt") int attempt,@Param("token") String token,
            @Param("outcome") String outcome,@Param("stage") Stage stage,@Param("now") java.time.Instant now,
            @Param("code") String code,@Param("message") String message);
    @Update("UPDATE task_attempt SET stage=#{stage} WHERE task_id=#{id} AND attempt_no=#{attempt} AND lease_token=#{token} AND outcome='RUNNING'")
    int advanceAttempt(@Param("id") String id,@Param("attempt") int attempt,@Param("token") String token,@Param("stage") Stage stage);
    @Select("SELECT * FROM task_attempt WHERE task_id=#{id} ORDER BY attempt_no") List<Attempt> attempts(String id);
    @Select("""
        <script>SELECT * FROM production_task WHERE owner_id=#{owner}
        <if test='statuses != null and !statuses.isEmpty()'>AND status IN
        <foreach collection='statuses' item='s' open='(' separator=',' close=')'>#{s}</foreach></if>
        ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}</script>
        """) List<Task> history(@Param("owner") String owner,@Param("statuses") List<Status> statuses,@Param("limit") int limit,@Param("offset") long offset);
    @Select("""
        <script>SELECT * FROM production_task WHERE owner_id=#{owner} AND status IN ('QUEUED','RUNNING')
        <if test='anchor != null'>AND (created_at &lt; #{anchor.createdAt} OR (created_at=#{anchor.createdAt} AND id &lt; #{anchor.id}))</if>
        ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}</script>
        """) List<Task> activeAfter(@Param("owner") String owner,@Param("anchor") Task anchor,@Param("limit") int limit,@Param("offset") long offset);
    @Select("SELECT * FROM app_user WHERE id=#{id}") Account account(String id);
    @Select("SELECT * FROM app_user WHERE username=#{username}") Account accountByUsername(String username);
    @Select("SELECT * FROM app_user WHERE email=#{email}") Account accountByEmail(String email);
    @Insert("""
        INSERT INTO app_user(id,username,email,password_hash,display_name,avatar_key,created_at,updated_at)
        VALUES(#{id},#{username},#{email},#{passwordHash},#{displayName},#{avatarKey},#{createdAt},#{updatedAt})
        """) void insertAccount(Account account);
    @Update("UPDATE app_user SET display_name=#{displayName},updated_at=#{updatedAt} WHERE id=#{id}") int updateProfile(Account account);
    @Update("UPDATE app_user SET password_hash=#{passwordHash},updated_at=#{updatedAt} WHERE id=#{id}") int updatePassword(Account account);
    @Update("UPDATE app_user SET avatar_key=#{avatarKey},updated_at=#{updatedAt} WHERE id=#{id}") int updateAvatar(Account account);
    @Delete("DELETE FROM app_user WHERE id=#{id}") int deleteAccount(String id);
    @Select("SELECT * FROM asset WHERE owner_id=#{owner}") List<Asset> assetsByOwner(String owner);
    @Select("SELECT * FROM production_task WHERE owner_id=#{owner}") List<Task> tasksByOwner(String owner);
    @Select("SELECT * FROM template WHERE owner_id=#{owner}") List<Template> templatesByOwner(String owner);
    @Delete("DELETE FROM task_step WHERE task_id=#{id}") int deleteSteps(String id);
    @Delete("DELETE FROM task_asset WHERE task_id=#{id}") int deleteTaskLinks(String id);
    @Delete("DELETE FROM task_asset WHERE asset_id=#{id}") int deleteAssetLinks(String id);
    @Delete("DELETE FROM production_task WHERE id=#{id}") int deleteTask(String id);
    @Delete("DELETE FROM asset WHERE id=#{id}") int deleteAsset(String id);
    @Delete("DELETE FROM template WHERE id=#{id}") int deleteTemplate(String id);
    @Select("SELECT COUNT(*) FROM template WHERE sample_key=#{key}") int templatesUsingSample(String key);
    @Select("""
        <script>SELECT COUNT(*) FROM production_task WHERE owner_id=#{owner}
        <if test='statuses != null and !statuses.isEmpty()'>AND status IN
        <foreach collection='statuses' item='s' open='(' separator=',' close=')'>#{s}</foreach></if></script>
        """) long taskCount(@Param("owner") String owner,@Param("statuses") List<Status> statuses);
}
