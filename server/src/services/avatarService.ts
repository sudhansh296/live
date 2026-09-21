import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
import { env } from '../config/env';
import { AppError } from '../lib/errors';
import type { User } from '../models';

export const AVATAR_DIR = path.join(env.uploadsDir, 'avatars');
export const MAX_AVATAR_UPLOAD_BYTES = 5 * 1024 * 1024;

const AVATAR_SIZE = 512;
// Refuse "decompression bombs": a tiny file that expands to a gigantic picture.
const MAX_INPUT_PIXELS = 25_000_000;
const ALLOWED_FORMATS = new Set(['jpeg', 'png', 'webp']);

/** Stored names are always <random uuid>.webp. Anything else in a URL is refused, so paths cannot be guessed or escaped. */
export const AVATAR_FILE_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.webp$/;

/**
 * Turns whatever the user uploaded into a clean 512x512 WebP:
 * the real file type is checked by decoding (not by the name or the declared type), the camera's rotation is
 * applied, the picture is cropped square, and ALL metadata (including GPS location) is dropped.
 */
async function toAvatarWebp(input: Buffer): Promise<Buffer> {
  let format: string | undefined;
  try {
    format = (await sharp(input, { limitInputPixels: MAX_INPUT_PIXELS }).metadata()).format;
  } catch {
    throw new AppError(400, 'image_invalid', 'avatar');
  }
  if (!format || !ALLOWED_FORMATS.has(format)) throw new AppError(400, 'image_unsupported', 'avatar');

  try {
    return await sharp(input, { limitInputPixels: MAX_INPUT_PIXELS, failOn: 'error' })
      .rotate() // apply the EXIF orientation, then forget it
      .resize(AVATAR_SIZE, AVATAR_SIZE, { fit: 'cover', position: 'centre' })
      .webp({ quality: 80 }) // sharp writes no metadata unless asked to
      .toBuffer();
  } catch {
    throw new AppError(400, 'image_invalid', 'avatar');
  }
}

async function deleteFile(key: string | null | undefined): Promise<void> {
  if (key && AVATAR_FILE_PATTERN.test(key)) await fs.rm(path.join(AVATAR_DIR, key), { force: true });
}

export async function setAvatar(user: User, upload: Buffer): Promise<User> {
  const webp = await toAvatarWebp(upload);
  await fs.mkdir(AVATAR_DIR, { recursive: true });

  const key = `${crypto.randomUUID()}.webp`;
  await fs.writeFile(path.join(AVATAR_DIR, key), webp, { flag: 'wx' });

  const previous = user.avatarKey;
  try {
    await user.update({ avatarKey: key });
  } catch (err) {
    await deleteFile(key); // do not leave an orphan file behind
    throw err;
  }
  await deleteFile(previous);
  return user;
}

export async function removeAvatar(user: User): Promise<User> {
  const previous = user.avatarKey;
  if (previous) {
    await user.update({ avatarKey: null });
    await deleteFile(previous);
  }
  return user;
}

export async function avatarFileExists(file: string): Promise<boolean> {
  if (!AVATAR_FILE_PATTERN.test(file)) return false;
  try {
    await fs.access(path.join(AVATAR_DIR, file));
    return true;
  } catch {
    return false;
  }
}
