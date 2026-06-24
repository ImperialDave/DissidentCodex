#!/usr/bin/env node
/**
 * Definitive check: are blockedUsers + feedHiddenTopics rules live in production?
 * Run from codex-web: node ../scripts/verify-rules-published.mjs
 *
 * If hiddenTopics passes but feedHiddenTopics fails, the full rules file was NOT
 * published (only the old rules are active).
 */
import { readFileSync } from "fs";
import { resolve, dirname } from "path";
import { fileURLToPath } from "url";
const __dirname = dirname(fileURLToPath(import.meta.url));
const webRoot = resolve(__dirname, "../codex-web");
const envPath = resolve(webRoot, ".env.local");
// Resolve firebase from codex-web's node_modules when run from repo root.
const { createRequire } = await import("module");
const require = createRequire(resolve(webRoot, "package.json"));
const firebaseAppPath = require.resolve("firebase/app");
const firebaseAuthPath = require.resolve("firebase/auth");
const firebaseFsPath = require.resolve("firebase/firestore");
const { initializeApp } = await import(firebaseAppPath);
const { getAuth, createUserWithEmailAndPassword, deleteUser } = await import(firebaseAuthPath);
const { getFirestore, collection, doc, getDocs, setDoc, Timestamp } = await import(firebaseFsPath);
const env = Object.fromEntries(
  readFileSync(envPath, "utf8")
    .split("\n")
    .map((line) => line.match(/^([^#=]+)=(.*)$/))
    .filter(Boolean)
    .map((m) => [m[1].trim(), m[2].trim()])
);

const app = initializeApp({
  apiKey: env.NEXT_PUBLIC_FIREBASE_API_KEY,
  authDomain: env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN,
  projectId: env.NEXT_PUBLIC_FIREBASE_PROJECT_ID,
  storageBucket: env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,
  appId: env.NEXT_PUBLIC_FIREBASE_APP_ID,
});
const auth = getAuth(app);
const db = getFirestore(app);

const email = `rules-check-${Date.now()}@example.com`;
const password = `Check_${Date.now()}_z9`;
const cred = await createUserWithEmailAndPassword(auth, email, password);
const uid = cred.user.uid;
await setDoc(doc(db, "users", uid), {
  uid,
  email,
  displayName: "RulesCheck",
  role: "USER",
  createdAt: Timestamp.now(),
  lastActive: Timestamp.now(),
});

async function canRead(pathParts) {
  try {
    await getDocs(collection(db, ...pathParts));
    return true;
  } catch {
    return false;
  }
}

const hiddenTopics = await canRead(["hiddenTopics"]);
const feedHiddenTopics = await canRead(["feedHiddenTopics"]);
const blockedUsers = await canRead(["users", uid, "blockedUsers"]);

console.log("Project:", env.NEXT_PUBLIC_FIREBASE_PROJECT_ID);
console.log("hiddenTopics:      ", hiddenTopics ? "OK" : "DENIED");
console.log("feedHiddenTopics:  ", feedHiddenTopics ? "OK" : "DENIED");
console.log("blockedUsers:      ", blockedUsers ? "OK" : "DENIED");

try {
  await deleteUser(cred.user);
} catch {
  /* ignore */
}

if (hiddenTopics && !feedHiddenTopics) {
  console.log(`
VERDICT: Production rules are STALE (partial publish or wrong file).

hiddenTopics and feedHiddenTopics use the same read rule in firestore.rules.
If one works and the other does not, the feedHiddenTopics block was never published.

Fix:
1. Open https://console.firebase.google.com/project/dissidentcodex/firestore/rules
2. In the editor, press Ctrl+F and search: feedHiddenTopics
   → If 0 results, your publish did not include the new rules.
3. Open ~/AndroidStudioProjects/Codex/firestore.rules (NOT the .grok worktree copy)
4. Select ALL → copy → paste into console (replace everything) → Publish
5. Search again for feedHiddenTopics AND blockedUsers — both must appear.
6. Re-run: node scripts/verify-rules-published.mjs
`);
  process.exit(1);
}

if (!blockedUsers || !feedHiddenTopics) {
  console.log("\nVERDICT: Rules still missing. See firestore.rules in ~/AndroidStudioProjects/Codex/");
  process.exit(1);
}

console.log("\nVERDICT: All required rules are live.");
