import React from "react";
import ReactDOM from "react-dom/client";

import App from "@/App";
import { useAuthStore } from "@/stores/authStore";
import { useThemeStore } from "@/stores/themeStore";
import "@/styles/globals.css";
import "@/styles/fan-lounge.css";

useThemeStore.getState().initialize();
useAuthStore.getState().checkAuth();

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
