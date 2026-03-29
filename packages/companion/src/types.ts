export type LoggerLike = {
  readonly debug?: (...args: unknown[]) => void;
  readonly info?: (...args: unknown[]) => void;
  readonly warn?: (...args: unknown[]) => void;
  readonly error?: (...args: unknown[]) => void;
};

export class CompanionError extends Error {
  constructor(
    readonly code: string,
    message: string
  ) {
    super(message);
    this.name = "CompanionError";
  }
}

export function toCompanionError(
  error: unknown,
  fallbackCode: string,
  fallbackMessage: string
): CompanionError {
  if (error instanceof CompanionError) {
    return error;
  }

  if (error instanceof Error) {
    return new CompanionError(fallbackCode, error.message || fallbackMessage);
  }

  return new CompanionError(fallbackCode, fallbackMessage);
}
