import { describe, expect, it } from "vitest";
import { failedUpload, materialRequirement, rejectedSelection, uploadingSelection, type Slot } from "./upload-slot";

const both = { requiredImageCount: 2, acceptedMimeTypes: ["image/jpeg", "image/png"], maxFileSizeBytes: 5242880 };
const pngOnly = { requiredImageCount: 1, acceptedMimeTypes: ["image/png"], maxFileSizeBytes: 1048576 };
const asset = { assetId: "a1", previewUrl: "/files/assets/a1", mimeType: "image/png", sizeBytes: 12 };
const ready: Slot = { asset, uploading: false };

describe("template upload rules", () => {
  it("describes the types and size required by that template", () => {
    expect(materialRequirement(both)).toBe("需要 2 张 · JPEG / PNG · 每张不超过 5 MiB");
    expect(materialRequirement(pngOnly)).toBe("需要 1 张 · PNG · 每张不超过 1 MiB");
  });
  it("keeps a ready image when a replacement is rejected or fails", () => {
    expect(rejectedSelection(ready, { type: "text/plain", size: 4 }, pngOnly)).toEqual({ asset, uploading: false, error: "请选择 PNG 图片。" });
    expect(rejectedSelection(ready, { type: "text/plain", size: 4 }, both)?.error).toBe("请选择 JPEG 或 PNG 图片。");
    expect(rejectedSelection(ready, { type: "image/png", size: 0 }, pngOnly)?.asset).toBe(asset);
    expect(rejectedSelection(ready, { type: "image/png", size: pngOnly.maxFileSizeBytes + 1 }, pngOnly)?.asset).toBe(asset);
    expect(rejectedSelection({ uploading: false }, { type: "image/jpeg", size: 10 }, both)).toBeNull();
    expect(uploadingSelection(ready)).toEqual({ asset, uploading: true });
    expect(failedUpload(ready, "图片内容损坏或无法读取")).toEqual({ asset, uploading: false, error: "图片内容损坏或无法读取" });
  });
});
