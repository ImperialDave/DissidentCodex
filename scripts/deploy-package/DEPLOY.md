# Codex Web — Deployment Package (`codex-web-deploy`)

This folder is a **production-ready** build of the Codex website (not the source code in `codex-web/`). Upload/copy the entire contents to your hosting service.

## What's inside

| Item | Purpose |
|------|---------|
| `server.js` | Next.js production server (Node 18+) |
| `node_modules/` | Runtime dependencies only |
| `.next/` | Built app + static assets |
| `public/` | Public files (icons, etc.) |
| `.env.production.example` | Environment variables template |
| `package.json` | Start scripts |
| `start.sh` | Quick start script |
| `Dockerfile` | Container deploy (Render, Fly.io, Railway, Cloud Run) |
| `firebase.json` | Optional Firebase Hosting config |

## Before you deploy

`NEXT_PUBLIC_*` Firebase variables are **baked in at build time**. If you change Firebase config, rebuild from the project root:

```bash
cd ~/AndroidStudioProjects/Codex
bash scripts/build-codex-web-deploy.sh
```

## Option A — Render / Railway / Fly.io (recommended)

1. Create a new **Web Service**
2. Upload this folder (or connect Git and copy these files to repo root)
3. Settings:
   - **Build command:** (leave empty — already built)
   - **Start command:** `npm start` or `node server.js`
   - **Node version:** 18 or 20
4. Add environment variables from `.env.production.example` (only needed if you rebuild on the host; otherwise already embedded)
5. Set **PORT** if the host requires it (most set `PORT` automatically)

### Docker deploy

```bash
docker build -t codex-web .
docker run -p 3000:3000 -e PORT=3000 codex-web
```

## Option B — VPS / Linux server

```bash
# Copy folder to server, then:
cd codex-web-deploy
cp .env.production.example .env.production   # edit if rebuilding
npm start
# Listens on PORT (default 3000)
```

Use nginx/Caddy as reverse proxy for HTTPS on your domain.

## Option C — Firebase Hosting

Firebase CLI login is unreliable on Chromebook. Use a **service account**:

1. Firebase Console → Project settings → Service accounts → Generate new private key
2. Save as `.firebase-service-account.json` in the parent Codex project (not in this upload folder unless your host supports secrets)
3. From the main Codex project (not required for static Node hosts):

```bash
npm run deploy:hosting
```

For Firebase **App Hosting** (full Next.js), connect your GitHub repo in Firebase Console → App Hosting instead of uploading this folder manually.

## Option D — Vercel

Easiest path: import the `codex-web` source folder from GitHub in Vercel (not this prebuilt package). Vercel builds automatically.

If using this package: set framework to **Other**, output is already built, start command `node server.js`.

## Health check

After deploy, open your site URL and verify:

- [ ] Login works
- [ ] Feed loads
- [ ] Create post works
- [ ] Like / search / topics work

## Firebase rules reminder

Ensure Firestore + Storage rules in Firebase Console allow the **web app** (`1:977568853771:web:...`). See `firestore.rules` in the main Codex project.