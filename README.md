# CampusSync (Android)

Campus Sync (an advanced notice board for Android) is a campus community app: students sign in, discover and RSVP to events, and join groups.
Built in Java with XML layouts, MVVM, Material Design 3 and Firebase.

- **Package:** `com.campussync.app`
- **minSdk:** 23 · **target/compileSdk:** 35
- **Stack:** Android Views + ViewBinding, MVVM (ViewModel/LiveData), Navigation Component,
  Firebase Auth, Cloud Firestore, Cloud Storage, Firebase Cloud Messaging

## Current module

Module 1 — foundation and authentication:

- Email/password sign-in and registration with inline validation and mapped Firebase error messages
- Firestore user profile created on registration (`users/{uid}`)
- Session persistence with an auth gate: signed-out users only see the auth screen
- Bottom navigation shell (Home, Events, Groups, Profile) with Home, Events, Groups and Profile screens
- Event list + detail with RSVP, group list + detail with membership, both written inside Firestore transactions
- Profile summary (joined events/groups counts) and Settings (notification preference, cache clear)
- FCM service with notification channels, token stored on the user document, and notification-tap routing
- Loading / empty / error states on every data screen

## Setup

1. Create a Firebase project and add an Android app with package `com.campussync.app`.
2. Download `google-services.json` into `app/google-services.json`.
   It is git-ignored on purpose and **must not be committed**.
3. Enable **Authentication → Email/Password** and **Cloud Firestore** in the console.
   **Storage is optional:** on projects created after October 2024 a bucket can only be created on
   the Blaze plan, so the app never assumes one exists. Every image is loaded from the `imageUrl`
   field, so a plain HTTPS link works without Storage and no `storage.rules` are needed.
4. Build from Android Studio, or:

   ```bash
   ./gradlew :app:assembleDebug
   ```

   The Google Services plugin is applied only when `app/google-services.json` exists, so the project
   still compiles without credentials; the auth screen shows a configuration message instead.

## Firestore data model

| Collection | Fields |
| --- | --- |
| `users/{uid}` | `email`, `displayName`, `createdAt`, `fcmTokens[]` |
| `events/{eventId}` | `title`, `description`, `location`, `startDate`, `endDate`, `imageUrl`, `organizerId`, `attendeeIds[]` |
| `groups/{groupId}` | `name`, `description`, `imageUrl`, `memberIds[]`, `createdAt` |

`FirestoreManager.ensureDemoData()` seeds one event and one group when the collections are empty,
so every screen can be exercised before an admin/module-2 authoring flow exists.

## Suggested Firestore security rules (starting point)

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth.uid == userId;
    }
    match /events/{eventId} {
      allow read, create: if request.auth != null;
      allow update: if request.auth != null;
      allow delete: if false;
    }
    match /groups/{groupId} {
      allow read, create: if request.auth != null;
      allow update: if request.auth != null;
      allow delete: if false;
    }
  }
}
```

Membership writes are transactional in the client, but a Cloud Function should enforce the same
rules server-side for trusted operations.

## Images without Cloud Storage

`FirestoreManager.getStorageOrNull()` returns `null` instead of throwing when the project has no
Storage bucket, and `getStorageReference()` returns `null` in that case — callers must check it.
Event and group covers are just URLs (`imageUrl` in Firestore) rendered with Glide, so they can
point at any public host. Uploads from the app need a bucket (Blaze plan) or a third-party image
host; neither is required for the current module.

## Structure

```
app/src/main/java/com/campussync/app/
├── CampusSyncApp.java           Application, notification channels
├── firebase/                    AuthManager, FirestoreManager, FCM service
├── model/                       Event, Group, UserProfile
└── ui/
    ├── MainActivity.java        Nav host, bottom bar, auth gate
    ├── auth/                    AuthFragment + AuthViewModel
    ├── home/                    HomeFragment + HomeViewModel
    ├── events/                  list, detail, adapters, ViewModels
    ├── groups/                  list, detail, adapter, ViewModels
    ├── profile/                 ProfileFragment + ProfileViewModel
    └── settings/                SettingsFragment
```

## Known gaps

- Cloud Functions for trusted backend operations are not part of this module yet.
- Event/group creation UI is admin-side and belongs to a later module.
- Profile editing (avatar, display name) is not implemented yet.
