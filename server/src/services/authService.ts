import crypto from 'node:crypto';
import { UniqueConstraintError, type Transaction } from 'sequelize';
import { env } from '../config/env';
import { AppError } from '../lib/errors';
import { RefreshToken, User, sequelize } from '../models';
import { verifyIdToken, type IdClaims } from './firebaseVerifier';
import { ACCESS_TTL_SECONDS, hashToken, newRefreshToken, signAccessToken } from './tokens';

const ALLOWED_PROVIDERS = new Set(['phone', 'google.com']);

export interface RequestMeta {
  ip?: string | undefined;
  userAgent?: string | undefined;
}

export interface SelfUser {
  id: string;
  phoneMasked: string | null;
  email: string | null;
  emailVerified: boolean;
  profileCompleted: boolean;
  displayName: string | null;
  username: string | null;
  /** Path on this server (for example /media/avatars/<key>), or null when there is no photo. */
  avatarUrl: string | null;
  countryCode: string | null;
  /** True when this account was refused for being under 18. The app then shows a blocked screen. */
  ageRestricted: boolean;
}

export interface TokenResponse {
  accessToken: string;
  expiresIn: number;
  refreshToken: string;
  user: SelfUser;
  isNewUser?: boolean;
}

export function maskPhone(phone: string | null | undefined): string | null {
  if (!phone) return null;
  const keepStart = Math.min(3, phone.length);
  const keepEnd = Math.min(3, Math.max(phone.length - keepStart, 0));
  const hidden = Math.max(phone.length - keepStart - keepEnd, 0);
  return phone.slice(0, keepStart) + '•'.repeat(hidden) + phone.slice(phone.length - keepEnd);
}

/** What a user may see about themselves. Other users never get phone or email. */
export function serializeSelf(user: User): SelfUser {
  return {
    id: user.publicId,
    phoneMasked: maskPhone(user.phone),
    email: user.email ?? null,
    emailVerified: Boolean(user.emailVerified),
    profileCompleted: Boolean(user.profileCompletedAt),
    displayName: user.displayName ?? null,
    username: user.username ?? null,
    avatarUrl: user.avatarKey ? `/media/avatars/${user.avatarKey}` : null,
    countryCode: user.countryCode ?? null,
    ageRestricted: Boolean(user.ageCheckFailedAt),
  };
}

async function createRefreshRow(userId: number, familyId: string, meta: RequestMeta, transaction: Transaction): Promise<string> {
  const { raw, hash } = newRefreshToken();
  await RefreshToken.create(
    {
      userId,
      tokenHash: hash,
      familyId,
      expiresAt: new Date(Date.now() + env.REFRESH_TOKEN_DAYS * 24 * 60 * 60 * 1000),
      ip: meta.ip ? meta.ip.slice(0, 45) : null,
      userAgent: meta.userAgent ? meta.userAgent.slice(0, 255) : null,
    },
    { transaction },
  );
  return raw;
}

function tokenResponse(user: User, refreshToken: string, extra: { isNewUser?: boolean } = {}): TokenResponse {
  return {
    accessToken: signAccessToken(user.publicId),
    expiresIn: ACCESS_TTL_SECONDS,
    refreshToken,
    user: serializeSelf(user),
    ...extra,
  };
}

interface LoginResult {
  user: User;
  refreshToken: string;
  isNewUser: boolean;
}

async function findOrCreateAndIssue(
  claims: IdClaims,
  provider: string,
  termsVersion: string,
  meta: RequestMeta,
): Promise<LoginResult> {
  return sequelize.transaction(async (t) => {
    const now = new Date();
    const fields = {
      phone: claims.phone_number ?? null,
      email: claims.email ?? null,
      emailVerified: Boolean(claims.email_verified),
      signInProvider: provider,
      lastLoginAt: now,
    };

    let user = await User.findOne({ where: { firebaseUid: claims.uid }, transaction: t, lock: t.LOCK.UPDATE });
    let isNewUser = false;

    if (!user) {
      user = await User.create({ firebaseUid: claims.uid, ...fields, termsVersion, termsAcceptedAt: now }, { transaction: t });
      isNewUser = true;
    } else {
      if (user.status !== 'active') throw new AppError(403, 'account_unavailable');
      user.set(fields);
      if (user.termsVersion !== termsVersion) {
        user.termsVersion = termsVersion;
        user.termsAcceptedAt = now;
      }
      await user.save({ transaction: t });
    }

    const refreshToken = await createRefreshRow(user.id, crypto.randomUUID(), meta, t);
    return { user, refreshToken, isNewUser };
  });
}

