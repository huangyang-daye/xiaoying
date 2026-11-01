FROM node:24.22.1-alpine AS build
WORKDIR /app
COPY package*.json .npmrc ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-fund
COPY . .
ARG JAVA_INTERNAL_URL=http://backend:8080
ENV JAVA_INTERNAL_URL=$JAVA_INTERNAL_URL NEXT_TELEMETRY_DISABLED=1
RUN npm run build && npm prune --omit=dev --ignore-scripts

FROM node:24.22.1-alpine
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1
COPY --from=build --chown=node:node /app/package.json ./package.json
COPY --from=build --chown=node:node /app/node_modules ./node_modules
COPY --from=build --chown=node:node /app/.next ./.next
COPY --from=build --chown=node:node /app/next.config.ts ./next.config.ts
USER node
EXPOSE 3000
CMD ["npm", "start", "--", "--hostname", "0.0.0.0"]
