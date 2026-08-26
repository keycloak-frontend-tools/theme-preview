#!/usr/bin/env node

const fs = require("node:fs");
const path = require("node:path");
const { jarPath } = require("..");

const keycloakHome = process.env.KEYCLOAK_HOME || process.argv[2];

if (!keycloakHome) {
  console.error("Usage: theme-preview-install [KEYCLOAK_HOME]");
  console.error("       KEYCLOAK_HOME=/path/to/keycloak theme-preview-install");
  process.exit(1);
}

const providersDir = path.join(keycloakHome, "providers");
const destination = path.join(providersDir, "theme-preview.jar");

if (!fs.existsSync(providersDir)) {
  console.error(`Providers directory not found: ${providersDir}`);
  process.exit(1);
}

fs.copyFileSync(jarPath, destination);
console.log(`Installed ${destination}`);
console.log("Run 'bin/kc.sh build' in your Keycloak distribution, then start the server.");
console.log("Open http://localhost:8080/realms/master/theme-preview/ to preview themes.");
