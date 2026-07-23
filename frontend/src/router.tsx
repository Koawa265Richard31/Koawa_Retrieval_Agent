import * as React from "react";
import { Navigate, createBrowserRouter } from "react-router-dom";

import { useAuthStore } from "@/stores/authStore";

const LoginPage = React.lazy(() =>
  import("@/pages/LoginPage").then((module) => ({ default: module.LoginPage }))
);
const RegisterPage = React.lazy(() =>
  import("@/pages/LoginPage").then((module) => ({ default: module.RegisterPage }))
);
const ChatPage = React.lazy(() =>
  import("@/pages/ChatPage").then((module) => ({ default: module.ChatPage }))
);
const NotFoundPage = React.lazy(() =>
  import("@/pages/NotFoundPage").then((module) => ({ default: module.NotFoundPage }))
);
const AdminLayout = React.lazy(() =>
  import("@/pages/admin/AdminLayout").then((module) => ({ default: module.AdminLayout }))
);
const DashboardPage = React.lazy(() =>
  import("@/pages/admin/dashboard/DashboardPage").then((module) => ({
    default: module.DashboardPage
  }))
);
const KnowledgeListPage = React.lazy(() =>
  import("@/pages/admin/knowledge/KnowledgeListPage").then((module) => ({
    default: module.KnowledgeListPage
  }))
);
const KnowledgeDocumentsPage = React.lazy(() =>
  import("@/pages/admin/knowledge/KnowledgeDocumentsPage").then((module) => ({
    default: module.KnowledgeDocumentsPage
  }))
);
const KnowledgeChunksPage = React.lazy(() =>
  import("@/pages/admin/knowledge/KnowledgeChunksPage").then((module) => ({
    default: module.KnowledgeChunksPage
  }))
);
const IntentTreePage = React.lazy(() =>
  import("@/pages/admin/intent-tree/IntentTreePage").then((module) => ({
    default: module.IntentTreePage
  }))
);
const IntentListPage = React.lazy(() =>
  import("@/pages/admin/intent-tree/IntentListPage").then((module) => ({
    default: module.IntentListPage
  }))
);
const IntentEditPage = React.lazy(() =>
  import("@/pages/admin/intent-tree/IntentEditPage").then((module) => ({
    default: module.IntentEditPage
  }))
);
const IngestionPage = React.lazy(() =>
  import("@/pages/admin/ingestion/IngestionPage").then((module) => ({
    default: module.IngestionPage
  }))
);
const RagTracePage = React.lazy(() =>
  import("@/pages/admin/traces/RagTracePage").then((module) => ({
    default: module.RagTracePage
  }))
);
const RagTraceDetailPage = React.lazy(() =>
  import("@/pages/admin/traces/RagTraceDetailPage").then((module) => ({
    default: module.RagTraceDetailPage
  }))
);
const SystemSettingsPage = React.lazy(() =>
  import("@/pages/admin/settings/SystemSettingsPage").then((module) => ({
    default: module.SystemSettingsPage
  }))
);
const SampleQuestionPage = React.lazy(() =>
  import("@/pages/admin/sample-questions/SampleQuestionPage").then((module) => ({
    default: module.SampleQuestionPage
  }))
);
const QueryTermMappingPage = React.lazy(() =>
  import("@/pages/admin/query-term-mapping/QueryTermMappingPage").then((module) => ({
    default: module.QueryTermMappingPage
  }))
);
const UserListPage = React.lazy(() =>
  import("@/pages/admin/users/UserListPage").then((module) => ({
    default: module.UserListPage
  }))
);

function lazyPage(element: React.ReactNode) {
  return (
    <React.Suspense
      fallback={
        <div className="flex min-h-screen items-center justify-center bg-[#f5f2eb] text-sm text-slate-500">
          正在加载工作台…
        </div>
      }
    >
      {element}
    </React.Suspense>
  );
}

function RequireAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

function RequireAdmin({ children }: { children: JSX.Element }) {
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (user?.role !== "admin") {
    return <Navigate to="/chat" replace />;
  }

  return children;
}

function RedirectIfAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (isAuthenticated) {
    return <Navigate to="/chat" replace />;
  }
  return children;
}

function HomeRedirect() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  return <Navigate to={isAuthenticated ? "/chat" : "/login"} replace />;
}

export const router = createBrowserRouter([
  {
    path: "/",
    element: <HomeRedirect />
  },
  {
    path: "/login",
    element: <RedirectIfAuth>{lazyPage(<LoginPage />)}</RedirectIfAuth>
  },
  {
    path: "/register",
    element: <RedirectIfAuth>{lazyPage(<RegisterPage />)}</RedirectIfAuth>
  },
  {
    path: "/chat",
    element: <RequireAuth>{lazyPage(<ChatPage />)}</RequireAuth>
  },
  {
    path: "/chat/:sessionId",
    element: <RequireAuth>{lazyPage(<ChatPage />)}</RequireAuth>
  },
  {
    path: "/admin",
    element: <RequireAdmin>{lazyPage(<AdminLayout />)}</RequireAdmin>,
    children: [
      {
        index: true,
        element: <Navigate to="/admin/dashboard" replace />
      },
      {
        path: "dashboard",
        element: lazyPage(<DashboardPage />)
      },
      {
        path: "knowledge",
        element: lazyPage(<KnowledgeListPage />)
      },
      {
        path: "knowledge/:kbId",
        element: lazyPage(<KnowledgeDocumentsPage />)
      },
      {
        path: "knowledge/:kbId/docs/:docId",
        element: lazyPage(<KnowledgeChunksPage />)
      },
      {
        path: "intent-tree",
        element: lazyPage(<IntentTreePage />)
      },
      {
        path: "intent-list",
        element: lazyPage(<IntentListPage />)
      },
      {
        path: "intent-list/:id/edit",
        element: lazyPage(<IntentEditPage />)
      },
      {
        path: "ingestion",
        element: lazyPage(<IngestionPage />)
      },
      {
        path: "traces",
        element: lazyPage(<RagTracePage />)
      },
      {
        path: "traces/:traceId",
        element: lazyPage(<RagTraceDetailPage />)
      },
      {
        path: "settings",
        element: lazyPage(<SystemSettingsPage />)
      },
      {
        path: "sample-questions",
        element: lazyPage(<SampleQuestionPage />)
      },
      {
        path: "mappings",
        element: lazyPage(<QueryTermMappingPage />)
      },
      {
        path: "users",
        element: lazyPage(<UserListPage />)
      }
    ]
  },
  {
    path: "*",
    element: lazyPage(<NotFoundPage />)
  }
]);
