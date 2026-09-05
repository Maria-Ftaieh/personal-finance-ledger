/*
 * SPEC §0: user-facing strings go through an i18n layer rather than being hardcoded.
 *
 * A typed dictionary and a `t()`, not a framework. The application has one audience and
 * two locales; pulling in i18next to hold sixty keys would be more configuration than
 * content. Turkish is the default because the statements, the categories and the user are.
 *
 * `TranslationKey` is derived from the Turkish dictionary, so a key used in a component
 * but missing from a translation is a compile error rather than a blank label.
 */

const tr = {
  "app.title": "Finans Defteri",
  "app.subtitle": "Harcama ve enflasyon",

  "view.basic": "Genel",
  "view.detailed": "Ayrıntılı",
  "view.switch": "Görünüm",

  "demo.banner":
    "Bu bir tanıtım kopyasıdır. Görünen tüm işlemler kurgusaldır; gerçek finansal veri değildir.",

  "common.loading": "Yükleniyor",
  "common.error": "Bir şeyler ters gitti",
  "common.retry": "Yeniden dene",
  "common.none": "Kayıt yok",
  "common.month": "Ay",
  "common.category": "Kategori",
  "common.subcategory": "Alt kategori",
  "common.amount": "Tutar",
  "common.date": "Tarih",
  "common.description": "Açıklama",
  "common.all": "Tümü",
  "common.cancel": "Vazgeç",
  "common.save": "Kaydet",
  "common.delete": "Sil",
  "common.close": "Kapat",

  "money.nominal": "Nominal",
  "money.real": "Reel",
  "money.baseMonth": "{month} parasıyla",
  "money.notAdjusted": "Enflasyona göre düzeltilmedi",
  "money.notAdjustedWhy":
    "Bu ay için TÜFE henüz yayımlanmadı. Rakam nominaldir; tahmin yürütülmez.",

  "basic.thisMonth": "Bu ay",
  "basic.spentIn": "{month} harcaması",
  "basic.vsLastYear": "Geçen yılın aynı ayı",
  "basic.realChange": "Reel değişim",
  "basic.notComparable": "Karşılaştırma için iki ayın da TÜFE'si gerekiyor",
  "basic.topCategories": "En çok harcanan beş kategori",
  "basic.noSpending": "Bu ay için işlem yok",
  "basic.alerts": "Bütçe uyarıları",
  "basic.noAlerts": "Aşılan bütçe yok",
  "basic.overBy": "{amount} aşıldı",

  "detailed.month": "Dönem",
  "detailed.baseMonth": "Referans ay",
  "detailed.breakdown": "Kategori dağılımı",
  "detailed.trend": "Aylık seyir",
  "detailed.trendNote": "Reel tutarlar, {month} parasıyla",
  "detailed.transactions": "İşlemler",
  "detailed.duplicates": "Yinelenen kayıt kuyruğu",
  "detailed.rules": "Kurallar",
  "detailed.upload": "Ekstre yükle",
  "detailed.share": "Pay",

  "upload.choose": "Dosya seç",
  "upload.hint": "PDF ekstre veya CSV dışa aktarım",
  "upload.password": "PDF parolası",
  "upload.passwordNeeded": "Bu dosya parola korumalı. Parolayı girip yeniden deneyin.",
  "upload.submit": "Yükle",
  "upload.imported": "{count} işlem alındı, {duplicates} olası yineleme işaretlendi",
  "upload.already": "Bu dosya daha önce alınmış; hiçbir şey değişmedi",
  "upload.unsupported": "Bu bankanın ekstresi tanınmadı. CSV dışa aktarımı deneyebilirsiniz.",
  "upload.unreadable": "Dosya okunamadı: {detail}",

  "duplicates.none": "İncelenecek kayıt yok",
  "duplicates.matches": "Eşleşme",
  "duplicates.similarity": "Benzerlik",
  "duplicates.reason.DESCRIPTION_SIMILARITY": "Açıklama benzerliği",
  "duplicates.reason.TRUNCATED_DESCRIPTION": "Kısaltılmış açıklama",
  "duplicates.confirm": "Yineleme",
  "duplicates.reject": "Ayrı işlem",
  "duplicates.explain":
    "Hiçbir kayıt silinmez. Onaylanan yineleme toplamlardan çıkarılır, kaydı durur.",

  "rules.priority": "Öncelik",
  "rules.matchType": "Eşleşme",
  "rules.pattern": "Desen",
  "rules.userDefined": "Kendi kuralım",
  "rules.system": "Hazır",
  "rules.add": "Kural ekle",
  "rules.firstMatchWins": "Öncelik sırasına göre ilk eşleşen kural kazanır.",
  "rules.preview": "Önizle",
  "rules.previewResult": "{changed} işlem yer değiştirir ({held} elle atanmış korunur)",
  "rules.apply": "Uygula",
  "rules.applied": "{changed} işlem yeniden sınıflandırıldı",

  "transactions.override": "Elle atandı",
  "transactions.installment": "Taksit",
  "transactions.original": "Özgün tutar",
  "transactions.filterFrom": "Başlangıç",
  "transactions.filterTo": "Bitiş",
  "transactions.showDuplicates": "Onaylanan yinelemeleri göster",
  "transactions.count": "{count} işlem",

  "cpi.title": "TÜFE",
  "cpi.published": "Son yayımlanan ay",
  "cpi.months": "{count} aylık seri",
  "cpi.refresh": "TCMB'den güncelle",
  "cpi.refreshed": "{added} yeni ay, {revised} revizyon",
  "cpi.offline": "TCMB'ye ulaşılamadı; yerel veriyle çalışılıyor",
} as const;

