/** An expected error that is safe to show to the client: only the short `code` (and a field name) is sent, never internals. */
export class AppError extends Error {
  readonly status: number;
  readonly code: string;
  /** Which request field caused a validation error, so the app can highlight it. */
  readonly field: string | undefined;

  constructor(status: number, code: string, field?: string) {
    super(code);
    this.name = 'AppError';
    this.status = status;
    this.code = code;
    this.field = field;
  }
}
