import crypto from 'node:crypto';
import jwt, { type JwtPayload } from 'jsonwebtoken';
import { env } from '../config/env';

export const ACCESS_TTL_SECONDS = env.ACCESS_TOKEN_MINUTES * 60;

/** Access token: short-lived, carries only the public user id (sub). Everything else is read from the DB. */
export function signAccessToken(publicId: string): string {
  return jwt.sign({}, env.JWT_ACCESS_SECRET, {
    algorithm: 'HS256',
    subject: publicId,
    issuer: env.JWT_ISSUER,
    audience: env.JWT_AUDIENCE,
    expiresIn: ACCESS_TTL_SECONDS,
    jwtid: crypto.randomUUID(),
  });
}

/** Throws on any problem: bad signature, expired, wrong issuer/audience, wrong algorithm. */
export function verifyAccessToken(token: string): JwtPayload {
  const payload = jwt.verify(token, env.JWT_ACCESS_SECRET, {
    algorithms: ['HS256'],
    issuer: env.JWT_ISSUER,
    audience: env.JWT_AUDIENCE,
  });
  if (typeof payload === 'string') throw new Error('Unexpected token payload');
  return payload;
}

export function hashToken(raw: string): string {
  return crypto.createHash('sha256').update(raw).digest('hex');
}

/** Refresh token: 256 random bits. Only the SHA-256 hash is stored. */
export function newRefreshToken(): { raw: string; hash: string } {
  const raw = crypto.randomBytes(32).toString('base64url');
  return { raw, hash: hashToken(raw) };
}
