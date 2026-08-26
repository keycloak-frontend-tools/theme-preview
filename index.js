const path = require("node:path");

/** Absolute path to the bundled Keycloak provider JAR. */
const jarPath = path.join(__dirname, "lib", "theme-preview.jar");

module.exports = {
  jarPath,
};
