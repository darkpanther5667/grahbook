export const API_URL =
  process.env.NEXT_PUBLIC_API_URL || "https://wpapp-xz9l.onrender.com";
  // ⚠️ Update fallback to real API URL after deploying the API service.

export const CURRENCY = "₹";
export const DATE_FORMAT = "dd MMM yyyy";
export const DATE_TIME_FORMAT = "dd MMM yyyy, hh:mm a";

export const PAYMENT_MODES = [
  { value: "cash", label: "Cash", icon: "Banknote" },
  { value: "upi", label: "UPI", icon: "Smartphone" },
  { value: "qr", label: "QR Code", icon: "QrCode" },
  { value: "cheque", label: "Cheque", icon: "FileText" },
] as const;

export const BILL_STATUS_COLORS: Record<string, string> = {
  paid: "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400",
  unpaid:
    "bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400",
  overdue:
    "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400",
  partial:
    "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400",
};

export const APP_NAME = "Grahbook";
