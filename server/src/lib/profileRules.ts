import { AppError } from './errors';

export const MIN_AGE = 18;
const MAX_AGE = 120;

// The earliest time zone on Earth is UTC+14. Using it for "today" means nobody is refused a few hours
// before their 18th birthday just because the server clock is in UTC. (The check stays about a day loose at most.)
const TODAY_OFFSET_MS = 14 * 60 * 60 * 1000;

// Words nobody may take: they could be used to look like staff or break routes.
export const RESERVED_USERNAMES: ReadonlySet<string> = new Set([
  'admin', 'administrator', 'support', 'help', 'hivo', 'hivolive', 'official', 'staff', 'moderator', 'mod',
  'system', 'root', 'null', 'undefined', 'api', 'www', 'mail', 'email', 'security', 'team', 'live',
  'everyone', 'anonymous', 'deleted', 'me', 'user', 'users', 'profile', 'settings',
]);

// 3-20 characters: lowercase letters, digits, "." and "_"; must start and end with a letter or digit.
const USERNAME_PATTERN = /^[a-z0-9][a-z0-9._]{1,18}[a-z0-9]$/;

// Control characters, zero-width characters and "bidi override" characters (used to fake names).
// eslint-disable-next-line no-control-regex
const HIDDEN_CHARS = /[\u0000-\u001F\u007F-\u009F​-‏‪-‮⁦-⁩﻿]/g;

export function normalizeDisplayName(input: string): string {
  return input.normalize('NFC').replace(HIDDEN_CHARS, '').replace(/\s+/g, ' ').trim();
}

export function validateDisplayName(input: string): string {
  const name = normalizeDisplayName(input);
  const length = [...name].length; // count characters, not UTF-16 units, so emoji count as one
  if (length < 2 || length > 30) throw new AppError(400, 'display_name_invalid', 'displayName');
  return name;
}

export type UsernameProblem = 'invalid' | 'reserved';

export function usernameProblem(input: string): UsernameProblem | null {
  const name = input.trim().toLowerCase();
  if (!USERNAME_PATTERN.test(name) || name.includes('..')) return 'invalid';
  if (RESERVED_USERNAMES.has(name)) return 'reserved';
  return null;
}

export function normalizeUsername(input: string): string {
  return input.trim().toLowerCase();
}

export function validateUsername(input: string): string {
  const problem = usernameProblem(input);
  if (problem) throw new AppError(400, `username_${problem}`, 'username');
  return normalizeUsername(input);
}

export function validateCountry(input: string): string {
  const code = input.trim().toUpperCase();
  if (!/^[A-Z]{2}$/.test(code)) throw new AppError(400, 'country_invalid', 'countryCode');
  return code;
}

/** Whole years between a 'YYYY-MM-DD' birth date and `today`. */
export function ageOn(birthDate: string, today: Date): number {
  const [y = 0, m = 0, d = 0] = birthDate.split('-').map(Number);
  let age = today.getUTCFullYear() - y;
  const month = today.getUTCMonth() + 1;
  if (month < m || (month === m && today.getUTCDate() < d)) age -= 1;
  return age;
}

export function todayForAgeCheck(now: Date = new Date()): Date {
  return new Date(now.getTime() + TODAY_OFFSET_MS);
}

/** Checks the format and that the date really exists, is not in the future and is not absurdly old. */
export function validateBirthDate(input: string, now: Date = new Date()): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(input);
  if (!match) throw new AppError(400, 'birth_date_invalid', 'birthDate');
  const [y, m, d] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const date = new Date(Date.UTC(y, m - 1, d));
  const real = date.getUTCFullYear() === y && date.getUTCMonth() === m - 1 && date.getUTCDate() === d;
  const today = todayForAgeCheck(now);
  if (!real || date.getTime() > today.getTime() || ageOn(input, today) > MAX_AGE) {
    throw new AppError(400, 'birth_date_invalid', 'birthDate');
  }
  return input;
}
