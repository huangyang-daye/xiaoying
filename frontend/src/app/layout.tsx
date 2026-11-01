import type { Metadata } from "next";
import { AppShell } from "@/components/layout/app-shell";
import "./globals.css";
import { StudioProvider } from "@/components/studio/provider";

export const metadata: Metadata = {
  title: { default: "映作 · 素材制作", template: "%s · 映作" },
  description: "选择灵感模板，上传图片，制作属于你的作品。",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body>
        <StudioProvider><AppShell>{children}</AppShell></StudioProvider>
      </body>
    </html>
  );
}
