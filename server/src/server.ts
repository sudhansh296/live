import { app } from './app';
import { closeDatabase } from './config/database';
import { env } from './config/env';

if (env.isProd && !env.dbConfigured) {
  console.error('DB_NAME, DB_USER and DB_PASSWORD are required in production.');
  process.exit(1);
}

const server = app.listen(env.PORT, env.HOST, () => {
  console.log(`Hivo Live API listening on http://${env.HOST}:${env.PORT} (${env.NODE_ENV})`);
});

function shutdown(signal: string): void {
  console.log(`${signal} received, shutting down`);
  server.close(() => {
    void closeDatabase().finally(() => process.exit(0));
  });
  setTimeout(() => process.exit(1), 10_000).unref();
}

process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));
