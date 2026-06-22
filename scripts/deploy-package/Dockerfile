FROM node:20-alpine

WORKDIR /app

ENV NODE_ENV=production
ENV HOSTNAME=0.0.0.0
ENV PORT=3000

COPY . .

EXPOSE 3000

CMD ["node", "server.js"]