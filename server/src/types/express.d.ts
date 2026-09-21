import type { User } from '../models';

declare global {
  namespace Express {
    interface Request {
      /** Set by requireAuth for authenticated routes. */
      user?: User;
    }
  }
}
