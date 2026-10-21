ARG IMAGE_REGISTRY=docker.io

FROM ${IMAGE_REGISTRY}/library/node:22-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./

RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

COPY . .

RUN npm run build

FROM ${IMAGE_REGISTRY}/library/nginx:stable-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=10 \
    CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
