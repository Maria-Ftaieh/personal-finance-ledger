import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, SlidersHorizontal, Trash2 } from "lucide-react";
import { useState } from "react";
import type { Category, MatchType } from "../api";
import { api, ApiFailure } from "../api";
import { READ_ONLY } from "../demo";
import { categoryLabel, t } from "../i18n";
import { Empty, ErrorState, Loading, Panel, Pill } from "../ui";

const MATCH_TYPES: MatchType[] = ["CONTAINS", "STARTS_WITH", "EXACT", "REGEX"];

/**
 * SPEC §5.3: rule management, with the preview before a bulk recategorisation.
 *
 * Rules are listed in evaluation order and the priority is editable, because the one thing
 * a user actually wants from this screen is to put their own rule above a seeded one.
 */
export function RulesPanel({ categories }: { categories: Category[] }) {
  const rules = useQuery({ queryKey: ["rules"], queryFn: api.rules });
  const client = useQueryClient();

  const [adding, setAdding] = useState(false);
  const [priority, setPriority] = useState(1);
  const [matchType, setMatchType] = useState<MatchType>("CONTAINS");
  const [pattern, setPattern] = useState("");
  const [categoryId, setCategoryId] = useState(categories[0]?.id ?? "");
  const [problem, setProblem] = useState<string | null>(null);

  const refresh = () => {
    void client.invalidateQueries({ queryKey: ["rules"] });
    void client.invalidateQueries({ queryKey: ["rulePreview"] });
  };

  const create = useMutation({
    mutationFn: () => api.createRule({ priority, matchType, pattern, categoryId }),
    onSuccess: () => {
      setAdding(false);
      setPattern("");
      setProblem(null);
      refresh();
    },
    // A pattern refused by RegexGuard comes back as a 400 with a usable message; showing it
    // is the whole point of validating on save rather than at match time.
    onError: (error) => setProblem(error instanceof ApiFailure ? error.message : String(error)),
  });

  const remove = useMutation({ mutationFn: api.deleteRule, onSuccess: refresh });

  const preview = useQuery({
    queryKey: ["rulePreview"],
    queryFn: api.rulePreview,
    enabled: false,
  });

  const apply = useMutation({
    mutationFn: api.reevaluateRules,
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ["transactions"] });
      void client.invalidateQueries({ queryKey: ["monthly"] });
      void client.invalidateQueries({ queryKey: ["yoy"] });
      void client.invalidateQueries({ queryKey: ["alerts"] });
      void preview.refetch();
    },
  });

  const nameOf = (id: string) =>
    categoryLabel(id, categories.find((category) => category.id === id)?.displayName);
  const list = rules.data ?? [];

  return (
    <Panel
      title={t("detailed.rules")}
      icon={<SlidersHorizontal size={16} aria-hidden />}
      note={t("rules.firstMatchWins")}
      action={
        <div className="row" style={{ gap: 6 }}>
          <button
            type="button"
            className="button button--small"
            onClick={() => void preview.refetch()}
            disabled={preview.isFetching}
          >
            {t("rules.preview")}
          </button>
          <button
            type="button"
            className="button button--small button--primary"
            onClick={() => setAdding((open) => !open)}
            disabled={READ_ONLY}
            title={READ_ONLY ? t("demo.readOnly") : undefined}
          >
            <Plus size={13} aria-hidden /> {t("rules.add")}
          </button>
        </div>
      }
      flush
    >
      {/* SPEC §5.3: how many rows would move, before anything moves. */}
      {preview.data && (
        <div
          className="row row--between fade-in"
          style={{ padding: "0 var(--space-5) var(--space-4)" }}
        >
          <span className="small muted">
            {t("rules.previewResult", {
              changed: preview.data.wouldChange,
              held: preview.data.heldByOverride,
            })}
          </span>
          <button
            type="button"
            className="button button--small"
            disabled={READ_ONLY || preview.data.wouldChange === 0 || apply.isPending}
            title={READ_ONLY ? t("demo.readOnly") : undefined}
            onClick={() => apply.mutate()}
          >
            {t("rules.apply")}
          </button>
        </div>
      )}

      {apply.data && (
        <p className="small muted fade-in" style={{ padding: "0 var(--space-5) var(--space-4)" }}>
          {t("rules.applied", { changed: apply.data.changed })}
        </p>
      )}

      {adding && (
        <div
          className="row fade-in"
          style={{ padding: "0 var(--space-5) var(--space-4)", alignItems: "flex-end" }}
        >
          <label className="field" style={{ width: 90 }}>
            <span>{t("rules.priority")}</span>
            <input
              type="number"
              value={priority}
              min={1}
              onChange={(event) => setPriority(Number(event.target.value))}
            />
          </label>
          <label className="field">
            <span>{t("rules.matchType")}</span>
            <select
              value={matchType}
              onChange={(event) => setMatchType(event.target.value as MatchType)}
            >
              {MATCH_TYPES.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
          </label>
          <label className="field" style={{ flex: 1, minWidth: 160 }}>
            <span>{t("rules.pattern")}</span>
            <input value={pattern} onChange={(event) => setPattern(event.target.value)} />
          </label>
          <label className="field">
            <span>{t("common.category")}</span>
            <select value={categoryId} onChange={(event) => setCategoryId(event.target.value)}>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {categoryLabel(category.id, category.displayName)}
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            className="button button--primary"
            disabled={READ_ONLY || !pattern.trim() || create.isPending}
            onClick={() => create.mutate()}
          >
            {t("common.save")}
          </button>
        </div>
      )}

      {problem && (
        <p className="small negative fade-in" style={{ padding: "0 var(--space-5) var(--space-4)" }}>
          {problem}
        </p>
      )}

      {rules.isLoading ? (
        <Loading />
      ) : rules.isError ? (
        <ErrorState error={rules.error} onRetry={() => void rules.refetch()} />
      ) : list.length === 0 ? (
        <Empty>{t("common.none")}</Empty>
      ) : (
        <div className="table__scroll" style={{ maxHeight: 420, overflowY: "auto" }}>
          <table className="table">
            <thead>
              <tr>
                <th>{t("rules.priority")}</th>
                <th>{t("rules.matchType")}</th>
                <th>{t("rules.pattern")}</th>
                <th>{t("common.category")}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {list.map((rule) => (
                <tr key={rule.id}>
                  <td className="num tertiary">{rule.priority}</td>
                  <td className="small tertiary">{rule.matchType}</td>
                  <td>
                    <span className="row" style={{ gap: 6 }}>
                      {rule.pattern}
                      {rule.userDefined && <Pill tone="accent">{t("rules.userDefined")}</Pill>}
                    </span>
                  </td>
                  <td>{nameOf(rule.categoryId)}</td>
                  <td className="align-right">
                    {rule.userDefined && (
                      <button
                        type="button"
                        className="button button--danger button--small"
                        onClick={() => remove.mutate(rule.id)}
                        disabled={READ_ONLY}
                        title={READ_ONLY ? t("demo.readOnly") : undefined}
                        aria-label={t("common.delete")}
                      >
                        <Trash2 size={13} aria-hidden />
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  );
}
