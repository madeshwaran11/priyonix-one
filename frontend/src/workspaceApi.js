import { useCallback, useEffect, useState } from "react";
import { apiFetch, ApiError } from "./api";

export function humanize(value) {
  return String(value || "").replace(/([a-z])([A-Z])/g, "$1 $2").replace(/[_-]/g, " ").toLowerCase().replace(/^./, (letter) => letter.toUpperCase());
}

export async function request(path, { payload, ...options } = {}) {
  const response = await apiFetch(path, {
    ...options,
    ...(payload === undefined ? {} : {
      headers: { "Content-Type": "application/json", ...options.headers },
      body: JSON.stringify(payload),
    }),
  });
  const data = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(data?.message || `Request failed (${response.status}). Please try again.`, {
      status: response.status, fieldErrors: data?.errors,
    });
  }
  return data;
}

export function useRemote(path) {
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState(null);
  const key = `${path}:${revision}`;
  const resource = path ? String(path).split("?")[0] : null;
  useEffect(() => {
    if (!path) return;
    const controller = new AbortController();
    request(path, { signal: controller.signal })
      .then((data) => setResult({ key, resource, data, error: null }))
      .catch((error) => {
        if (error.name === "AbortError") return;
        setResult((previous) => ({
          key,
          resource,
          data: previous?.resource === resource ? previous.data : null,
          error,
        }));
      });
    return () => controller.abort();
  }, [path, key, resource]);
  const reload = useCallback(() => setRevision((value) => value + 1), []);
  const sameResource = result?.resource === resource;
  const hasData = sameResource && result?.data != null;
  const pending = Boolean(path) && result?.key !== key;
  return {
    data: sameResource ? result?.data ?? null : null,
    error: sameResource ? result?.error ?? null : null,
    loading: pending && !hasData,
    refreshing: pending && hasData,
    reload,
  };
}

export function queryString(values) {
  return new URLSearchParams(Object.entries(values).filter(([, value]) => value !== "" && value != null)).toString();
}

export function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function exportRows(rows, columns, filename) {
  const escape = (value) => {
    let text = String(value ?? "");
    if (/^[=+@\-\t\r]/.test(text)) text = `'${text}`;
    return `"${text.replaceAll('"', '""')}"`;
  };
  const csv = [columns.map((column) => escape(column.label)).join(","),
    ...rows.map((row) => columns.map((column) => escape(column.value(row))).join(","))].join("\r\n");
  saveBlob(new Blob(["\ufeff", csv], { type: "text/csv;charset=utf-8;" }), filename);
}
