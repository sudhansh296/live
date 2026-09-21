import path from 'node:path';
import dotenv from 'dotenv';
import { z } from 'zod';

dotenv.config({ quiet: true });

const schema = z.object({
  NODE_ENV: z.enum(['development', 'production', 'test']).default('development'),
  HOST: z.string().min(1).default('127.0.0.1'),
  PORT: z.coerce.number().int().min(1).max(65535).default(4000),
  TRUST_PROXY: z.coerce.number().int().min(0).default(0),
  CORS_ORIGINS: z.string().default(''),

  DB_HOST: z.string().min(1).default('127.0.0.1'),
  DB_PORT: z.coerce.number().int().min(1).max(65535).default(3306),
  DB_NAME: z.string().optional(),
  DB_USER: z.string().optional(),
  DB_PASSWORD: z.string().optional(),

  // Sessions
  JWT_ACCESS_SECRET: z.string().min(32, 'must be at least 32 characters'),
  JWT_ISSUER: z.string().min(1).default('hivo-live'),
  JWT_AUDIENCE: z.string().min(1).default('hivo-app'),
  ACCESS_TOKEN_MINUTES: z.coerce.number().int().min(1).max(60).default(15),
  REFRESH_TOKEN_DAYS: z.coerce.number().int().min(1).max(90).default(30),
  // A refresh token that was rotated less than this many seconds ago is treated as a retry, not theft.
  REFRESH_REUSE_GRACE_SECONDS: z.coerce.number().int().min(0).max(120).default(10),

  // Firebase login (phone OTP + Google). The service-account JSON lives in server/secrets/ (git-ignored).
  FIREBASE_SERVICE_ACCOUNT_PATH: z.string().optional(),
  // A Firebase ID token is only accepted for a sign-in that happened this recently.
  AUTH_MAX_AGE_SECONDS: z.coerce.number().int().min(30).max(3600).default(300),
  // Requests per minute per IP on /auth/*.
  AUTH_RATE_LIMIT_PER_MINUTE: z.coerce.number().int().min(1).default(20),
  // Requests per minute per IP on the profile routes (username check, profile save).
  PROFILE_RATE_LIMIT_PER_MINUTE: z.coerce.number().int().min(1).default(30),
  // Photo uploads per hour per IP.
  AVATAR_RATE_LIMIT_PER_HOUR: z.coerce.number().int().min(1).default(20),
  // Where uploaded files live. Default: server/uploads (git-ignored). Use a persistent folder in production.
  UPLOADS_DIR: z.string().optional(),
});

const parsed = schema.safeParse(process.env);
if (!parsed.success) {
  const problems = parsed.error.issues.map((i) => `${i.path.join('.')}: ${i.message}`).join('\n  ');
  console.error(`Invalid environment configuration:\n  ${problems}`);
  process.exit(1);
}

const data = parsed.data;

const db =
  data.DB_NAME && data.DB_USER && data.DB_PASSWORD
    ? { name: data.DB_NAME, user: data.DB_USER, password: data.DB_PASSWORD, host: data.DB_HOST, port: data.DB_PORT }
    : null;

export const env = Object.freeze({
  ...data,
  isProd: data.NODE_ENV === 'production',
  corsOrigins: data.CORS_ORIGINS.split(',').map((s) => s.trim()).filter(Boolean),
  /** null until DB_NAME, DB_USER and DB_PASSWORD are all set. */
  db,
  uploadsDir: path.resolve(data.UPLOADS_DIR ?? path.join(__dirname, '..', '..', 'uploads')),
  dbConfigured: db !== null,
});
