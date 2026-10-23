import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { request } from "./workspaceApi";

// Keep detail navigation in the URL while leaving each page's filters and
// pagination mounted. Forms remain local; opening one closes the detail link.
export function useRecordDialog(endpoint, recordKey) {
  const [searchParams, setSearchParams] = useSearchParams();
  const recordId = searchParams.get("record");
  const [localDialog, setLocalDialog] = useState(null);
  const [detailError, setDetailError] = useState("");
  const activeRequest = useRef(null);

  useEffect(() => {
    if (!recordId) return;
    const controller = new AbortController();
    activeRequest.current = controller;
    request(`${endpoint}/${encodeURIComponent(recordId)}`, { signal: controller.signal })
      .then((data) => {
        if (controller.signal.aborted) return;
        const record = data?.[recordKey] ?? data;
        if (!record || String(record.id) !== recordId) {
          throw new Error("The requested record could not be found.");
        }
        setDetailError("");
        setLocalDialog({ mode: "detail", [recordKey]: record });
      })
      .catch((failure) => {
        if (controller.signal.aborted || failure.name === "AbortError") return;
        setLocalDialog(null);
        setDetailError(`Could not open this record. ${failure.message}`);
        setSearchParams((current) => {
          const next = new URLSearchParams(current);
          if (next.get("record") === recordId) next.delete("record");
          return next;
        }, { replace: true });
      });
    return () => controller.abort();
  }, [endpoint, recordId, recordKey, setSearchParams]);

  const dialog = localDialog?.mode === "detail"
    ? String(localDialog[recordKey]?.id) === recordId ? localDialog : null
    : recordId ? null : localDialog;

  function setDialog(nextDialog) {
    activeRequest.current?.abort();
    setDetailError("");
    const nextId = nextDialog?.mode === "detail" ? String(nextDialog[recordKey].id) : null;
    // Fetch a newly opened detail by ID, even when it is outside the visible
    // list page. Updates to the already-open detail retain its current data.
    setLocalDialog(nextId && nextId !== recordId ? null : nextDialog);
    if (nextId !== recordId) {
      setSearchParams((current) => {
        const next = new URLSearchParams(current);
        if (nextId) next.set("record", nextId);
        else next.delete("record");
        return next;
      });
    }
  }

  function updateRecord(record) {
    setLocalDialog((current) => current?.mode === "detail" && current[recordKey]?.id === record.id
      ? { mode: "detail", [recordKey]: record } : current);
  }

  return { dialog, setDialog, updateRecord, detailError, detailLoading: Boolean(recordId) && !dialog };
}
