# Codex — Sleek Coding Discussion Forum (Android)

**Codex** is a high-function, modern Android forum app for discussing code. Built for Android Studio with Kotlin, Material 3, and Firebase.

## Features Implemented

- **Authentication**: Email/password sign up and login via Firebase Auth.
- **User Profiles**: Display name, bio, profile picture (upload + stored in Firebase Storage).
- **Posts**: Create rich posts with title, body, optional image upload, category selection.
- **Feed**: Real-time-ish list (pull-to-refresh), filter by 9 coding-related categories using chips. Live search by title/body. Clickable category tags on posts jump the filter. Client-side category filter (no index required).
- **Post Detail**: Full view, embedded image, like (simple count), threaded comments with author info and role badges.
- **Roles & Permissions** (enforced in app + Firestore rules):
  - **USER / Member**: Full read, create posts & comments.
  - **MOD / Moderator**: Delete any post, change user roles (suspend/ban/promote), full moderation.
  - **ADMIN**: Same as mod + promote others to Admin.
  - **SUSPENDED**: Can read and view everything, but cannot post or comment (clear UI feedback).
  - **BANNED**: Blocked at login; cannot access app content.
- **Moderation Panel + Dedicated Mod Tools Menu**: Accessible from Profile (and top toolbar menu for mods). Full ModToolsActivity with user role manager + recent post moderation list (deletes etc.). Refactored for reuse.
- **Drafts**: Auto + manual save/restore of post title/body/category in Create tab (per-user local prefs). Persists across navigation/background; cleared on publish.
- **Post history in profiles**: Fully functional "Your Posts" (click to open, delete). Dynamic count + empty state.
- **Super Admin**: `ericdanielevans@gmail.com` is automatically granted (and maintained as) ADMIN role with max permissions on register/login/restore.
- **Images**: Posts and avatars uploaded to Firebase Storage, displayed with Glide.
- **Sleek Dark UI**: Deep navy + teal accents, role color badges (gold admin, blue mod, etc.), clean cards.
- **Navigation**: Bottom nav (Feed / Create / Profile). Post detail as separate activity.

## Project Structure

```
Codex/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/codex/app/
│       │   ├── CodexApplication.kt
│       │   ├── MainActivity.kt
│       │   ├── auth/
│       │   │   └── AuthActivity.kt
│       │   ├── adapters/
│       │   │   └── PostAdapter.kt
│       │   ├── models/
│       │   │   ├── Comment.kt
│       │   │   ├── Post.kt
│       │   │   ├── Role.kt
│       │   │   └── User.kt
│       │   ├── ui/
│       │   │   ├── CreatePostFragment.kt
│       │   │   ├── FeedFragment.kt
│       │   │   ├── PostDetailActivity.kt
│       │   │   └── ProfileFragment.kt
│       │   └── utils/
│       │       └── FirebaseHelper.kt
│       └── res/
│           ├── drawable/               # All vector icons + badges + jpg logo/avatar
│           ├── layout/                 # All activity + fragment + item XML
│           ├── menu/
│           ├── mipmap-*/               # Launcher icons (adaptive + png fallbacks)
│           ├── values/ + values-night/ # colors, themes, strings
│           └── ...
├── firestore.rules
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

## Setup & Running in Android Studio (Full Instructions)

1. **Open the project**
   - File → Open → select the `Codex` folder.
   - Android Studio will download Gradle + dependencies on first sync.

2. **Firebase Project (REQUIRED)**
   - Go to https://console.firebase.google.com/
   - Create a new project (or reuse).
   - Add an **Android app**:
     - Package name: `com.codex.app`
     - Download `google-services.json`
   - Place the downloaded `google-services.json` into:
     `Codex/app/google-services.json`
   - In Firebase Console enable:
     - **Authentication** → Sign-in method → Email/Password (enable)
     - **Firestore Database** → Create database (start in **test mode** first, then replace rules)
     - **Storage** → Get started (test rules ok initially)

3. **Firestore Security Rules**
   - In Firebase Console → Firestore → Rules tab, paste the content of the `firestore.rules` file from this project root.
   - Or deploy via CLI: `firebase deploy --only firestore:rules`
   - The rules allow authenticated reads, create for active roles, and mod/admin privileged write/delete. **Important**: rules now also restrict profile field updates (no self role escalation) and user data reads (emails private except to self/mods).

4. **Firebase Storage Security Rules (REQUIRED for image security)**
   - In Firebase Console → Storage → Rules tab, paste the content of the `storage.rules` file.
   - Or deploy: `firebase deploy --only storage`
   - This ensures only the owning user can upload to their `post_images/{uid}/...` or `profile_pics/{uid}/...` paths, while allowing public reads for displayed images. Default/test rules are insecure for production.

5. **Build & Run**
   - Sync Gradle.
   - Select a device/emulator (API 24+ recommended).
   - Run 'app'.
   - The launcher is the stylized "C" icon.
   - First run: Register a new account. It starts as Member (USER).

6. **Test Roles, Fixes & New Features**
   - Register normal + the special `ericdanielevans@gmail.com` (auto-ADMIN with full mod powers, visible Mod Tools in toolbar + profile).
   - Create posts under different categories. In feed: filter by chip (posts now correctly appear only under their topic), use live search, click category tag on a post card to filter.
   - Create tab: drafts auto-save + manual Save/Discard; restore after leaving/returning; cleared after publish.
   - Profile: "Your Posts" now fully works (click opens with correct post, delete ok); shows count + helpful empty state.
   - Mods: access "Mod Tools" from top menu or profile button -> dedicated screen with user manager + post moderation list (deletes).
   - Normal users cannot access mod tools.

## Graphics & Assets

- Custom vector launcher icon (adaptive) + generated PNG fallbacks.
- Multiple vector icons and role badges (crown, shield, clock, ban, etc.).
- Two generated high-quality JPGs (logo + default avatar) placed in drawable.
- Everything coded in XML where possible for theming.

## Extending the App

Many improvements from the plan implemented (search, drafts, clickable categories, share, profile polish, dedicated mod menu, super-admin email, post IDs fixed for history/filtering).

Remaining ideas for v2:
- Real per-user like tracking + filled heart state.
- Rich text / code syntax blocks (use WebView or custom spans).
- Push notifications (FCM) for replies.
- Report post flow + admin queue in ModTools.
- Pagination / infinite scroll on feed (currently client limited).
- Other user profiles (click author -> read-only view with their history).
- Post editing for authors/mods.
- Dark/light complete parity or theme switch.

## Notes / Gotchas

- Always run on device/emulator with internet (Firebase).
- If login shows "banned" during testing, use Firebase Console → Auth → Users and delete the user doc + auth entry, or use the mod panel.
- Image upload requires Storage enabled.
- The `gradle-wrapper.jar` may be missing on fresh clone. Android Studio will usually offer to fix/ download Gradle wrapper when you open the project.

Enjoy building the community on Codex!

```

## Directory verification
```

Now list the full dir to verify.