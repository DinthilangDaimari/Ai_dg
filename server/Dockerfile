FROM node:22-alpine

WORKDIR /app

COPY package.json ./
COPY server.js telephony.js ./

ENV NODE_ENV=production

CMD ["node", "server.js"]
