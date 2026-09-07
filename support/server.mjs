// Bind behind an HTTPS reverse proxy; the queue is never served over HTTP.
import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const directory = path.resolve(process.env.FOLIORE_REPORTS_DIR || fileURLToPath(new URL('./private-reports', import.meta.url)));
await fs.mkdir(directory, { recursive: true, mode: 0o700 });
const limits = new Map();
const retention = 90 * 86400000;
async function clean() {
  for (const entry of await fs.readdir(directory, { withFileTypes: true })) {
    if (!entry.isFile() || !/^[a-f0-9-]{36}\.json$/.test(entry.name)) continue;
    const file = path.join(directory, entry.name);
    if (Date.now() - (await fs.stat(file)).mtimeMs > retention) await fs.unlink(file);
  }
  for (const [key, value] of limits) if (value.expires <= Date.now()) limits.delete(key);
}
await clean();
setInterval(() => clean().catch(() => {}), 3600000).unref();
const server = http.createServer(async (req, res) => {
  const reply = (code, data) => { res.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(data)); };
  if (req.url !== '/api/content-reports' || req.method !== 'POST') return reply(404, { error: 'not_found' });
  if (!req.headers['content-type']?.startsWith('application/json')) return reply(415, { error: 'json_required' });
  // The proxy must overwrite X-Real-IP, never forward a value supplied by a client.
  const address = String(req.headers['x-real-ip'] || req.socket.remoteAddress);
  const key = crypto.createHash('sha256').update(address).digest('hex');
  const window = limits.get(key);
  const bucket = window && window.expires > Date.now() ? window : { count: 0, expires: Date.now() + 3600000 };
  if (limits.size > 10000 || ++bucket.count > 20) return reply(429, { error: 'retry_later' });
  limits.set(key, bucket);
  try {
    let size = 0;
    const chunks = [];
    for await (const chunk of req) {
      size += chunk.length;
      if (size > 32768) { reply(413, { error: 'too_large' }); return; }
      chunks.push(chunk);
    }
    let payload;
    try { payload = JSON.parse(Buffer.concat(chunks).toString('utf8')); }
    catch { return reply(400, { error: 'invalid_json' }); }
    if (typeof payload?.message !== 'string' || !payload.message.trim() || payload.message.length > 4000) return reply(400, { error: 'invalid_message' });
    const id = crypto.randomUUID();
    // A receipt is returned only after the report is durably written.
    const file = await fs.open(path.join(directory, `${id}.json`), 'wx', 0o600);
    try { await file.writeFile(JSON.stringify({ id, receivedAt: new Date().toISOString(), message: payload.message.trim() })); await file.sync(); }
    finally { await file.close(); }
    const parent = await fs.open(directory, 'r');
    try { await parent.sync(); } finally { await parent.close(); }
    reply(201, { id });
  } catch { if (!res.headersSent) reply(500, { error: 'not_received' }); }
});
server.requestTimeout = 15000;
server.headersTimeout = 10000;
server.listen(Number(process.env.FOLIORE_REPORTS_PORT || 8787), '127.0.0.1');
