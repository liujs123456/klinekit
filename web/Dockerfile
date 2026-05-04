# Multi-stage build for the Next.js dashboard.
# Stage 1: install + build (standalone output).
# Stage 2: run with the minimal Node runtime needed by .next/standalone.

FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:22-alpine AS build
WORKDIR /app
ARG NEXT_PUBLIC_KLINEKIT_API=http://localhost:8080/api/v1
ENV NEXT_PUBLIC_KLINEKIT_API=$NEXT_PUBLIC_KLINEKIT_API
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    PORT=3000

# Next.js standalone bundle includes only the deps the runtime needs.
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

EXPOSE 3000
HEALTHCHECK --interval=20s --timeout=5s --start-period=15s --retries=5 \
    CMD wget --quiet --spider http://localhost:3000 || exit 1

CMD ["node", "server.js"]
