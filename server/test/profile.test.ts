import assert from 'node:assert/strict';
import { after, before, beforeEach, describe, it } from 'node:test';
import * as h from './helpers';

before(() => h.start());
after(() => h.stop());
beforeEach(() => h.resetDb());

const DAY = 24 * 60 * 60 * 1000;

/** A birth date that makes someone exactly 18 today (server "today" = UTC+14), shifted by `days`. */
function eighteenAgo(days = 0): string {
  const t = new Date(Date.now() + 14 * 60 * 60 * 1000);
  return new Date(Date.UTC(t.getUTCFullYear() - 18, t.getUTCMonth(), t.getUTCDate() + days)).toISOString().slice(0, 10);
}
const ADULT = () => eighteenAgo(-2); // turned 18 two days ago
const CHILD = () => eighteenAgo(3); // turns 18 in three days

function valid(overrides: Record<string, unknown> = {}) {
  return { displayName: 'Sudhanshu', username: 'sudha_live', birthDate: ADULT(), countryCode: 'IN', ...overrides };
}

async function save(token: string, body: unknown) {
  return h.api('PUT', '/me/profile', { token, body });
}

describe('GET /profile/username-available', () => {
  it('needs a login', async () => {
    assert.equal((await h.api('GET', '/profile/username-available?u=abc')).status, 401);
  });

  it('says yes to a free name and ignores letter case', async () => {
    const token = await h.signIn('u-free');
    const res = await h.api('GET', '/profile/username-available?u=Sudha_Live', { token });
    assert.equal(res.status, 200);
    assert.deepEqual(res.body, { available: true });
  });

  it('says a taken name is taken, in any letter case', async () => {
    const a = await h.signIn('u-a');
    assert.equal((await save(a, valid({ username: 'taken_name' }))).status, 200);
    const b = await h.signIn('u-b');
    const res = await h.api('GET', '/profile/username-available?u=TAKEN_Name', { token: b });
    assert.deepEqual(res.body, { available: false, reason: 'taken' });
  });

  it('rejects names in the wrong shape', async () => {
    const token = await h.signIn('u-shape');
    for (const bad of ['ab', 'a', '.abc', 'abc.', 'a..b', 'has space', 'bad!name', 'x'.repeat(21), '_abc', 'abc_']) {
      const res = await h.api('GET', `/profile/username-available?u=${encodeURIComponent(bad)}`, { token });
      assert.deepEqual(res.body, { available: false, reason: 'invalid' }, `"${bad}" should be invalid`);
    }
  });

  it('keeps reserved words for the team', async () => {
    const token = await h.signIn('u-res');
    for (const word of ['admin', 'Hivo', 'support', 'moderator']) {
      const res = await h.api('GET', `/profile/username-available?u=${word}`, { token });
      assert.deepEqual(res.body, { available: false, reason: 'reserved' }, `"${word}" should be reserved`);
    }
  });

  it('needs the u parameter', async () => {
    const token = await h.signIn('u-noparam');
    const res = await h.api('GET', '/profile/username-available', { token });
    assert.equal(res.status, 400);
    assert.deepEqual(res.body, { error: 'invalid_request', field: 'u' });
  });
});

