# Build the frontend, then serve the static output behind nginx. The runtime image carries
# neither Node nor a package manager.
FROM node:22-alpine AS build
WORKDIR /build
COPY ledger-web/package.json ledger-web/package-lock.json ./
RUN npm ci
COPY ledger-web/ ./
# SPEC §8.3: the deployed demo runs on generated data and says so on screen.
ARG VITE_DEMO_MODE=true
ENV VITE_DEMO_MODE=$VITE_DEMO_MODE
RUN npm run build

FROM nginx:1.27-alpine
COPY ledger-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
