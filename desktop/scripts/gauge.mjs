// Builds the gauge painter of :gauge as JavaScript (:gauge-js) for the desktop: every surface draws the
// drop with the same code (docs/04). Vite imports it from the Gradle output (see vite.config.ts).
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
execFileSync(gradlew, [':gauge-js:jsProductionLibraryCompileSync', '-q'], { cwd: root, stdio: 'inherit', shell: process.platform === 'win32' });
