import type { Asset, Template } from "./api";

const mimeLabel: Record<string, string> = { "image/jpeg": "JPEG", "image/png": "PNG" };
export type Slot = { asset?: Asset; uploading: boolean; error?: string };
type Limits = Pick<Template, "requiredImageCount" | "acceptedMimeTypes" | "maxFileSizeBytes">;

export function typeNames(types: string[]) { return types.map(type => mimeLabel[type] ?? type); }
export function typePhrase(types: string[]) {
  const names = typeNames(types);
  if (names.length <= 1) return names[0] ?? "指定格式";
  return `${names.slice(0, -1).join("、")} 或 ${names[names.length - 1]}`;
}
export function materialRequirement(template: Limits) {
  const names = typeNames(template.acceptedMimeTypes);
  return `需要 ${template.requiredImageCount} 张 · ${names.join(" / ") || "指定格式"} · 每张不超过 ${template.maxFileSizeBytes / 1048576} MiB`;
}
export function rejectedSelection(slot: Slot, file: { type: string; size: number }, template: Pick<Template, "acceptedMimeTypes" | "maxFileSizeBytes">): Slot | null {
  if (!template.acceptedMimeTypes.includes(file.type)) return { asset: slot.asset, uploading: false, error: `请选择 ${typePhrase(template.acceptedMimeTypes)} 图片。` };
  if (file.size === 0 || file.size > template.maxFileSizeBytes) return { asset: slot.asset, uploading: false, error: `图片须大于 0，且不超过 ${template.maxFileSizeBytes / 1048576} MiB。` };
  return null;
}
export function uploadingSelection(slot: Slot): Slot { return { asset: slot.asset, uploading: true }; }
export function failedUpload(slot: Slot, message: string): Slot { return { asset: slot.asset, uploading: false, error: message }; }
