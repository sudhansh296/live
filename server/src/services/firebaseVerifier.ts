import fs from 'node:fs';
import path from 'node:path';
import { cert, getApps, initializeApp, type App } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { env } from '../config/env';
import { AppError } from '../lib/errors';

/** The parts of a Firebase ID token we use. firebase-admin's DecodedIdToken fits this shape. */
export interface IdClaims {
  uid: string;
  auth_time?: number | undefined;
  phone_number?: string | undefined;
  email?: string | undefined;
  email_verified?: boolean | undefined;
  firebase?: { sign_in_provider?: string | undefined } | undefined;
}

type Verifier = (idToken: string) => Promise<IdClaims>;

const SECRETS_DIR = path.resolve(__dirname, '..', '..', 'secrets');

// firebase-admin error codes that mean "this token is not acceptable" (client problem, HTTP 401).
const BAD_TOKEN_CODES = new Set([
  'auth/argument-error',
  'auth/id-token-expired',
  'auth/id-token-revoked',
  'auth/invalid-id-token',
  'auth/user-disabled',
  'auth/user-not-found',
]);

let firebaseApp: App | null = null;
let testVerifier: Verifier | null = null;

function findServiceAccountFile(): string | null {
  if (env.FIREBASE_SERVICE_ACCOUNT_PATH) return path.resolve(env.FIREBASE_SERVICE_ACCOUNT_PATH);
  if (!fs.existsSync(SECRETS_DIR)) return null;
  const first = fs.readdirSync(SECRETS_DIR).find((f) => f.toLowerCase().endsWith('.json'));
  return first ? path.join(SECRETS_DIR, first) : null;
}

function getFirebaseApp(): App | null {
  if (firebaseApp) return firebaseApp;
  const file = findServiceAccountFile();
  if (!file || !fs.existsSync(file)) return null;
  const credentials: unknown = JSON.parse(fs.readFileSync(file, 'utf8'));
  firebaseApp = getApps()[0] ?? initializeApp({ credential: cert(credentials as Parameters<typeof cert>[0]) });
  return firebaseApp;
}

function errorCode(err: unknown): string | undefined {
  return typeof err === 'object' && err !== null && 'code' in err ? String((err as { code: unknown }).code) : undefined;
}

/** Returns the decoded Firebase ID token, or throws AppError(401 invalid_token / 503 auth_unavailable). */
export async function verifyIdToken(idToken: string): Promise<IdClaims> {
  if (testVerifier) return testVerifier(idToken);

  const app = getFirebaseApp();
  if (!app) {
    console.error('Firebase service-account key not found in server/secrets/. Login is unavailable.');
    throw new AppError(503, 'auth_unavailable');
  }
  try {
    // checkRevoked = true: also rejects tokens of disabled or signed-out-everywhere users.
    return await getAuth(app).verifyIdToken(idToken, true);
  } catch (err) {
    const code = errorCode(err);
    if (code && BAD_TOKEN_CODES.has(code)) throw new AppError(401, 'invalid_token');
    console.error('Firebase token verification failed:', code ?? (err instanceof Error ? err.message : 'unknown'));
    throw new AppError(503, 'auth_unavailable');
  }
}

/** Tests replace Firebase with a stub. Never used in production code paths. */
export function setVerifierForTests(fn: Verifier | null): void {
  if (env.NODE_ENV !== 'test') throw new Error('setVerifierForTests is only allowed when NODE_ENV=test');
  testVerifier = fn;
}
