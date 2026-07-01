FROM node:22-alpine AS deps
WORKDIR /app
COPY frontend/package*.json ./
RUN npm install

FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY frontend .
ARG NEXT_PUBLIC_CATALOG_API
ARG NEXT_PUBLIC_INVENTORY_API
ARG NEXT_PUBLIC_POS_API
ARG NEXT_PUBLIC_REPORT_API
ENV NEXT_PUBLIC_CATALOG_API=${NEXT_PUBLIC_CATALOG_API}
ENV NEXT_PUBLIC_INVENTORY_API=${NEXT_PUBLIC_INVENTORY_API}
ENV NEXT_PUBLIC_POS_API=${NEXT_PUBLIC_POS_API}
ENV NEXT_PUBLIC_REPORT_API=${NEXT_PUBLIC_REPORT_API}
RUN npm run build

FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public
EXPOSE 3000
CMD ["node", "server.js"]
