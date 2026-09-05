import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Upload } from "lucide-react";
import { useRef, useState } from "react";
import type { ImportOutcome } from "../api";
import { api } from "../api";
import { t } from "../i18n";
import { Panel, Pill } from "../ui";

/**
 * Statement upload.
 *
 * The interesting part is what happens when it does not simply work: the endpoint answers
 * with one of five statuses, and each needs something different from the user — a password
 * prompt, an offer to use CSV instead, or an explanation that the file is a scan. Collapsing
 * them into "upload failed" would throw away the reason the sealed type exists (SPEC §3.3).
 */
export function UploadPanel() {
  const [file, setFile] = useState<File | null>(null);
  const [password, setPassword] = useState("");
  const [outcome, setOutcome] = useState<ImportOutcome | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const client = useQueryClient();

  const upload = useMutation({
    mutationFn: () => api.upload(file!, password || undefined),
    onSuccess: (result) => {
      setOutcome(result);
      if (result.status === "IMPORTED" || result.status === "PARSED_NOT_STORED") {
        setFile(null);
        setPassword("");
        if (input.current) {
          input.current.value = "";
        }
        void client.invalidateQueries();
      }
    },
  });

  const needsPassword = outcome?.status === "NEEDS_PASSWORD";

  return (
    <Panel title={t("detailed.upload")} icon={<Upload size={16} aria-hidden />} note={t("upload.hint")}>
      <div className="row" style={{ alignItems: "flex-end" }}>
        <label className="field" style={{ flex: 1, minWidth: 200 }}>
          <span>{t("upload.choose")}</span>
          <input
            ref={input}
            type="file"
            accept=".pdf,.csv,application/pdf,text/csv"
            onChange={(event) => {
              setFile(event.target.files?.[0] ?? null);
              setOutcome(null);
            }}
          />
        </label>

        {needsPassword && (
          <label className="field fade-in">
            <span>{t("upload.password")}</span>
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="off"
            />
          </label>
        )}

        <button
          type="button"
          className="button button--primary"
          disabled={!file || upload.isPending}
          onClick={() => upload.mutate()}
        >
          {t("upload.submit")}
        </button>
      </div>

      {outcome && (
        <p className="row fade-in" style={{ marginTop: "var(--space-4)", marginBottom: 0 }}>
          <Outcome outcome={outcome} />
        </p>
      )}
    </Panel>
  );
}

function Outcome({ outcome }: { outcome: ImportOutcome }) {
  switch (outcome.status) {
    case "IMPORTED":
      return (
        <Pill tone="positive">
          {t("upload.imported", {
            count: outcome.transactionsImported,
            duplicates: outcome.suspectedDuplicates,
          })}
        </Pill>
      );
    case "ALREADY_IMPORTED":
      return <Pill>{t("upload.already")}</Pill>;
    case "PARSED_NOT_STORED":
      return (
        <Pill tone="accent">
          {t("upload.notStored", { count: outcome.transactionsImported })}
        </Pill>
      );
    case "NEEDS_PASSWORD":
      return <Pill tone="caution">{t("upload.passwordNeeded")}</Pill>;
    case "UNSUPPORTED_BANK":
      return <Pill tone="caution">{t("upload.unsupported")}</Pill>;
    case "UNREADABLE":
      return <Pill tone="negative">{t("upload.unreadable", { detail: outcome.detail ?? "" })}</Pill>;
  }
}
