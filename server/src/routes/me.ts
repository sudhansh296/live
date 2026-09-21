import { Router } from 'express';
import { requireAuth } from '../middleware/requireAuth';
import { serializeSelf } from '../services/authService';

export const meRouter = Router();

meRouter.get('/me', requireAuth, (req, res) => {
  const user = req.user;
  if (!user) {
    res.status(401).json({ error: 'unauthorized' });
    return;
  }
  res.json({ user: serializeSelf(user) });
});
