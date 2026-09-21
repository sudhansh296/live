import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import { after, before, beforeEach, describe, it } from 'node:test';
import jwt from 'jsonwebtoken';
import { Op } from 'sequelize';
import * as h from './helpers';

const SECRET = process.env.JWT_ACCESS_SECRET as string;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

before(() => h.start());
after(() => h.stop());
beforeEach(() => h.resetDb());

describe('health', () => {
  it('reports the database as up', async () => {
    const res = await h.api('GET', '/health');
    assert.equal(res.status, 200);
    assert.deepEqual(res.body, { status: 'ok', db: 'up' });
  });
});

describe('POST /auth/firebase', () => {
  it('rejects a missing or unknown body field', async () => {
    const a = await h.api('POST', '/auth/firebase', { body: { idToken: h.idToken(h.claims('u')) } });
    assert.equal(a.status, 400);
    assert.deepEqual(a.body, { error: 'invalid_request', field: 'termsVersion' });
    const b = await h.api('POST', '/auth/firebase', {
      body: { idToken: h.idToken(h.claims('u')), termsVersion: '2026-09', adultConfirmed: true, isAdmin: true },
    });
    assert.equal(b.status, 400);
  });

  it('refuses a login unless the 18+ / Terms checkbox was ticked', async () => {
    const token = h.idToken(h.claims('u-consent'));
    const missing = await h.api('POST', '/auth/firebase', { body: { idToken: token, termsVersion: '2026-09' } });
    assert.equal(missing.status, 400);
    assert.deepEqual(missing.body, { error: 'invalid_request', field: 'adultConfirmed' });
    for (const value of [false, 'true', 1, null]) {
      const res = await h.api('POST', '/auth/firebase', { body: { idToken: token, termsVersion: '2026-09', adultConfirmed: value } });
      assert.equal(res.status, 400, `adultConfirmed=${JSON.stringify(value)} must be refused`);
      assert.equal(res.body.field, 'adultConfirmed');
    }
    assert.equal(await h.User.count(), 0, 'no account may be created without the confirmation');
  });

  it('rejects a token that Firebase does not accept', async () => {
    const res = await h.api('POST', '/auth/firebase', {
      body: { idToken: 'not-a-real-token-at-all-xxxxxxxx', termsVersion: '2026-09', adultConfirmed: true },
    });
    assert.equal(res.status, 401);
    assert.deepEqual(res.body, { error: 'invalid_token' });
  });

  it('rejects a sign-in that is too old (replay protection)', async () => {
    const res = await h.login('uid-old', { auth_time: Math.floor(Date.now() / 1000) - 3600 });
    assert.equal(res.status, 401);
    assert.deepEqual(res.body, { error: 'stale_token' });
  });

  it('rejects sign-in providers we do not support', async () => {
    const res = await h.login('uid-anon', { firebase: { sign_in_provider: 'anonymous' } });
    assert.equal(res.status, 401);
    assert.deepEqual(res.body, { error: 'unsupported_provider' });
  });

  it('creates a user on first phone login and returns only safe fields', async () => {
    const res = await h.login('uid-phone');
    assert.equal(res.status, 200);
    const b = res.body;
    assert.equal(b.isNewUser, true);
    assert.equal(b.expiresIn, 900);
    assert.ok(b.accessToken && b.refreshToken);
    assert.match(b.user.id, UUID);
    assert.equal(b.user.phoneMasked, '+91•••••••210');
    assert.equal(b.user.profileCompleted, false);
    assert.deepEqual(Object.keys(b.user).sort(), [
      'ageRestricted',
      'avatarUrl',
      'countryCode',
      'displayName',
      'email',
      'emailVerified',
      'id',
      'phoneMasked',
      'profileCompleted',
      'username',
    ]);
    assert.ok(!JSON.stringify(b).includes('uid-phone'), 'firebase uid must not be sent to the client');

    const user = await h.User.findOne({ where: { firebaseUid: 'uid-phone' } });
    assert.equal(user?.termsVersion, '2026-09');
    assert.ok(user?.termsAcceptedAt);
  });

  it('stores only a hash of the refresh token', async () => {
    const res = await h.login('uid-hash');
    const [rows] = (await h.sequelize.query('SELECT token_hash FROM refresh_tokens')) as [Array<{ token_hash: string }>, unknown];
    assert.equal(rows.length, 1);
    assert.equal(rows[0]?.token_hash.length, 64);
    assert.equal(rows[0]?.token_hash, crypto.createHash('sha256').update(res.body.refreshToken).digest('hex'));
    assert.notEqual(rows[0]?.token_hash, res.body.refreshToken);
  });

  it('logs the same person into the same account next time', async () => {
    const first = await h.login('uid-again');
    const second = await h.login('uid-again');
    assert.equal(second.body.isNewUser, false);
    assert.equal(second.body.user.id, first.body.user.id);
    assert.notEqual(second.body.refreshToken, first.body.refreshToken);
    assert.equal(await h.User.count(), 1);
  });

  it('supports Google sign-in', async () => {
    const res = await h.login('uid-google', {
      phone_number: undefined,
      email: 'someone@example.com',
      email_verified: true,
      firebase: { sign_in_provider: 'google.com' },
    });
    assert.equal(res.status, 200);
    assert.equal(res.body.user.email, 'someone@example.com');
    assert.equal(res.body.user.emailVerified, true);
    assert.equal(res.body.user.phoneMasked, null);
  });

  it('refuses a suspended account', async () => {
    await h.login('uid-bad');
    await h.User.update({ status: 'suspended' }, { where: { firebaseUid: 'uid-bad' } });
    const res = await h.login('uid-bad');
    assert.equal(res.status, 403);
    assert.deepEqual(res.body, { error: 'account_unavailable' });
  });

  it('survives two simultaneous first logins for the same user', async () => {
    const [a, b] = await Promise.all([h.login('uid-race'), h.login('uid-race')]);
    assert.equal(a?.status, 200);
    assert.equal(b?.status, 200);
    assert.equal(a?.body.user.id, b?.body.user.id);
    assert.equal(await h.User.count(), 1);
  });
});

