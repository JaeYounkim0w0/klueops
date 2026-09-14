# Compile Vue source with the lockfile-pinned Node toolchain.
FROM node:22-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Serve only static build output through an unprivileged nginx runtime.
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=5s --start-period=10s --retries=5 CMD wget --quiet --tries=1 --spider http://127.0.0.1:8080/healthz || exit 1
