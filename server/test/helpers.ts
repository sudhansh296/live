import './setupEnv';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs/promises';
import type { AddressInfo } from 'node:net';
import type { Server } from 'node:http';
import path from 'node:path';
import { app } from '../src/app';
import { AppError } from '../src/lib/errors';
import { RefreshToken, User, sequelize } from '../src/models';
import { setVerifierForTests, type IdClaims } from '../src/services/firebaseVerifier';

const serverDir = path.resolve(__dirname, '..');

// Bring the test database up to date using the same migrations as development.
const migrate = spawnSync(
  process.execPath,
  ['--import', 'tsx', path.join('node_modules', 'sequelize-cli', 'lib', 'sequelize'), 'db:migrate'],
  { cwd: serverDir, env: process.env, encoding: 'utf8' },
);
if (migrate.status !== 0) throw new Error(`Test database migration failed:\n${migrate.stdout}\n${migrate.stderr}`);

// Fake Firebase: an "ID token" is `fake::` + base64url(JSON claims). Anything else is rejected.
setVerifierForTests(async (idToken) => {
  if (!idToken.startsWith('fake::')) throw new AppError(401, 'invalid_token');
  try {
    return JSON.parse(Buffer.from(idToken.slice(6), 'base64url').toString('utf8')) as IdClaims;
  } catch {
    throw new AppError(401, 'invalid_token');
  }
});

export function claims(uid: string, overrides: Partial<IdClaims> = {}): IdClaims {
  return {
    uid,
    auth_time: Math.floor(Date.now() / 1000),
    phone_number: '+919876543210',
    firebase: { sign_in_provider: 'phone' },
    ...overrides,
  };
}

export function idToken(c: IdClaims): string {
  return `fake::${Buffer.from(JSON.stringify(c)).toString('base64url')}`;
}

let server: Server;
let baseUrl = '';

export async function start(): Promise<void> {
  await new Promise<void>((resolve) => {
    server = app.listen(0, '127.0.0.1', () => resolve());
  });
  baseUrl = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
}

export async function stop(): Promise<void> {
  await new Promise<void>((resolve) => server.close(() => resolve()));
  await sequelize.close();
  if (process.env.UPLOADS_DIR) await fs.rm(process.env.UPLOADS_DIR, { recursive: true, force: true });
}

/** A plain fetch against the test server (used for multipart uploads and file downloads). */
export function rawFetch(url: string, init: RequestInit = {}): Promise<Response> {
  return fetch(baseUrl + url, init);
}

export async function resetDb(): Promise<void> {
  await sequelize.query('DELETE FROM refresh_tokens');
  await sequelize.query('DELETE FROM users');
}

export interface ApiResponse {
  status: number;
  // Test code inspects arbitrary JSON, so this stays loosely typed on purpose.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  body: any;
}

export async function api(method: string, url: string, options: { body?: unknown; token?: string } = {}): Promise<ApiResponse> {
  const headers: Record<string, string> = { 'content-type': 'application/json' };
  if (options.token) headers.authorization = `Bearer ${options.token}`;
  const res = await fetch(baseUrl + url, {
    method,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
  const text = await res.text();
  return { status: res.status, body: text ? JSON.parse(text) : null };
}

export function login(uid = 'uid-1', overrides: Partial<IdClaims> = {}): Promise<ApiResponse> {
  return api('POST', '/auth/firebase', {
    body: { idToken: idToken(claims(uid, overrides)), termsVersion: '2026-09', adultConfirmed: true },
  });
}

let phoneCounter = 0;

/** Logs a (fake) Firebase user in and returns their access token. Every user gets their own phone number. */
export async function signIn(uid: string): Promise<string> {
  phoneCounter += 1;
  const res = await login(uid, { phone_number: `+9190000${String(phoneCounter).padStart(5, '0')}` });
  return res.body.accessToken as string;
}

export { RefreshToken, User, sequelize };
