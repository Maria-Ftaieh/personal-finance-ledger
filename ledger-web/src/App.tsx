import { useQuery } from "@tanstack/react-query";
import { Info, LayoutGrid, Table2 } from "lucide-react";
import { useEffect, useState } from "react";
import { api } from "./api";
import { t } from "./i18n";
import { Segmented } from "./ui";
import { BasicView } from "./views/BasicView";
import { DetailedView } from "./views/DetailedView";

type View = "basic" | "detailed";

const VIEW_STORAGE_KEY = "ledger.view";

/**
 * SPEC §8.1: the toggle persists per user and is a single obvious control.
 *
 * This deployment is single user, so the browser is the per-user store. When authentication
 * arrives the same call moves to a preferences endpoint; nothing else here changes.
 */
function useStoredView(): [View, (view: View) => void] {
  const [view, setView] = useState<View>(() => {
    const stored = localStorage.getItem(VIEW_STORAGE_KEY);
    return stored === "detailed" ? "detailed" : "basic";
  });

  useEffect(() => {
    localStorage.setItem(VIEW_STORAGE_KEY, view);
  }, [view]);

  return [view, setView];
}

export default function App() {
  const [view, setView] = useStoredView();

  // Both views are anchored to the newest month that actually has transactions, so the
  // application opens on something rather than on an empty current month.
  const months = useQuery({ queryKey: ["months"], queryFn: api.monthsWithSpending });
  const latestMonth = months.data?.[0];

  const demo = import.meta.env.VITE_DEMO_MODE === "true";

  return (
    <div className="shell">
      <header className="topbar">
        <div className="topbar__title">
          <div className="stack" style={{ gap: 0 }}>
            <h1>{t("app.title")}</h1>
            <span className="small muted">{t("app.subtitle")}</span>
          </div>
        </div>
        <Segmented
          label={t("view.switch")}
          value={view}
          onChange={setView}
          options={[
            { value: "basic", label: t("view.basic"), icon: <LayoutGrid size={14} aria-hidden /> },
            { value: "detailed", label: t("view.detailed"), icon: <Table2 size={14} aria-hidden /> },
          ]}
        />
      </header>

      {/* SPEC §8.3: say plainly, on screen, that the data is fictional. */}
      {demo && (
        <p className="banner" style={{ marginBottom: "var(--space-5)" }}>
          <Info size={16} aria-hidden style={{ flexShrink: 0, marginTop: 1 }} />
          {t("demo.banner")}
        </p>
      )}

      <main className="fade-in" key={view}>
        {view === "basic" ? (
          <BasicView month={latestMonth} loading={months.isLoading} />
        ) : (
          <DetailedView months={months.data ?? []} loading={months.isLoading} />
        )}
      </main>
    </div>
  );
}
