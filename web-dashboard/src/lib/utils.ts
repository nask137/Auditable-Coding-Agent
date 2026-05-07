import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatDate(value?: string | null) {
  if (!value) return "无";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  }).format(date);
}

export function compactId(value?: string | null) {
  if (!value) return "无";
  return value.length > 12 ? `${value.slice(0, 8)}...${value.slice(-4)}` : value;
}

export function asText(value: unknown) {
  if (value == null || value === "") return "无";
  if (typeof value === "string") return value;
  return JSON.stringify(value, null, 2);
}

export function statusTone(status?: string | null) {
  const normalized = (status ?? "").toUpperCase();
  if (["SUCCESS", "COMPLETED", "FINISHED", "APPROVED", "CONSUMED"].includes(normalized)) return "success";
  if (["RUNNING", "IN_PROGRESS", "WAITING_APPROVAL", "WAITING_USER_INPUT", "PENDING"].includes(normalized)) return "warning";
  if (["FAILURE", "FAILED", "DENIED", "BLOCKED", "CANCELLED", "REJECTED"].includes(normalized)) return "danger";
  return "neutral";
}
