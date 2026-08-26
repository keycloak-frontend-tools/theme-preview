const fs = require("node:fs");
const path = require("node:path");

const source = path.join(__dirname, "..", "target", "theme-preview.jar");
const destinationDir = path.join(__dirname, "..", "lib");
const destination = path.join(destinationDir, "theme-preview.jar");

if (!fs.existsSync(source)) {
  console.error(`JAR not found: ${source}`);
  console.error("Run 'mvn package' first.");
  process.exit(1);
}

fs.mkdirSync(destinationDir, { recursive: true });
fs.copyFileSync(source, destination);
console.log(`Copied ${source} -> ${destination}`);
