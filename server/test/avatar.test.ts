import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { after, before, beforeEach, describe, it } from 'node:test';
import sharp from 'sharp';
import * as h from './helpers';

const AVATARS = path.join(process.env.UPLOADS_DIR as string, 'avatars');
const AVATAR_URL = /^\/media\/avatars\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.webp$/;

before(() => h.start());
after(() => h.stop());
beforeEach(async () => {
  await h.resetDb();
  await fs.rm(AVATARS, { recursive: true, force: true });
});

const RED = { r: 200, g: 30, b: 30 };

function solid(width: number, height: number) {
  return sharp({ create: { width, height, channels: 3, background: RED } });
}

async function upload(
  token: string | undefined,
  file: Buffer | null,
  options: { field?: string; filename?: string; type?: string } = {},
) {
  const form = new FormData();
  if (file) {
    form.append(options.field ?? 'avatar', new Blob([new Uint8Array(file)], { type: options.type ?? 'image/png' }), options.filename ?? 'photo.png');
  }
  const headers: Record<string, string> = {};
  if (token) headers.authorization = `Bearer ${token}`;
  const res = await h.rawFetch('/me/avatar', { method: 'POST', headers, body: form });
  const text = await res.text();
  return { status: res.status, body: text ? JSON.parse(text) : null };
}

async function download(url: string): Promise<{ status: number; type: string | null; cache: string | null; bytes: Buffer }> {
  const res = await h.rawFetch(url);
  return { status: res.status, type: res.headers.get('content-type'), cache: res.headers.get('cache-control'), bytes: Buffer.from(await res.arrayBuffer()) };
}

async function filesOnDisk(): Promise<string[]> {
  try {
    return await fs.readdir(AVATARS);
  } catch {
    return [];
  }
}

describe('POST /me/avatar', () => {
  it('needs a login', async () => {
    const res = await upload(undefined, await solid(100, 100).png().toBuffer());
    assert.equal(res.status, 401);
  });

  it('turns any photo into a public 512x512 WebP', async () => {
    const token = await h.signIn('a-ok');
    const res = await upload(token, await solid(1200, 800).jpeg().toBuffer(), { type: 'image/jpeg', filename: 'me.jpg' });
    assert.equal(res.status, 200);
    assert.match(res.body.user.avatarUrl, AVATAR_URL);

    // Anyone can fetch it without logging in (profile photos are public).
    const file = await download(res.body.user.avatarUrl);
    assert.equal(file.status, 200);
    assert.equal(file.type, 'image/webp');
    assert.match(file.cache ?? '', /immutable/);
    const meta = await sharp(file.bytes).metadata();
    assert.deepEqual([meta.format, meta.width, meta.height], ['webp', 512, 512]);
  });

  it('removes location and other hidden data from the photo', async () => {
    const token = await h.signIn('a-exif');
    const original = await solid(600, 600)
      .jpeg()
      .withExif({
        IFD0: { ImageDescription: 'SECRET-LOCATION-TEXT' },
        IFD3: { GPSLatitudeRef: 'N', GPSLatitude: '28/1 36/1 0/1', GPSLongitudeRef: 'E', GPSLongitude: '77/1 12/1 0/1' },
      })
      .toBuffer();
    assert.ok(original.includes(Buffer.from('SECRET-LOCATION-TEXT')), 'the test photo must carry the secret text');

    const res = await upload(token, original, { type: 'image/jpeg', filename: 'gps.jpg' });
    assert.equal(res.status, 200);
    const stored = await download(res.body.user.avatarUrl);
    assert.ok(!stored.bytes.includes(Buffer.from('SECRET-LOCATION-TEXT')), 'hidden text must be gone');
    const meta = await sharp(stored.bytes).metadata();
    assert.equal(meta.exif, undefined, 'no EXIF block may remain');
  });

  it('applies the camera rotation before dropping it', async () => {
    const token = await h.signIn('a-rotate');
    const redHalf = await solid(200, 200).png().toBuffer();
    // Stored 400x200: red on the left, blue on the right, tagged "rotate 90 degrees clockwise".
    const photo = await sharp({ create: { width: 400, height: 200, channels: 3, background: { r: 0, g: 0, b: 255 } } })
      .composite([{ input: redHalf, left: 0, top: 0 }])
      .jpeg()
      .withMetadata({ orientation: 6 })
      .toBuffer();
    assert.equal((await sharp(photo).metadata()).orientation, 6);

    const res = await upload(token, photo, { type: 'image/jpeg', filename: 'rot.jpg' });
    const shown = await sharp((await download(res.body.user.avatarUrl)).bytes).raw().toBuffer({ resolveWithObject: true });
    const pixel = (x: number, y: number) => {
      const i = (y * shown.info.width + x) * shown.info.channels;
      return [shown.data[i] ?? 0, shown.data[i + 1] ?? 0, shown.data[i + 2] ?? 0];
    };
    const top = pixel(256, 60);
    const bottom = pixel(256, 450);
    assert.ok((top[0] ?? 0) > 150 && (top[2] ?? 255) < 100, `top should be red, got ${top}`);
    assert.ok((bottom[2] ?? 0) > 150 && (bottom[0] ?? 255) < 100, `bottom should be blue, got ${bottom}`);
  });

  it('replaces the old photo and deletes its file', async () => {
    const token = await h.signIn('a-replace');
    const first = await upload(token, await solid(300, 300).png().toBuffer());
    const second = await upload(token, await solid(400, 300).png().toBuffer());
    assert.notEqual(first.body.user.avatarUrl, second.body.user.avatarUrl);
    assert.equal((await download(first.body.user.avatarUrl)).status, 404, 'old photo must be gone');
    assert.equal((await download(second.body.user.avatarUrl)).status, 200);
    assert.equal((await filesOnDisk()).length, 1);
  });

  it('refuses a request with no file or the wrong field name', async () => {
    const token = await h.signIn('a-nofile');
    const none = await upload(token, null);
    assert.equal(none.status, 400);
    assert.deepEqual(none.body, { error: 'file_required', field: 'avatar' });
    const wrong = await upload(token, await solid(50, 50).png().toBuffer(), { field: 'photo' });
    assert.equal(wrong.status, 400);
    assert.equal((await filesOnDisk()).length, 0);
  });

  it('refuses files that are not really pictures, whatever their name says', async () => {
    const token = await h.signIn('a-fake');
    const text = await upload(token, Buffer.from('<?php echo 1; ?> not an image at all'), { filename: 'evil.jpg', type: 'image/jpeg' });
    assert.equal(text.status, 400);
    assert.deepEqual(text.body, { error: 'image_invalid', field: 'avatar' });
    const html = await upload(token, Buffer.from('<html><script>alert(1)</script></html>'), { filename: 'x.png', type: 'image/png' });
    assert.equal(html.status, 400);
    assert.equal((await filesOnDisk()).length, 0);
  });

  it('refuses picture types we do not accept', async () => {
    const token = await h.signIn('a-gif');
    const gif = await solid(40, 40).gif().toBuffer();
    const res = await upload(token, gif, { filename: 'a.gif', type: 'image/gif' });
    assert.equal(res.status, 400);
    assert.deepEqual(res.body, { error: 'image_unsupported', field: 'avatar' });
  });

  it('refuses a file over 5 MB', async () => {
    const token = await h.signIn('a-big');
    const res = await upload(token, Buffer.alloc(5 * 1024 * 1024 + 1024, 1), { filename: 'big.jpg', type: 'image/jpeg' });
    assert.equal(res.status, 413);
    assert.deepEqual(res.body, { error: 'file_too_large', field: 'avatar' });
    assert.equal((await filesOnDisk()).length, 0);
  });

  it('refuses a "decompression bomb" (small file, gigantic picture)', async () => {
    const token = await h.signIn('a-bomb');
    const bomb = await solid(5300, 5300).png({ compressionLevel: 9 }).toBuffer(); // ~28 megapixels of one colour
    assert.ok(bomb.length < 5 * 1024 * 1024, 'the bomb must be small enough to be uploaded');
    const res = await upload(token, bomb, { filename: 'bomb.png' });
    assert.equal(res.status, 400);
    assert.deepEqual(res.body, { error: 'image_invalid', field: 'avatar' });
    assert.equal((await filesOnDisk()).length, 0);
  });
});

