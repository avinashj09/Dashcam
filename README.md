# LiveDashcam — final project package

Apple TV-inspired dashcam dashboard plus a Node.js backend scaffold for Google OAuth, Google Drive file uploads, and YouTube Live broadcast resource creation.

## What's included
- `public/index.html` — responsive dark-mode LiveDashcam dashboard with Drive-save, Live Only and YouTube preferences.
- `server.js` — API server, Google OAuth flow, Drive upload endpoint, YouTube broadcast/RTMP stream creation endpoint, and camera status endpoint.
- `.env.example` — environment configuration template.
- `.gitignore` — excludes credentials, tokens, and uploaded files.

## Honest capability status
- Website design: included.
- Google Drive API upload route: implemented, requires Google OAuth setup and a file submitted to `POST /api/drive/upload` as multipart field `file`.
- YouTube Live resource creation: implemented, requires OAuth and YouTube live streaming enabled. It creates a broadcast and RTMP stream; it does **not** send camera video.
- Oncord 4K / Viidure live feed: **not connected**. The camera protocol and a compatible bridge have not been verified. A dashcam being visible in Viidure does not prove its stream is available to a website.
- Browser preferences: the included UI stores some options locally. The interface is not yet wired to start automatic camera recording/upload or push a video feed to YouTube.

## Run locally
Requires a computer/server with Node.js 20+. An iPhone alone can download and upload the project, but it cannot run this Node server as a persistent public backend.

1. Copy `.env.example` to `.env` and fill Google OAuth values.
2. In Google Cloud Console, enable **Google Drive API** and **YouTube Data API v3**; configure an OAuth consent screen and OAuth client.
3. Add the exact redirect URI from `GOOGLE_REDIRECT_URI` to the OAuth client. For local use it is `http://localhost:3000/oauth2callback`; for hosted use, set it to your HTTPS domain callback.
4. Run `npm install`, then `npm start`.
5. Open `/auth/google` on the server domain and approve access. Keep `.env` and `.tokens.json` private.
6. Test `/api/health` and `/api/google/status`.

## Hosting
Deploy the Node app to a Node-capable host (not static-only GitHub Pages). Set environment variables in the host dashboard. Use HTTPS in production and configure `BASE_URL`/`GOOGLE_REDIRECT_URI` to the deployed HTTPS domain. The app stores OAuth tokens in `.tokens.json`; for production use persistent private storage, restrict access to the server, add user authentication, CSRF/rate limits, and a proper secret manager before public exposure.

## Upload a recording
Once Google OAuth is connected, send a multipart POST to `/api/drive/upload` with the file field named `file`. Optional `GOOGLE_DRIVE_FOLDER_ID` chooses a target folder; otherwise Drive uploads to the connected account's root. This route uploads a supplied file; it does not capture dashcam video itself.

## YouTube Live
`POST /api/youtube/create-broadcast` JSON example:
```json
{"title":"LiveDashcam Dashcam Live","privacyStatus":"private"}
```
The response includes an RTMP ingestion address and stream name. Treat the stream name as a secret. A compatible encoder/bridge must send actual video to YouTube's RTMP endpoint. Do not expose stream credentials in frontend JavaScript or public logs.

## Next required step for actual camera live view
Identify a documented Oncord camera streaming protocol or a supported SDK/bridge. Then implement a bridge that reads the camera feed and relays it in a browser-compatible format (for example WebRTC/HLS, depending on source and latency needs). Do not assume the Viidure feed is externally accessible until tested.
