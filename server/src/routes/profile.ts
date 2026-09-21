import { Router } from 'express';
import { rateLimit } from 'express-rate-limit';
import { z } from 'zod';
import { env } from '../config/env';
import { AppError } from '../lib/errors';
import { parseBody } from '../lib/validate';
import { requireAuth } from '../middleware/requireAuth';
import { serializeSelf } from '../services/authService';
import { checkUsername, completeProfile } from '../services/profileService';

export const profileRouter = Router();

const limiter = rateLimit({
  windowMs: 60_000,
  limit: env.PROFILE_RATE_LIMIT_PER_MINUTE,
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  message: { error: 'too_many_requests' },
});

const usernameQuery = z.object({ u: z.string().min(1).max(40) }).strict();

// Field lengths are capped here only to stop huge payloads; the real rules live in lib/profileRules.ts.
const profileBody = z
  .object({
    displayName: z.string().max(100),
    username: z.string().max(40),
    birthDate: z.string().max(10),
    countryCode: z.string().max(5),
  })
  .strict();

profileRouter.get('/profile/username-available', limiter, requireAuth, async (req, res) => {
  const { u } = parseBody(usernameQuery, req.query);
  res.json(await checkUsername(u));
});

profileRouter.put('/me/profile', limiter, requireAuth, async (req, res) => {
  const user = req.user;
  if (!user) throw new AppError(401, 'unauthorized');
  const updated = await completeProfile(user, parseBody(profileBody, req.body));
  res.json({ user: serializeSelf(updated) });
});