describe('PUT /me/profile', () => {
  it('needs a login', async () => {
    assert.equal((await h.api('PUT', '/me/profile', { body: valid() })).status, 401);
  });

  it('saves a valid profile and cleans the values', async () => {
    const token = await h.signIn('p-ok');
    const res = await save(token, valid({ displayName: '  Sudha\n  Kumar  ', username: 'Sudha_Live', countryCode: 'in' }));
    assert.equal(res.status, 200);
    assert.equal(res.body.user.displayName, 'Sudha Kumar');
    assert.equal(res.body.user.username, 'sudha_live');
    assert.equal(res.body.user.countryCode, 'IN');
    assert.equal(res.body.user.profileCompleted, true);

    const row = await h.User.findOne({ where: { firebaseUid: 'p-ok' } });
    assert.equal(row?.birthDate, ADULT());
    assert.ok(row?.profileCompletedAt);
    assert.equal(row?.ageCheckFailedAt, null);
  });

  it('never sends the birth date back, not even to the owner', async () => {
    const token = await h.signIn('p-private');
    const res = await save(token, valid());
    const me = await h.api('GET', '/me', { token });
    assert.equal(me.body.user.profileCompleted, true);
    assert.ok(!JSON.stringify(res.body).includes(ADULT()));
    assert.ok(!JSON.stringify(me.body).includes(ADULT()));
    assert.ok(!('birthDate' in me.body.user));
  });

  it('accepts someone who turns 18 today', async () => {
    const token = await h.signIn('p-18today');
    assert.equal((await save(token, valid({ birthDate: eighteenAgo(0) }))).status, 200);
  });

  it('refuses under 18, does not keep the date, and does not allow a retry', async () => {
    const token = await h.signIn('p-child');
    const res = await save(token, valid({ birthDate: CHILD() }));
    assert.equal(res.status, 403);
    assert.deepEqual(res.body, { error: 'age_restricted', field: 'birthDate' });

    const row = await h.User.findOne({ where: { firebaseUid: 'p-child' } });
    assert.equal(row?.birthDate, null, 'the under-18 birth date must not be stored');
    assert.equal(row?.profileCompletedAt, null);
    assert.ok(row?.ageCheckFailedAt);

    // Trying again with an adult date is refused too.
    const retry = await save(token, valid({ birthDate: ADULT() }));
    assert.equal(retry.status, 403);
    assert.equal(retry.body.error, 'age_restricted');
    assert.equal((await h.User.findOne({ where: { firebaseUid: 'p-child' } }))?.profileCompletedAt, null);
  });

  it('refuses a username somebody already has (any letter case)', async () => {
    const a = await h.signIn('p-first');
    assert.equal((await save(a, valid({ username: 'only_one' }))).status, 200);
    const b = await h.signIn('p-second');
    const res = await save(b, valid({ username: 'ONLY_ONE' }));
    assert.equal(res.status, 409);
    assert.deepEqual(res.body, { error: 'username_taken', field: 'username' });
    assert.equal((await h.User.findOne({ where: { firebaseUid: 'p-second' } }))?.profileCompletedAt, null);
  });

  it('lets only one of two simultaneous requests win the same username', async () => {
    const a = await h.signIn('p-race-a');
    const b = await h.signIn('p-race-b');
    const [ra, rb] = await Promise.all([save(a, valid({ username: 'race_name' })), save(b, valid({ username: 'race_name' }))]);
    assert.deepEqual([ra.status, rb.status].sort(), [200, 409]);
    assert.equal(await h.User.count({ where: { username: 'race_name' } }), 1);
  });

  it('validates the display name', async () => {
    const token = await h.signIn('p-name');
    const cases: Array<[string, boolean]> = [
      ['A', false],
      ['x'.repeat(31), false],
      ['​​​', false], // only invisible characters
      ['🔥', false], // one emoji = one character
      ['🔥🔥', true], // two emoji are fine
    ];
    for (const [name, ok] of cases) {
      const res = await save(token, valid({ displayName: name, username: `n${Math.random().toString(36).slice(2, 10)}` }));
      if (ok) {
        assert.equal(res.status, 200);
        return;
      }
      assert.equal(res.status, 400, `"${name}" should be refused`);
      assert.deepEqual(res.body, { error: 'display_name_invalid', field: 'displayName' });
    }
  });

  it('validates the username', async () => {
    const token = await h.signIn('p-user');
    const bad = await save(token, valid({ username: 'no spaces' }));
    assert.deepEqual(bad.body, { error: 'username_invalid', field: 'username' });
    const reserved = await save(token, valid({ username: 'admin' }));
    assert.deepEqual(reserved.body, { error: 'username_reserved', field: 'username' });
  });

  it('validates the birth date', async () => {
    const token = await h.signIn('p-date');
    const future = new Date(Date.now() + 400 * DAY).toISOString().slice(0, 10);
    for (const bad of ['2001-02-30', 'abc', '', '2001/05/12', '1800-01-01', future]) {
      const res = await save(token, valid({ birthDate: bad }));
      assert.equal(res.status, 400, `"${bad}" should be refused`);
      assert.deepEqual(res.body, { error: 'birth_date_invalid', field: 'birthDate' });
    }
    // A bad format must not count as an under-18 attempt.
    assert.equal((await h.User.findOne({ where: { firebaseUid: 'p-date' } }))?.ageCheckFailedAt, null);
  });

  it('validates the country', async () => {
    const token = await h.signIn('p-country');
    for (const bad of ['IND', '1N', 'I', '']) {
      const res = await save(token, valid({ countryCode: bad }));
      assert.equal(res.status, 400, `"${bad}" should be refused`);
    }
  });

  it('refuses unknown or missing fields', async () => {
    const token = await h.signIn('p-shape');
    const extra = await save(token, { ...valid(), isAdmin: true });
    assert.equal(extra.status, 400);
    assert.equal(extra.body.error, 'invalid_request');
    const { username: _dropped, ...withoutUsername } = valid();
    const missing = await save(token, withoutUsername);
    assert.deepEqual(missing.body, { error: 'invalid_request', field: 'username' });
  });

  it('cannot be run twice', async () => {
    const token = await h.signIn('p-twice');
    assert.equal((await save(token, valid())).status, 200);
    const again = await save(token, valid({ username: 'another_one' }));
    assert.equal(again.status, 409);
    assert.deepEqual(again.body, { error: 'profile_already_completed' });
  });
});
