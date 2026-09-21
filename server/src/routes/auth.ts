import { Router, type Request } from 'express';
import { rateLimit } from 'express-rate-limit';
import { z } from 'zod';
import { env } from '../config/env';
import { parseBody } from '../lib/validate';
import { loginWithFirebase, logout, refreshSession, type RequestMeta } from '../services/authService';

export const authRouter = Router();

// Stricter than the global limit: these endpoints are the main target for guessing and abuse.
authRouter.use(
  '/auth',
  rateLimit({
    windowMs: 60_000,
    limit: env.AUTH_RATE_LIMIT_PER_MINUTE,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    message: { error: 'too_many_requests' },
  }),
);

const firebaseBody = z
  .object({
    idToken: z.string().min(20).max(4096),
    // Version of the Terms/Privacy text the user agreed to on the welcome screen.
    termsVersion: z.string().min(1).max(20),
    // The welcome-screen checkbox: "I am 18 or older and I agree to the Terms and Privacy Policy".
    // Anything other than `true` is refused. It is a declaration only; the birth date check on the
    // profile screen is the real age gate.
    adultConfirmed: z.literal(true),
  })
  .strict();

const refreshBody = z.object({ refreshToken: z.string().min(20).max(200) }).strict();

function meta(req: Request): RequestMeta {
  return { ip: req.ip, userAgent: req.get('user-agent') };
}

authRouter.post('/auth/firebase', async (req, res) => {
  const { idToken, termsVersion } = parseBody(firebaseBody, req.body); // adultConfirmed is checked (must be true) by the schema
  res.json(await loginWithFirebase({ idToken, termsVersion, meta: meta(req) }));
});

authRouter.post('/auth/refresh', async (req, res) => {
  const { refreshToken } = parseBody(refreshBody, req.body);
  res.json(await refreshSession({ refreshToken, meta: meta(req) }));
});

authRouter.post('/auth/logout', async (req, res) => {
  const { refreshToken } = parseBody(refreshBody, req.body);
  await logout({ refreshToken });
  res.status(204).end();
});