export type TranslationKey = keyof typeof tr;

const en: Record<TranslationKey, string> = {
  "app.title": "Finance Ledger",
  "app.subtitle": "Spending and inflation",

  "view.basic": "Basic",
  "view.detailed": "Detailed",
  "view.switch": "View",

  "demo.banner":
    "This is a demonstration copy. Every transaction shown is fictional and is not real financial data.",

  "common.loading": "Loading",
  "common.error": "Something went wrong",
  "common.retry": "Try again",
  "common.none": "Nothing here",
  "common.month": "Month",
  "common.category": "Category",
  "common.subcategory": "Subcategory",
  "common.amount": "Amount",
  "common.date": "Date",
  "common.description": "Description",
  "common.all": "All",
  "common.cancel": "Cancel",
  "common.save": "Save",
  "common.delete": "Delete",
  "common.close": "Close",

  "money.nominal": "Nominal",
  "money.real": "Real",
  "money.baseMonth": "in {month} money",
  "money.notAdjusted": "Not adjusted for inflation",
  "money.notAdjustedWhy":
    "CPI for this month has not been published yet. The figure is nominal; nothing is extrapolated.",

  "basic.thisMonth": "This month",
  "basic.spentIn": "Spending in {month}",
  "basic.vsLastYear": "Same month last year",
  "basic.realChange": "Real change",
  "basic.notComparable": "Both months need a published CPI to be compared",
  "basic.topCategories": "Top five categories",
  "basic.noSpending": "No transactions this month",
  "basic.alerts": "Budget alerts",
  "basic.noAlerts": "No budget exceeded",
  "basic.overBy": "over by {amount}",

  "detailed.month": "Period",
  "detailed.baseMonth": "Base month",
  "detailed.breakdown": "Category breakdown",
  "detailed.trend": "Month on month",
  "detailed.trendNote": "Real amounts, in {month} money",
  "detailed.transactions": "Transactions",
  "detailed.duplicates": "Duplicate review",
  "detailed.rules": "Rules",
  "detailed.upload": "Upload a statement",
  "detailed.share": "Share",

  "upload.choose": "Choose a file",
  "upload.hint": "A PDF statement or a CSV export",
  "upload.password": "PDF password",
  "upload.passwordNeeded": "This file is password protected. Enter the password and try again.",
  "upload.submit": "Upload",
  "upload.imported": "{count} transactions imported, {duplicates} flagged as possible duplicates",
  "upload.already": "This file was already imported; nothing changed",
  "upload.unsupported": "No adapter recognised this bank. A CSV export will work.",
  "upload.unreadable": "The file could not be read: {detail}",

  "duplicates.none": "Nothing to review",
  "duplicates.matches": "Matches",
  "duplicates.similarity": "Similarity",
  "duplicates.reason.DESCRIPTION_SIMILARITY": "Description similarity",
  "duplicates.reason.TRUNCATED_DESCRIPTION": "Truncated description",
  "duplicates.confirm": "Duplicate",
  "duplicates.reject": "Separate purchase",
  "duplicates.explain":
    "Nothing is ever deleted. A confirmed duplicate stops counting towards totals and stays on the record.",

  "rules.priority": "Priority",
  "rules.matchType": "Match",
  "rules.pattern": "Pattern",
  "rules.userDefined": "Mine",
  "rules.system": "Seeded",
  "rules.add": "Add a rule",
  "rules.firstMatchWins": "Rules are tried in priority order and the first match wins.",
  "rules.preview": "Preview",
  "rules.previewResult": "{changed} transactions would move ({held} held by a manual assignment)",
  "rules.apply": "Apply",
  "rules.applied": "{changed} transactions recategorised",

  "transactions.override": "Set by hand",
  "transactions.installment": "Instalment",
  "transactions.original": "Original amount",
  "transactions.filterFrom": "From",
  "transactions.filterTo": "To",
  "transactions.showDuplicates": "Show confirmed duplicates",
  "transactions.count": "{count} transactions",

  "cpi.title": "CPI",
  "cpi.published": "Latest published month",
  "cpi.months": "{count} months of index",
  "cpi.refresh": "Refresh from TCMB",
  "cpi.refreshed": "{added} new months, {revised} revised",
  "cpi.offline": "TCMB could not be reached; serving the local cache",
};

const dictionaries = { tr, en } as const;

export type Locale = keyof typeof dictionaries;

export const LOCALE: Locale =
  (import.meta.env.VITE_LOCALE as Locale | undefined) ?? "tr";

/** The locale used for every `Intl` call, so formatting and wording never disagree. */
export const INTL_LOCALE = LOCALE === "tr" ? "tr-TR" : "en-GB";

/** Looks up a string and substitutes `{name}` placeholders. */
export function t(key: TranslationKey, params?: Record<string, string | number>): string {
  const template: string = dictionaries[LOCALE][key];
  if (!params) {
    return template;
  }
  return template.replace(/\{(\w+)\}/g, (whole, name: string) =>
    name in params ? String(params[name]) : whole,
  );
}
