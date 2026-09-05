import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CopyCheck } from "lucide-react";
import { api } from "../api";
import { bankLabel, formatDate } from "../format";
import { t } from "../i18n";
import { Empty, ErrorState, Loading, MoneyText, Panel, Pill } from "../ui";

/**
 * SPEC §3.4: the review queue. The pass flags, the user decides, and nothing is deleted
 * either way — which is the whole reason this is a screen rather than a silent merge.
 */
export function DuplicateQueue() {
  const queue = useQuery({ queryKey: ["duplicates"], queryFn: api.duplicates });
  const client = useQueryClient();

  const settle = () => {
    void client.invalidateQueries({ queryKey: ["duplicates"] });
    void client.invalidateQueries({ queryKey: ["transactions"] });
    void client.invalidateQueries({ queryKey: ["monthly"] });
    void client.invalidateQueries({ queryKey: ["yoy"] });
    void client.invalidateQueries({ queryKey: ["alerts"] });
  };

  const confirm = useMutation({ mutationFn: api.confirmDuplicate, onSuccess: settle });
  const reject = useMutation({ mutationFn: api.rejectDuplicate, onSuccess: settle });

  const rows = queue.data ?? [];

  return (
    <Panel
      title={t("detailed.duplicates")}
      icon={<CopyCheck size={16} aria-hidden />}
      note={t("duplicates.explain")}
      action={rows.length > 0 ? <Pill tone="caution">{rows.length}</Pill> : undefined}
      flush
    >
      {queue.isLoading ? (
        <Loading />
      ) : queue.isError ? (
        <ErrorState error={queue.error} onRetry={() => void queue.refetch()} />
      ) : rows.length === 0 ? (
        <Empty>{t("duplicates.none")}</Empty>
      ) : (
        <div className="table__scroll">
          <table className="table">
            <thead>
              <tr>
                <th>{t("common.date")}</th>
                <th>{t("common.description")}</th>
                <th>{t("duplicates.similarity")}</th>
                <th className="align-right">{t("common.amount")}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((transaction) => (
                <tr key={transaction.id}>
                  <td className="num tertiary" style={{ whiteSpace: "nowrap" }}>
                    {formatDate(transaction.transactionDate)}
                  </td>
                  <td>
                    <div className="stack" style={{ gap: 3 }}>
                      <span>{transaction.rawDescription}</span>
                      <span className="small tertiary">{bankLabel(transaction.bank)}</span>
                    </div>
                  </td>
                  <td>
                    <div className="stack" style={{ gap: 3 }}>
                      <span className="num">
                        {transaction.duplicateSimilarity !== undefined
                          ? `${Math.round(transaction.duplicateSimilarity * 100)}%`
                          : ""}
                      </span>
                      {transaction.duplicateReason && (
                        <span className="small tertiary">
                          {t(`duplicates.reason.${transaction.duplicateReason}`)}
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="align-right">
                    <MoneyText money={transaction.amount} />
                  </td>
                  <td>
                    <div className="row" style={{ gap: 6, flexWrap: "nowrap", justifyContent: "flex-end" }}>
                      <button
                        type="button"
                        className="button button--small"
                        disabled={confirm.isPending}
                        onClick={() => confirm.mutate(transaction.id)}
                      >
                        {t("duplicates.confirm")}
                      </button>
                      <button
                        type="button"
                        className="button button--quiet button--small"
                        disabled={reject.isPending}
                        onClick={() => reject.mutate(transaction.id)}
                      >
                        {t("duplicates.reject")}
                      </button>
                    </div>
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
