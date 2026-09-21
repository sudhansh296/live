import { UniqueConstraintError } from 'sequelize';
import { AppError } from '../lib/errors';
import {
  MIN_AGE,
  ageOn,
  normalizeUsername,
  todayForAgeCheck,
  usernameProblem,
  validateBirthDate,
  validateCountry,
  validateDisplayName,
  validateUsername,
} from '../lib/profileRules';
import { User } from '../models';

export interface UsernameCheck {
  available: boolean;
  reason?: 'invalid' | 'reserved' | 'taken';
}

/** Live "is this name free?" check while the user types. */
export async function checkUsername(raw: string): Promise<UsernameCheck> {
  const problem = usernameProblem(raw);
  if (problem) return { available: false, reason: problem };
  const taken = await User.count({ where: { username: normalizeUsername(raw) } });
  return taken > 0 ? { available: false, reason: 'taken' } : { available: true };
}

export interface ProfileInput {
  displayName: string;
  username: string;
  /** 'YYYY-MM-DD' */
  birthDate: string;
  countryCode: string;
}

/**
 * First-time profile setup. The server, not the app, decides who is old enough:
 * an under-18 attempt is refused, the birth date is NOT stored, and the account can never retry.
 */
export async function completeProfile(user: User, input: ProfileInput): Promise<User> {
  if (user.profileCompletedAt) throw new AppError(409, 'profile_already_completed');
  if (user.ageCheckFailedAt) throw new AppError(403, 'age_restricted', 'birthDate');

  const displayName = validateDisplayName(input.displayName);
  const username = validateUsername(input.username);
  const birthDate = validateBirthDate(input.birthDate);
  const countryCode = validateCountry(input.countryCode);

  if (ageOn(birthDate, todayForAgeCheck()) < MIN_AGE) {
    await user.update({ ageCheckFailedAt: new Date() });
    throw new AppError(403, 'age_restricted', 'birthDate');
  }

  try {
    return await user.update({ displayName, username, birthDate, countryCode, profileCompletedAt: new Date() });
  } catch (err) {
    // The unique index is the real guard: two people can pass checkUsername() at the same moment.
    if (err instanceof UniqueConstraintError) throw new AppError(409, 'username_taken', 'username');
    throw err;
  }
}
