# Frontend (DES-014/017): build the Vite/React app, then serve the static bundle via
# nginx with the same-origin /api proxy to the backend. Mirrors a production build
# (NFR-001) and keeps the image free of dev tooling.
FROM node:22-alpine AS build
WORKDIR /app
# Install dependencies from the committed lockfile exactly, before copying sources,
# so dependency layers are cacheable.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# Type-check + production bundle (bundle lands in dist/).
RUN npm run typecheck && npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html/
EXPOSE 80
