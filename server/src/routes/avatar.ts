import type { NextFunction, Request, RequestHandler, Response } from 'express';
import { Router } from 'express';
import { rateLimit } from 'express-rate-limit';
import multer from 'multer';
import { env } from '../config/env';
import { AppError } from '../lib/errors';
import { requireAuth } from '../middleware/requireAuth';
import { serializeSelf } from '../services/authService';
import {
  AVATAR_DIR,
  MAX_AVATAR_UPLOAD_BYTES,
  avatarFileExists,
  removeAvatar,
  setAvatar,
} from '../services/avatarService';

export const avatarRouter = Router();

const limiter = rateLimit({
  windowMs: 60 * 60_000,
  limit: env.AVATAR_RATE_LIMIT_PER_HOUR,
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  message: { error: 'too_many_requests' },
});

// The photo is kept in memory only until it has been checked and re-encoded. The original is never saved.
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: MAX_AVATAR_UPLOAD_BYTES, files: 1, fields: 0, parts: 2 },
});

const receiveAvatar: RequestHandler = (req: Request, res: Response, next: NextFunction) => {
  upload.single('avatar')(req, res, (err: unknown) => {
    if (!err) return next();
    if (err instanceof multer.MulterError) {
      if (err.code === 'LIMIT_FILE_SIZE') return next(new AppError(413, 'file_too_large', 'avatar'));
      return next(new AppError(400, 'invalid_request', err.field));
    }
    return next(err);
  });
};

avatarRouter.post('/me/avatar', limiter, requireAuth, receiveAvatar, async (req, res) => {
  const user = req.user;
  if (!user) throw new AppError(401, 'unauthorized');
  if (!req.file) throw new AppError(400, 'file_required', 'avatar');
  res.json({ user: serializeSelf(await setAvatar(user, req.file.buffer)) });
});

avatarRouter.delete('/me/avatar', requireAuth, async (req, res) => {
  const user = req.user;
  if (!user) throw new AppError(401, 'unauthorized');
  res.json({ user: serializeSelf(await removeAvatar(user)) });
});

// Profile photos are public, like on every social app. Their names are random, so they cannot be guessed,
// and the file name is checked strictly, so this route can only ever serve files from the avatar folder.
avatarRouter.get('/media/avatars/:file', async (req, res) => {
  const file = req.params.file;
  if (typeof file !== 'string' || !(await avatarFileExists(file))) {
    res.status(404).json({ error: 'not_found' });
    return;
  }
  res.set('Cache-Control', 'public, max-age=31536000, immutable'); // the name changes whenever the photo changes
  res.type('image/webp');
  res.sendFile(file, { root: AVATAR_DIR, dotfiles: 'deny' }, (err) => {
    if (err && !res.headersSent) res.status(404).json({ error: 'not_found' });
  });
});
