#!/usr/bin/env node
import { parseArgs } from "node:util";
import { readFileSync, existsSync } from "node:fs";
import { join } from "node:path";
import { homedir } from "node:os";
import WebSocket from "ws";
import { formatEvent } from "./formatter";

const { values } = parseArgs({
  options: {
    relay: { type: "string" },
    token: { type: "string" },
    session: { type: "string" },
    help: { type: "boolean", short: "h" },
  },
  strict: false,
});

if (values.help) {
  console.log(`Usage: imbot-viewer [--relay <url>] [--token <token>] [--session <id>]

Options:
  --relay    Relay WebSocket URL (wss://relay.example.com)
  --token    Authentication token
  --session  Filter to a specific session ID
  --help     Show this help message

If --relay and --token are omitted, reads from ~/.imbot/companion.json`);
  process.exit(0);
}

function loadConfig(): { relayUrl: string; token: string } {
  if (values.relay && values.token) {
    return { relayUrl: values.relay as string, token: values.token as string };
  }

  const configPath = join(homedir(), ".imbot", "companion.json");
  if (!existsSync(configPath)) {
    console.error("Error: no --relay/--token provided and ~/.imbot/companion.json not found");
    process.exit(1);
  }

  const raw = JSON.parse(readFileSync(configPath, "utf8")) as Record<string, unknown>;
  const relayUrl = typeof raw.relay_url === "string" ? raw.relay_url : null;
  const token = typeof raw.token === "string" ? raw.token : null;

  if (!relayUrl || !token) {
    console.error("Error: companion.json missing relay_url or token");
    process.exit(1);
  }

  return { relayUrl, token };
}

const config = loadConfig();
const sessionFilter = values.session as string | undefined;

function toWsUrl(relayUrl: string): string {
  const url = relayUrl.replace(/\/$/, "");
  if (url.endsWith("/ws")) {
    return url;
  }
  if (!url.includes("/v1/")) {
    return `${url}/v1/ws`;
  }
  return url;
}

function validateRelayUrl(relayUrl: string): void {
  let parsed: URL;
  try {
    parsed = new URL(relayUrl);
  } catch {
    console.error(`Error: invalid relay URL: ${relayUrl}`);
    process.exit(1);
  }
  const isLocal = parsed.hostname === "localhost" || parsed.hostname === "127.0.0.1" || parsed.hostname === "::1";
  if (!isLocal && parsed.protocol !== "wss:" && parsed.protocol !== "https:") {
    console.error("Error: relay URL must use wss:// for non-localhost hosts");
    process.exit(1);
  }
}

validateRelayUrl(config.relayUrl);

let currentWs: WebSocket | null = null;
let announcedReady = false;

process.on("SIGINT", () => {
  console.error("\nClosing...");
  currentWs?.close();
  process.exit(0);
});

process.on("SIGTERM", () => {
  currentWs?.close();
  process.exit(0);
});

function connect(): void {
  const wsUrl = `${toWsUrl(config.relayUrl)}?token=${encodeURIComponent(config.token)}`;
  console.error(`Connecting to ${toWsUrl(config.relayUrl)}...`);

  const ws = new WebSocket(wsUrl);
  currentWs = ws;
  announcedReady = false;

  ws.on("open", () => {
    ws.send(JSON.stringify({ action: "auth", token: config.token }));
    if (sessionFilter) {
      ws.send(JSON.stringify({ action: "subscribe", session_id: sessionFilter }));
    }
    // A ping after auth/subscribe gives the CLI a real readiness barrier because the relay
    // processes client messages in order and replies with pong after prior actions were handled.
    ws.send(JSON.stringify({ action: "ping" }));
  });

  ws.on("message", (data: WebSocket.RawData) => {
    try {
      const msg = JSON.parse(data.toString()) as Record<string, unknown>;
      if (msg.type === "pong") {
        if (!announcedReady) {
          announcedReady = true;
          console.error("Connected. Streaming events...\n");
        }
      } else if (msg.type === "event") {
        const sessionId = msg.session_id as string;
        if (sessionFilter && sessionId !== sessionFilter) return;
        const prefix = sessionFilter ? "" : `[${sessionId.slice(0, 8)}] `;
        const formatted = formatEvent(msg);
        if (formatted) {
          process.stdout.write(`${prefix}${formatted}\n`);
        }
      } else if (msg.type === "status") {
        const sessionId = msg.session_id as string;
        if (sessionFilter && sessionId !== sessionFilter) return;
        const prefix = sessionFilter ? "" : `[${sessionId.slice(0, 8)}] `;
        process.stdout.write(`${prefix}--- ${msg.status} ---\n`);
      } else if (msg.type === "error") {
        console.error(`Server error: ${msg.message}`);
      }
    } catch {
      // ignore non-JSON
    }
  });

  ws.on("close", (code: number) => {
    console.error(`\nDisconnected (code ${code}). Reconnecting in 3s...`);
    setTimeout(connect, 3000);
  });

  ws.on("error", (err: Error) => {
    console.error(`WebSocket error: ${err.message}`);
  });
}

connect();
