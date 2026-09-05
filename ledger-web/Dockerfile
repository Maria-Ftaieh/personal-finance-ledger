# Build the frontend, then serve the static output behind nginx. The runtime image carries
# neither Node nor a package manager.
FROM node:22-alpine AS build
WORKDIR /build
COPY ledger-web/package.json ledger-web/package-lock.json ./
RUN npm ci
COPY ledger-web/ ./
# The deployed demo runs on generated data and says so on screen.
ARG VITE_DEMO_MODE=true
ENV VITE_DEMO_MODE=$VITE_DEMO_MODE
# English by default here: most people who open the public demo do not read Turkish.
# A self-hosted build wants tr, which is what the source default is — set
# VITE_LOCALE=tr to get it. Either way the reader can switch in the header.
ARG VITE_LOCALE=en
ENV VITE_LOCALE=$VITE_LOCALE
RUN npm run build

FROM nginx:1.27-alpine
COPY ledger-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