export async function loginWithFirebase(input: { idToken: string; termsVersion: string; meta: RequestMeta }): Promise<TokenResponse> {
  const claims = await verifyIdToken(input.idToken);

  // Only accept a sign-in that just happened, so an old leaked ID token cannot be replayed.
  const nowSec = Math.floor(Date.now() / 1000);
  if (!claims.auth_time || nowSec - claims.auth_time > env.AUTH_MAX_AGE_SECONDS) {
    throw new AppError(401, 'stale_token');
  }

  const provider = claims.firebase?.sign_in_provider;
  if (!provider || !ALLOWED_PROVIDERS.has(provider)) throw new AppError(401, 'unsupported_provider');

  let result: LoginResult;
  try {
    result = await findOrCreateAndIssue(claims, provider, input.termsVersion, input.meta);
  } catch (err) {
    if (!(err instanceof UniqueConstraintError)) throw err;
    // Two things can collide: a parallel first login for the same Firebase user (retry once),
    // or the phone number already belongs to a different account.
    const existing = await User.findOne({ where: { firebaseUid: claims.uid } });
    if (!existing) throw new AppError(409, 'account_conflict');
    result = await findOrCreateAndIssue(claims, provider, input.termsVersion, input.meta);
  }
  return tokenResponse(result.user, result.refreshToken, { isNewUser: result.isNewUser });
}

type RefreshOutcome = { fail: [number, string] } | { user: User; refreshToken: string };

/**
 * Rotates a refresh token. Failures that must persist (family revocation) are committed first,
 * then the error is thrown after the transaction has ended.
 */
export async function refreshSession(input: { refreshToken: string; meta: RequestMeta }): Promise<TokenResponse> {
  const outcome = await sequelize.transaction(async (t): Promise<RefreshOutcome> => {
    const row = await RefreshToken.findOne({
      where: { tokenHash: hashToken(input.refreshToken) },
      transaction: t,
      lock: t.LOCK.UPDATE,
    });
    if (!row) return { fail: [401, 'invalid_refresh_token'] };

    const now = new Date();

    if (row.revokedAt) {
      const ageSeconds = (now.getTime() - row.revokedAt.getTime()) / 1000;
      if (ageSeconds <= env.REFRESH_REUSE_GRACE_SECONDS) return { fail: [401, 'refresh_race'] };
      // A rotated token came back after the grace period: treat it as stolen and end this login everywhere.
      await RefreshToken.update({ revokedAt: now }, { where: { familyId: row.familyId, revokedAt: null }, transaction: t });
      return { fail: [401, 'refresh_reuse_detected'] };
    }

    if (row.expiresAt <= now) return { fail: [401, 'invalid_refresh_token'] };

    const user = await User.findByPk(row.userId, { transaction: t });
    if (!user || user.status !== 'active') {
      await RefreshToken.update({ revokedAt: now }, { where: { familyId: row.familyId, revokedAt: null }, transaction: t });
      return { fail: [403, 'account_unavailable'] };
    }

    row.revokedAt = now;
    await row.save({ transaction: t });
    const next = await createRefreshRow(user.id, row.familyId, input.meta, t);
    return { user, refreshToken: next };
  });

  if ('fail' in outcome) throw new AppError(outcome.fail[0], outcome.fail[1]);
  return tokenResponse(outcome.user, outcome.refreshToken);
}

/** Ends the login (family) that owns this refresh token. Always succeeds, so callers learn nothing. */
export async function logout(input: { refreshToken: string }): Promise<void> {
  const row = await RefreshToken.findOne({ where: { tokenHash: hashToken(input.refreshToken) } });
  if (row) {
    await RefreshToken.update({ revokedAt: new Date() }, { where: { familyId: row.familyId, revokedAt: null } });
  }
}
