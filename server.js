require('dotenv').config();
const express = require('express');
const multer = require('multer');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { google } = require('googleapis');

const app = express();
const PORT = Number(process.env.PORT || 3000);
const ROOT = __dirname;
const TOKENS_PATH = path.join(ROOT, '.tokens.json');
const upload = multer({ dest: path.join(ROOT, 'uploads'), limits: { fileSize: 1024 * 1024 * 1024 } });
app.use(express.json({ limit: '1mb' }));
app.use(express.static(path.join(ROOT, 'public')));

const SCOPES = [
  'https://www.googleapis.com/auth/drive.file',
  'https://www.googleapis.com/auth/youtube',
  'https://www.googleapis.com/auth/youtube.upload'
];
const oauthStates = new Set();
function oauthConfigured() {
  return Boolean(process.env.GOOGLE_CLIENT_ID && process.env.GOOGLE_CLIENT_SECRET && process.env.GOOGLE_REDIRECT_URI);
}
function oauthClient() {
  if (!oauthConfigured()) throw new Error('Google OAuth is not configured. Add the credentials to .env first.');
  return new google.auth.OAuth2(process.env.GOOGLE_CLIENT_ID, process.env.GOOGLE_CLIENT_SECRET, process.env.GOOGLE_REDIRECT_URI);
}
function readTokens() {
  if (!fs.existsSync(TOKENS_PATH)) return null;
  try { return JSON.parse(fs.readFileSync(TOKENS_PATH, 'utf8')); } catch { return null; }
}
function saveTokens(tokens) { fs.writeFileSync(TOKENS_PATH, JSON.stringify(tokens, null, 2), { mode: 0o600 }); }
function authorizedClient() {
  const tokens = readTokens();
  if (!tokens) return null;
  const client = oauthClient();
  client.setCredentials(tokens);
  client.on('tokens', fresh => saveTokens({ ...tokens, ...fresh }));
  return client;
}

app.get('/api/health', (_req, res) => res.json({ ok: true, service: 'LiveDashcam', googleOAuthConfigured: oauthConfigured(), googleConnected: Boolean(readTokens()), camera: 'not-connected-unverified', note: 'Camera stream bridge must be configured and tested separately.' }));
app.get('/api/google/status', (_req, res) => res.json({ configured: oauthConfigured(), connected: Boolean(readTokens()) }));
app.get('/auth/google', (req, res) => {
  try {
    const client = oauthClient();
    const state = crypto.randomBytes(24).toString('hex');
    oauthStates.add(state);
    const url = client.generateAuthUrl({ access_type: 'offline', prompt: 'consent', scope: SCOPES, state });
    res.redirect(url);
  } catch (e) { res.status(400).send(pageMessage('Google setup required', e.message)); }
});
app.get('/oauth2callback', async (req, res) => {
  try {
    if (!req.query.state || !oauthStates.has(String(req.query.state))) return res.status(400).send(pageMessage('Sign-in expired', 'Please start Google sign-in again.'));
    oauthStates.delete(String(req.query.state));
    const client = oauthClient();
    const { tokens } = await client.getToken(String(req.query.code || ''));
    saveTokens(tokens);
    res.send(pageMessage('Google connected', 'Google authorization completed. You can return to LiveDashcam. Keep this server private and never share .tokens.json.'));
  } catch (e) { res.status(400).send(pageMessage('Google sign-in failed', e.message)); }
});
app.post('/api/google/disconnect', (_req, res) => {
  try { if (fs.existsSync(TOKENS_PATH)) fs.unlinkSync(TOKENS_PATH); res.json({ ok: true }); }
  catch (e) { res.status(500).json({ error: e.message }); }
});
app.post('/api/drive/upload', upload.single('file'), async (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'Choose a recording file to upload.' });
  try {
    const auth = authorizedClient();
    if (!auth) return res.status(401).json({ error: 'Connect Google first at /auth/google.' });
    const drive = google.drive({ version: 'v3', auth });
    const metadata = { name: path.basename(req.file.originalname), ...(process.env.GOOGLE_DRIVE_FOLDER_ID ? { parents: [process.env.GOOGLE_DRIVE_FOLDER_ID] } : {}) };
    const result = await drive.files.create({ requestBody: metadata, media: { mimeType: req.file.mimetype || 'application/octet-stream', body: fs.createReadStream(req.file.path) }, fields: 'id,name,mimeType,size,webViewLink' });
    res.json({ ok: true, file: result.data });
  } catch (e) { res.status(500).json({ error: e.message }); }
  finally { if (req.file?.path && fs.existsSync(req.file.path)) fs.unlink(req.file.path, () => {}); }
});
app.post('/api/youtube/create-broadcast', async (req, res) => {
  try {
    const auth = authorizedClient();
    if (!auth) return res.status(401).json({ error: 'Connect Google first at /auth/google.' });
    const title = String(req.body?.title || 'LiveDashcam Dashcam Live').slice(0, 100);
    const privacyStatus = ['private', 'unlisted', 'public'].includes(req.body?.privacyStatus) ? req.body.privacyStatus : 'private';
    const youtube = google.youtube({ version: 'v3', auth });
    const broadcast = await youtube.liveBroadcasts.insert({ part: ['snippet', 'status', 'contentDetails'], requestBody: { snippet: { title, scheduledStartTime: new Date(Date.now() + 5 * 60 * 1000).toISOString() }, status: { privacyStatus, selfDeclaredMadeForKids: false }, contentDetails: { enableAutoStart: false, enableAutoStop: false } } });
    const stream = await youtube.liveStreams.insert({ part: ['snippet', 'cdn', 'status'], requestBody: { snippet: { title: `${title} stream` }, cdn: { ingestionType: 'rtmp', resolution: 'variable', frameRate: 'variable' }, status: { privacyStatus } } });
    const streamId = stream.data.id;
    await youtube.liveBroadcasts.bind({ id: broadcast.data.id, part: ['id', 'contentDetails'], streamId });
    res.json({ ok: true, broadcastId: broadcast.data.id, watchUrl: `https://www.youtube.com/watch?v=${broadcast.data.id}`, ingestionAddress: stream.data.cdn?.ingestionInfo?.ingestionAddress, streamName: stream.data.cdn?.ingestionInfo?.streamName, warning: 'This creates YouTube resources only. A compatible camera/encoder must send video to the RTMP ingestion address; no video is transmitted by this API.' });
  } catch (e) { res.status(500).json({ error: e.message, hint: 'The Google account needs YouTube API access and live streaming enabled on the channel.' }); }
});
// The camera source is intentionally not faked. Add a verified Oncord/Viidure-compatible bridge here.
app.get('/api/camera/status', (_req, res) => res.json({ connected: false, source: null, message: 'No verified Oncord 4K stream adapter is configured. The Viidure app showing video does not automatically expose a browser/API feed.' }));
app.get('*', (_req, res) => res.sendFile(path.join(ROOT, 'public', 'index.html')));
function pageMessage(title, message) { return `<!doctype html><meta name="viewport" content="width=device-width"><title>${escapeHtml(title)}</title><body style="background:#080b12;color:#eef3ff;font:16px system-ui;padding:32px;max-width:650px;margin:auto"><h1>${escapeHtml(title)}</h1><p>${escapeHtml(message)}</p><p><a style="color:#a7c8ff" href="/">Return to LiveDashcam</a></p></body>`; }
function escapeHtml(s) { return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
app.listen(PORT, () => console.log(`LiveDashcam running at http://localhost:${PORT}`));
