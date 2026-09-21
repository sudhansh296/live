import type { NextFunction, Request, Response } from 'express';
import { User } from '../models';
import { verifyAccessToken } from '../services/tokens';

/** Every failure looks the same to the client: 401 {"error":"unauthorized"}. */
export async function requireAuth(req: Request, res: Response, next: NextFunction): Promise<void> {
  const [scheme, token] = (req.get('authorization') ?? '').split(' ');
  if (scheme !== 'Bearer' || !token) {
    res.status(401).json({ error: 'unauthorized' });
    return;
  }

  let subject: string | undefined;
  try {
    subject = verifyAccessToken(token).sub;
  } catch {
    res.status(401).json({ error: 'unauthorized' });
    return;
  }

  // Checked on every request so a suspended user loses access immediately, not when the token expires.
  const user = subject ? await User.findOne({ where: { publicId: subject } }) : null;
  if (!user || user.status !== 'active') {
    res.status(401).json({ error: 'unauthorized' });
    return;
  }

  req.user = user;
  next();
}
