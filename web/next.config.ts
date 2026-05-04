import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Enables `.next/standalone` output so the Dockerfile can ship a tiny runtime image.
  output: "standalone",
};

export default nextConfig;
