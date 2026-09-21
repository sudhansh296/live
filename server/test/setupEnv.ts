// Imported first by helpers.ts, before anything from src/: tests use their own database and secrets.
import os from 'node:os';
import path from 'node:path';

process.env.NODE_ENV = 'test';
process.env.DB_NAME = 'hivolive_test';
process.env.JWT_ACCESS_SECRET = 'test-only-secret-test-only-secret-test-only-secret';
process.env.AUTH_RATE_LIMIT_PER_MINUTE = '1000';
process.env.PROFILE_RATE_LIMIT_PER_MINUTE = '1000';
process.env.AVATAR_RATE_LIMIT_PER_HOUR = '1000';
// Uploaded files go to a throw-away folder, never to the real server/uploads.
process.env.UPLOADS_DIR = path.join(os.tmpdir(), `hivo-test-uploads-${process.pid}`);
