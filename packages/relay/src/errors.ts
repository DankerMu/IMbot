import { ERROR_HTTP_STATUS, type ErrorCode } from "@imbot/wire";

export class RelayError extends Error {
  readonly code: ErrorCode;
  readonly statusCode: number;

  constructor(code: ErrorCode, message?: string) {
    super(message ?? code);
    this.code = code;
    this.statusCode = ERROR_HTTP_STATUS[code];
    this.name = "RelayError";
  }
}

export function isRelayError(error: unknown): error is RelayError {
  return error instanceof RelayError;
}

