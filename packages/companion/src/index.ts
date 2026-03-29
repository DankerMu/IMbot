import { PROVIDERS, type Provider } from "@imbot/wire";

export const COMPANION_SUPPORTED_PROVIDERS = PROVIDERS;

export function supportsInteractiveProvider(
  provider: Provider
): provider is Exclude<Provider, "openclaw"> {
  return provider === "claude" || provider === "book";
}
