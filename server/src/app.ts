import cors from 'cors';
import express, { type ErrorRequestHandler } from 'express';
import { rateLimit } from 'express-rate-limit';
import helmet from 'helmet';
import { env } from './config/env';
import { AppError } from './lib/errors';
import { authRouter } from './routes/auth';
import { avatarRouter } from './routes/avatar';
import { healthRouter } from './routes/health';
import { meRouter } from './routes/me';
import { profileRouter } from './routes/profile';

export const app = express();

app.set('trust proxy', env.TRUST_PROXY);
app.disable('x-powered-by');

app.use(helmet());

// The Android app does not use CORS. Only allow web origins (admin panel) that are listed explicitly.
if (env.corsOrigins.length > 0) {
  app.use(cors({ origin: env.corsOrigins }));
}

app.use(
  rateLimit({
    windowMs: 60_000,
    limit: 100,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    message: { error: 'too_many_requests' },
  }),
);

app.use(express.json({ limit: '100kb' }));

app.use(healthRouter);
app.use(authRouter);
app.use(meRouter);
app.use(profileRouter);
app.use(avatarRouter);

app.use((_req, res) => {
  res.status(404).json({ error: 'not_found' });
});

// Central error handler: never send stack traces or internal messages to clients.
const errorHandler: ErrorRequestHandler = (err: unknown, _req, res, _next) => {
  if (err instanceof AppError) {
    res.status(err.status).json(err.field ? { error: err.code, field: err.field } : { error: err.code });
    return;
  }
  const type = typeof err === 'object' && err !== null && 'type' in err ? String((err as { type: unknown }).type) : '';
  if (type === 'entity.parse.failed') {
    res.status(400).json({ error: 'invalid_json' });
    return;
  }
  if (type === 'entity.too.large') {
    res.status(413).json({ error: 'payload_too_large' });
    return;
  }
  console.error('Unhandled error:', env.isProd && err instanceof Error ? err.message : err);
  res.status(500).json({ error: 'internal_error' });
};
app.use(errorHandler);
