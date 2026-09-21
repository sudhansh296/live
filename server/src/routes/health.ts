import { Router } from 'express';
import { checkDatabase } from '../config/database';

export const healthRouter = Router();

healthRouter.get('/health', async (_req, res) => {
  const db = await checkDatabase();
  const degraded = db === 'down';
  res.status(degraded ? 503 : 200).json({ status: degraded ? 'degraded' : 'ok', db });
});
