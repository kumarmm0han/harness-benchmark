# TASK-006: static nginx frontend that proxies /api to the backend (DES-017).
# Replaced in TASK-007 by a multi-stage Vite node build → nginx serve.
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY html/ /usr/share/nginx/html/
EXPOSE 80
