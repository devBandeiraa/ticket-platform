# Imagem do frontend: compila com Node e serve o resultado com nginx.
#
# O que vai para producao e HTML, CSS e JS estaticos — nao ha servidor a rodar. Manter o Node
# na imagem final carregaria centenas de megabytes e uma superficie de ataque inteira para
# entregar arquivos que o nginx entrega melhor.

FROM node:24-alpine AS compilacao

WORKDIR /app

# package.json antes do resto do codigo: a instalacao das dependencias so refaz quando elas
# mudam, e nao a cada linha alterada num componente.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# O Vite embute esta variavel no bundle na hora da compilacao — nao ha como troca-la depois
# sem recompilar. E o endereco que o NAVEGADOR usa, e nao um nome interno da rede do compose:
# quem faz a chamada e a maquina de quem acessa, que nao enxerga `api-gateway`.
ARG VITE_API_URL=http://localhost:8080/api
ENV VITE_API_URL=$VITE_API_URL

RUN npm run build

FROM nginx:1.27-alpine AS execucao

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=compilacao /app/dist /usr/share/nginx/html

EXPOSE 80
