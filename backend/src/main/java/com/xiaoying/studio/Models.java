package com.xiaoying.studio;

import java.time.Instant;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class Models {
    private Models() {}
    public enum Status { QUEUED, RUNNING, SUCCEEDED, FAILED }
    public enum Stage { NOT_STARTED, MATERIAL_CHECK, MOCK_GENERATION, RESULT_ASSEMBLY, COMPLETED }
    public enum Failure { NONE, MATERIAL_CHECK, MOCK_GENERATION, RESULT_ASSEMBLY }
    public enum StepStatus { PENDING, RUNNING, SUCCEEDED, FAILED, SKIPPED }
    public static final List<Stage> STAGES = List.of(Stage.MATERIAL_CHECK, Stage.MOCK_GENERATION, Stage.RESULT_ASSEMBLY);
    public record User(String id, String displayName) {}
    public record SessionRequest(@NotBlank String demoKey) {}
    public record Simulation(Failure failAtStage, @Min(0) @Max(30000) Integer materialCheckDelayMs,
                             @Min(0) @Max(30000) Integer mockGenerationDelayMs,
                             @Min(0) @Max(30000) Integer resultAssemblyDelayMs) {}
    public record CreateTask(@NotBlank String templateId, @NotEmpty @Size(max=3) List<@NotBlank String> assetIds,
                             @Valid Simulation simulation) {}
    public record Page<T>(List<T> items, long total, int page, int pageSize) {}
    public static class Category { public String id, name; public int sortOrder; }
    public static class Template {
        public String id, categoryId, title, description, acceptedMimeTypes, sampleKey;
        public int requiredImageCount; public long maxFileSizeBytes;
    }
    public static class Asset {
        public String id, ownerId, originalName, storageKey, mimeType;
        public long sizeBytes; public Instant createdAt;
    }
    public static class Task {
        public String id, ownerId, templateId, templateTitle, sampleKey, errorCode, errorMessage, resultKey;
        public Status status; public Stage currentStage, failureStage; public Failure failAtStage;
        public int checkDelayMs, generationDelayMs, assemblyDelayMs;
        public Long resultSize; public long version;
        public Instant createdAt, updatedAt, startedAt, finishedAt;
    }
    public static class Step {
        public String taskId, errorCode, errorMessage; public Stage stage; public int sequenceNo;
        public StepStatus status; public Instant startedAt, finishedAt;
    }
    public record TemplateView(String id, String categoryId, String title, String description, String coverUrl,
                               int requiredImageCount, List<String> acceptedMimeTypes, long maxFileSizeBytes) {}
    public record AssetView(String assetId, String previewUrl, String mimeType, long sizeBytes, Instant createdAt) {}
    public record ResultView(String id, String url, String mimeType, long sizeBytes) {}
    public record TaskView(String id, String templateId, String templateTitle, Status status, Stage currentStage,
                           Stage failureStage, String errorCode, String errorMessage, ResultView result,
                           long version, Instant createdAt, Instant updatedAt, Instant startedAt, Instant finishedAt,
                           List<Step> steps) {}
}
