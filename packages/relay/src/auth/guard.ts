import { timingSafeEqual } from "node:crypto";

import type { FastifyReply, FastifyRequest } from "fastify";

import type { RelayConfig } from "../config";

function toBuffer(value: string): Buffer {
  return Buffer.from(value, "utf8");
}

export function isValidToken(candidate: string | null | undefined, expected: string): boolean {
  if (!candidate) {
    return false;
  }

  const candidateBuffer = toBuffer(candidate);
  const expectedBuffer = toBuffer(expected);

  if (candidateBuffer.length !== expectedBuffer.length) {
    return false;
  }

  return timingSafeEqual(candidateBuffer, expectedBuffer);
}

export function extractBearerToken(
  authorizationHeader: string | string[] | undefined
): string | null {
  if (typeof authorizationHeader !== "string") {
    return null;
  }

  const [scheme, token, extra] = authorizationHeader.trim().split(/\s+/);
  if (scheme !== "Bearer" || !token || extra) {
    return null;
  }

  return token;
}

export function getWsQueryParam(url: string | undefined, name: string): string | null {
  if (!url) {
    return null;
  }

  return new URL(url, "http://localhost").searchParams.get(name);
}

export function createAuthGuard(config: RelayConfig) {
  return async function authGuard(request: FastifyRequest, reply: FastifyReply) {
    const token = extractBearerToken(request.headers.authorization);

    if (!isValidToken(token, config.staticToken)) {
      return reply.code(401).send({ error: "unauthenticated" });
    }
  };
}