describe('GET /me', () => {
  it('needs a valid access token', async () => {
    assert.equal((await h.api('GET', '/me')).status, 401);
    assert.equal((await h.api('GET', '/me', { token: 'garbage' })).status, 401);
    const bad = await h.api('GET', '/me');
    assert.deepEqual(bad.body, { error: 'unauthorized' });
  });

  it('returns the signed-in user', async () => {
    const login = await h.login('uid-me');
    const res = await h.api('GET', '/me', { token: login.body.accessToken });
    assert.equal(res.status, 200);
    assert.equal(res.body.user.id, login.body.user.id);
  });

  it('rejects a tampered token', async () => {
    const login = await h.login('uid-tamper');
    const t: string = login.body.accessToken;
    const tampered = t.slice(0, -2) + (t.endsWith('AA') ? 'BB' : 'AA');
    assert.equal((await h.api('GET', '/me', { token: tampered })).status, 401);
  });

  it('rejects an expired token', async () => {
    const login = await h.login('uid-exp');
    const expired = jwt.sign({}, SECRET, {
      algorithm: 'HS256',
      subject: login.body.user.id,
      issuer: 'hivo-live',
      audience: 'hivo-app',
      expiresIn: -10,
    });
    assert.equal((await h.api('GET', '/me', { token: expired })).status, 401);
  });

  it('rejects an unsigned ("alg: none") token', async () => {
    const login = await h.login('uid-none');
    const enc = (o: object) => Buffer.from(JSON.stringify(o)).toString('base64url');
    const forged = `${enc({ alg: 'none', typ: 'JWT' })}.${enc({ sub: login.body.user.id, iss: 'hivo-live', aud: 'hivo-app', exp: 9999999999 })}.`;
    assert.equal((await h.api('GET', '/me', { token: forged })).status, 401);
  });

  it('rejects a token signed for another audience', async () => {
    const login = await h.login('uid-aud');
    const other = jwt.sign({}, SECRET, { algorithm: 'HS256', subject: login.body.user.id, issuer: 'hivo-live', audience: 'someone-else', expiresIn: 60 });
    assert.equal((await h.api('GET', '/me', { token: other })).status, 401);
  });

  it('stops working as soon as the account is suspended', async () => {
    const login = await h.login('uid-susp');
    assert.equal((await h.api('GET', '/me', { token: login.body.accessToken })).status, 200);
    await h.User.update({ status: 'suspended' }, { where: { firebaseUid: 'uid-susp' } });
    assert.equal((await h.api('GET', '/me', { token: login.body.accessToken })).status, 401);
  });
});

