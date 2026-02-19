const fs = require('fs');
const path = require('path');

const packageJsonPath = path.join(__dirname, '../package.json');
const appJsonPath = path.join(__dirname, '../app.json');

const args = process.argv.slice(2);
const command = args[0];
const versionArg = args[1];

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function writeJson(filePath, data) {
  fs.writeFileSync(filePath, JSON.stringify(data, null, 2) + '\n');
}

if (command === 'show') {
  const packageJson = readJson(packageJsonPath);
  console.log(`Current version: ${packageJson.version}`);
} else if (command === 'set') {
  if (!versionArg) {
    console.error('Please provide a version number.');
    process.exit(1);
  }

  const packageJson = readJson(packageJsonPath);
  const appJson = readJson(appJsonPath);

  const oldVersion = packageJson.version;
  packageJson.version = versionArg;
  
  if (appJson.expo) {
    appJson.expo.version = versionArg;
  }

  writeJson(packageJsonPath, packageJson);
  writeJson(appJsonPath, appJson);

  console.log(`Updated version from ${oldVersion} to ${versionArg}`);
} else {
  // If no command is provided, assume it's a version set if argument looks like a version
  if (command && /^\d+\.\d+\.\d+/.test(command)) {
      const version = command;
      const packageJson = readJson(packageJsonPath);
      const appJson = readJson(appJsonPath);

      const oldVersion = packageJson.version;
      packageJson.version = version;
      
      if (appJson.expo) {
        appJson.expo.version = version;
      }

      writeJson(packageJsonPath, packageJson);
      writeJson(appJsonPath, appJson);

      console.log(`Updated version from ${oldVersion} to ${version}`);
  } else {
      console.log('Usage: node scripts/version.js [show|set <version>]');
  }
}
