import type { NextConfig } from "next";

const backendUrl = new URL(
  process.env.JAVA_INTERNAL_URL ?? "http://localhost:8080",
);

if (
  !["http:", "https:"].includes(backendUrl.protocol) ||
  backendUrl.username ||
  backendUrl.password ||
  backendUrl.pathname !== "/" ||
  backendUrl.search ||
  backendUrl.hash
) {
  throw new Error("JAVA_INTERNAL_URL must be an HTTP(S) origin without credentials or a path.");
}

const nextConfig: NextConfig = {
  poweredByHeader: false,
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backendUrl.origin}/api/:path*` },
      { source: "/files/:path*", destination: `${backendUrl.origin}/files/:path*` },
    ];
  },
};

export default nextConfig;
