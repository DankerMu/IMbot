import type { EventType } from "@imbot/wire";

export type OpenClawRequestFrame = {
  type: "req";
  id: string;
  method: string;
  params?: unknown;
};

export type OpenClawResponseFrame = {
  type: "res";
  id: string;
  ok: boolean;
  payload?: unknown;
  error?: {
    code?: string;
    message?: string;
  };
};

export type OpenClawEventFrame = {
  type: "event";
  event: string;
  payload?: unknown;
  payloadJSON?: string;
  seq?: number;
};

export type OpenClawFrame = OpenClawRequestFrame | OpenClawResponseFrame | OpenClawEventFrame;

export type OpenClawGatewayEventMessage = {
  event: string;
  payload: unknown;
};

export type OpenClawConnectResponse = {
  server?: {
    host?: string;
  };
  snapshot?: {
    sessionDefaults?: {
      mainSessionKey?: string;
    };
  };
};

export type OpenClawTranslatedEvent = {
  type: EventType;
  payload: unknown;
};
