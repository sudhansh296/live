// Connection settings for sequelize-cli. Reads the same server/.env as the app.
import { env } from '../config/env';

if (!env.db) {
  throw new Error('DB_NAME, DB_USER and DB_PASSWORD must be set in server/.env to run migrations.');
}

const base = {
  username: env.db.user,
  password: env.db.password,
  database: env.db.name,
  host: env.db.host,
  port: env.db.port,
  dialect: 'mysql',
  timezone: '+00:00',
  logging: false,
  dialectOptions: { charset: 'utf8mb4' },
  define: { underscored: true, charset: 'utf8mb4', collate: 'utf8mb4_unicode_ci' },
};

// Tests run with NODE_ENV=test and DB_NAME=hivolive_test. Refuse to run against any other database.
if (env.NODE_ENV === 'test' && !env.db.name.endsWith('_test')) {
  throw new Error('Refusing to run in test mode: DB_NAME must end with "_test".');
}

export const development = base;
export const test = base;
export const production = base;
