import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { LOCALE, t } from "./i18n";
import "./styles.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Reports are derived from data that only changes when the user imports or
      // recategorises, both of which invalidate explicitly. Refetching on every window
      // focus would just make the figures flicker.
      refetchOnWindowFocus: false,
      staleTime: 30_000,
      retry: 1,
    },
  },
});

// The markup ships with Turkish defaults; both follow the resolved locale from here on,
// which matters for screen readers and for the browser tab.
document.documentElement.lang = LOCALE;
document.title = t("app.title");

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
);
