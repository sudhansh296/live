import { Sequelize } from 'sequelize';
import { env } from './env';

let sequelize: Sequelize | null = null;

/** Returns null until the database settings are present in .env. */
export function getSequelize(): Sequelize | null {
  if (!env.db) return null;
  sequelize ??= new Sequelize(env.db.name, env.db.user, env.db.password, {
    host: env.db.host,
    port: env.db.port,
    dialect: 'mysql', // works with MariaDB (XAMPP)
    timezone: '+00:00', // store and read every timestamp in UTC
    logging: false,
    dialectOptions: { charset: 'utf8mb4' },
    define: {
      underscored: true,
      charset: 'utf8mb4',
      collate: 'utf8mb4_unicode_ci',
    },
    pool: { max: 10, min: 0, acquire: 10000, idle: 10000 },
  });
  return sequelize;
}

export type DatabaseStatus = 'not_configured' | 'up' | 'down';

/** Never leaks the underlying error. */
export async function checkDatabase(): Promise<DatabaseStatus> {
  const db = getSequelize();
  if (!db) return 'not_configured';
  try {
    await db.authenticate();
    return 'up';
  } catch {
    return 'down';
  }
}

export async function closeDatabase(): Promise<void> {
  if (sequelize) await sequelize.close();
}
