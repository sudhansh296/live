import type { z } from 'zod';
import { AppError } from './errors';

/** Parses a request body with a zod schema. Unknown fields are rejected by the schemas (.strict()). */
export function parseBody<T extends z.ZodType>(schema: T, body: unknown): z.infer<T> {
  const result = schema.safeParse(body);
  if (!result.success) {
    const first = result.error.issues[0]?.path[0];
    throw new AppError(400, 'invalid_request', typeof first === 'string' ? first : undefined);
  }
  return result.data;
}
