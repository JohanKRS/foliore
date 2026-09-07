import { spawn } from 'node:child_process';
import { once } from 'node:events';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
import net from 'node:net';
const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'foliore-reports-test-'));
const probe = net.createServer();
probe.listen(0, '127.0.0.1');
await once(probe, 'listening');
const port = probe.address().port;
await new Promise(resolve => probe.close(resolve));
const child = spawn(process.execPath, ['support/server.mjs'], { env: { ...process.env, FOLIORE_REPORTS_DIR: directory, FOLIORE_REPORTS_PORT: String(port) }, stdio: ['ignore', 'pipe', 'pipe'] });
try {
  const url = `http://127.0.0.1:${port}/api/content-reports`;
  let ready = false;
  for (let i = 0; i < 100; i++) {
    try { await fetch(url); ready = true; break; } catch { await new Promise(resolve => setTimeout(resolve, 20)); }
  }
  assert.ok(ready, 'local server must start');
  const post = message => fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ message }) });
  assert.equal((await post('')).status, 400);
  for (const body of ['{', 'null']) {
    assert.equal((await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body })).status, 400);
  }
  assert.equal((await post('x'.repeat(4001))).status, 400);
  const result = await post('Synthetic QA report — no real user data');
  assert.equal(result.status, 201);
  const { id } = await result.json();
  const stored = JSON.parse(await fs.readFile(path.join(directory, `${id}.json`), 'utf8'));
  assert.equal(stored.message, 'Synthetic QA report — no real user data');
  assert.deepEqual(Object.keys(stored).sort(), ['id', 'message', 'receivedAt']);
  assert.equal((await fetch(`http://127.0.0.1:${port}/${id}.json`)).status, 404);
  assert.equal((await fetch(url, { method: 'POST', body: 'plain text' })).status, 415);
  console.log('PASS: bounded report input, durable queue receipt, private queue, content type validation');
} finally {
  child.kill();
  await once(child, 'exit');
  await fs.rm(directory, { recursive: true, force: true });
}