describe('POST /auth/refresh', () => {
  it('rotates the refresh token and issues a working access token', async () => {
    const login = await h.login('uid-rot');
    const res = await h.api('POST', '/auth/refresh', { body: { refreshToken: login.body.refreshToken } });
    assert.equal(res.status, 200);
    assert.notEqual(res.body.refreshToken, login.body.refreshToken);
    assert.equal((await h.api('GET', '/me', { token: res.body.accessToken })).status, 200);
  });

  it('treats a quick replay of the old token as a retry, not theft', async () => {
    const login = await h.login('uid-retry');
    const first = await h.api('POST', '/auth/refresh', { body: { refreshToken: login.body.refreshToken } });
    const replay = await h.api('POST', '/auth/refresh', { body: { refreshToken: login.body.refreshToken } });
    assert.equal(replay.status, 401);
    assert.deepEqual(replay.body, { error: 'refresh_race' });
    // The session created by the first refresh is still alive.
    const next = await h.api('POST', '/auth/refresh', { body: { refreshToken: first.body.refreshToken } });
    assert.equal(next.status, 200);
  });

  it('revokes the whole login when an old token comes back later (theft)', async () => {
    const login = await h.login('uid-theft');
    const first = await h.api('POST', '/auth/refresh', { body: { refreshToken: login.body.refreshToken } });
    // Pretend the rotation happened a minute ago.
    await h.RefreshToken.update({ revokedAt: new Date(Date.now() - 60_000) }, { where: { revokedAt: { [Op.ne]: null } } });
    const replay = await h.api('POST', '/auth/refresh', { body: { refreshToken: login.body.refreshToken } });
    assert.equal(replay.status, 401);
    assert.deepEqual(replay.body, { error: 'refresh_reuse_detected' });
    // The thief and the real owner are both logged out.
    const owner = await h.api('POST', '/auth/refresh', { body: { refreshToken: first.body.refreshToken } });
    assert.equal(owner.status, 401);
  });

  it('rejects unknown and expired refresh tokens', async () => {
    const unknown = await h.api('POST', '/auth/refresh', { body: { refreshToken: 'x'.repeat(43) } });
    assert.equal(unknown.status, 401);
    assert.deepEqual(unknown.body, { error: 'invalid_refresh_token' });

    const login = await h.login('uid-old-rt');
    await h.RefreshToken.update({ expiresAt: new Date(Date.now() - 1000) }, { where: {} });
    const expired = await h.api('POST', '/auth/refresh', { body: { refreshToken: login.body.refreshToken } });
    assert.equal(expired.status, 401);
  });

  it('refuses to refresh a suspended account', async () => {
    const login = await h.login('uid-susp-rt');
    await h.User.update({ status: 'suspended' }, { where: { firebaseUid: 'uid-susp-rt' } });
    const res = await h.api('POST', '/auth/refresh', { body: { refreshToken: login.body.refreshToken } });
    assert.equal(res.status, 403);
    assert.deepEqual(res.body, { error: 'account_unavailable' });
  });
});

describe('POST /auth/logout', () => {
  it('ends the session', async () => {
    const login = await h.login('uid-out');
    const out = await h.api('POST', '/auth/logout', { body: { refreshToken: login.body.refreshToken } });
    assert.equal(out.status, 204);
    const after = await h.api('POST', '/auth/refresh', { body: { refreshToken: login.body.refreshToken } });
    assert.equal(after.status, 401);
  });

  it('gives the same answer for an unknown token', async () => {
    const out = await h.api('POST', '/auth/logout', { body: { refreshToken: 'y'.repeat(43) } });
    assert.equal(out.status, 204);
  });
});