describe('DELETE /me/avatar', () => {
  it('needs a login', async () => {
    assert.equal((await h.api('DELETE', '/me/avatar')).status, 401);
  });

  it('removes the photo and its file, and can be repeated', async () => {
    const token = await h.signIn('a-del');
    const up = await upload(token, await solid(200, 200).png().toBuffer());
    const url: string = up.body.user.avatarUrl;
    const first = await h.api('DELETE', '/me/avatar', { token });
    assert.equal(first.status, 200);
    assert.equal(first.body.user.avatarUrl, null);
    assert.equal((await download(url)).status, 404);
    assert.equal((await filesOnDisk()).length, 0);
    const again = await h.api('DELETE', '/me/avatar', { token });
    assert.equal(again.status, 200);
  });
});

describe('GET /media/avatars/:file', () => {
  it('only serves files that look like our own random names', async () => {
    const bad = [
      '..%2f..%2fpackage.json',
      '%2e%2e%2fsecrets%2fanything',
      'package.json',
      'x.webp',
      '00000000-0000-0000-0000-000000000000.webp', // right shape, but no such file
      '00000000-0000-0000-0000-000000000000.png',
    ];
    for (const name of bad) {
      const res = await h.rawFetch(`/media/avatars/${name}`);
      assert.equal(res.status, 404, `${name} must be 404`);
      assert.equal(res.headers.get('cache-control')?.includes('immutable') ?? false, false, '404 must never be cached forever');
    }
  });
});

describe('profile photo and the rest of the account', () => {
  it('shows up in /me, and survives completing the profile', async () => {
    const token = await h.signIn('a-me');
    const up = await upload(token, await solid(300, 300).png().toBuffer());
    const me = await h.api('GET', '/me', { token });
    assert.equal(me.body.user.avatarUrl, up.body.user.avatarUrl);
    const saved = await h.api('PUT', '/me/profile', {
      token,
      body: { displayName: 'Photo Person', username: 'photo_person', birthDate: '1990-01-15', countryCode: 'IN' },
    });
    assert.equal(saved.status, 200);
    assert.equal(saved.body.user.avatarUrl, up.body.user.avatarUrl);
  });

  it('reports an under-18 account as ageRestricted', async () => {
    const token = await h.signIn('a-child');
    const soon = new Date(Date.now() + 14 * 3600_000);
    const child = new Date(Date.UTC(soon.getUTCFullYear() - 18, soon.getUTCMonth(), soon.getUTCDate() + 5)).toISOString().slice(0, 10);
    const refused = await h.api('PUT', '/me/profile', {
      token,
      body: { displayName: 'Kid', username: 'kid_account', birthDate: child, countryCode: 'IN' },
    });
    assert.equal(refused.status, 403);
    const me = await h.api('GET', '/me', { token });
    assert.equal(me.body.user.ageRestricted, true);
    assert.equal(me.body.user.profileCompleted, false);
  });
});
