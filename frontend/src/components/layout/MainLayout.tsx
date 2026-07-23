import * as React from "react";

import { Header } from "@/components/layout/Header";
import { Sidebar } from "@/components/layout/Sidebar";

interface MainLayoutProps {
  children: React.ReactNode;
}

export function MainLayout({ children }: MainLayoutProps) {
  const [sidebarOpen, setSidebarOpen] = React.useState(false);

  return (
    <div className="knowledge-shell flex min-h-screen bg-[#f4f2ed]">
      <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <div className="flex min-h-screen min-w-0 flex-1 flex-col lg:py-3 lg:pr-3">
        <div className="flex min-h-0 flex-1 flex-col overflow-hidden bg-[#fbfaf7] lg:rounded-[22px] lg:border lg:border-white/80 lg:shadow-[0_24px_70px_-38px_rgba(15,23,42,0.42)]">
          <Header onToggleSidebar={() => setSidebarOpen((prev) => !prev)} />
          <main className="min-h-0 flex-1 overflow-hidden">{children}</main>
        </div>
      </div>
    </div>
  );
}
