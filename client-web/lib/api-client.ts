import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from "axios";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "";

export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE,
  headers: { "Content-Type": "application/json" },
  withCredentials: false,
});

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  let token = null;
  if (typeof window !== "undefined") {
    try {
      // Try gh_auth first (new zustand persist format)
      const ghAuthStr = localStorage.getItem("gh_auth");
      if (ghAuthStr) {
        const ghAuth = JSON.parse(ghAuthStr);
        token = ghAuth?.state?.token || null;
      }
      // Fallback to gh_user (legacy format)
      if (!token) {
        const ghUserStr = localStorage.getItem("gh_user");
        if (ghUserStr) {
          const ghUser = JSON.parse(ghUserStr);
          token = ghUser?.state?.token || null;
        }
      }
    } catch (e) {}
  }
  
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401) {
      if (typeof window !== "undefined") {
        localStorage.removeItem("gh_auth");
        localStorage.removeItem("gh_user");
        localStorage.removeItem("gh_token");
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  }
);
