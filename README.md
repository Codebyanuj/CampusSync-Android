# CampusSync (Android)

Campus Sync (an advanced notice board for Android) is a campus community app: students and faculty sign in, discover and RSVP to events, join groups and carry a verified campus profile.
Built in Java with XML layouts, MVVM, Material Design 3 and Firebase.

- **Package:** `com.campussync.app`
- **minSdk:** 23 · **target/compileSdk:** 35
- **Stack:** Android Views + ViewBinding, MVVM (ViewModel/LiveData), Navigation Component,
  Firebase Auth, Cloud Firestore, Cloud Storage, Firebase Cloud Messaging

## Authentication and profiles

- Splash screen that routes on the restored session, and a one-time onboarding flow
- Student and faculty registration with a shared validated form (role, college, department,
  course/semester or designation, academic year)
- Email/password sign-in with remembered email, mapped Firebase error messages, and sign-out
- Forgot password (reset email), email verification gate with resend + refresh
- Profile: name, photo, email, department, course, semester, academic year, designation,
  bio, skills, interests, role, account status and verification status
- Edit profile with the same validation as registration, and profile photo upload to
  Firebase Storage (`profile_photos/{uid}/avatar.jpg`, downscaled before upload)
- Profile settings: edit profile, verification centre, change password and sign-out
- Verification: student/faculty requests, live status tracking, admin approve/reject queue and
  verified badges rendered from admin-approved status only
- Suspended accounts are signed out when they reach the main screens
- Firestore and Storage security rules enforce role-based access (see below)

## Social community

- **Feed** (Social tab): create text and image posts, like/unlike, comment and reply, share,
  bookmark, edit or delete your own posts, report posts. Three feeds: your college, people you
  follow, and trending (most liked). The first page is live and older pages load on scroll.
- **Ghost mode**: a post can be published anonymously. The public document then has no
  `authorId`; the attribution is written to `anonymousAuthors/{postId}` and is readable only by
  its author and administrators, so no client query can join anonymous content to an account.
- **Stories**: image and video stories, a viewer with progress segments, tap left/right
  navigation, view tracking, replies, deletion of your own story and 24-hour expiry driven by the
  trusted server timestamp.
- **Message boards** (from the feed toolbar): create boards and discussions, upvote/downvote,
  comment and reply, prefix search over boards and discussions, trending discussions across all
  boards, anonymous discussions and reporting.
- **Moderation**: every report lands in `reports/{id}`. Administrators see the queue from
  Profile → *Reported content*, can resolve or dismiss a report, and can reveal the restricted
  author of anonymously published content.

Following a classmate from their public profile feeds the *Following* tab.

## Private and group messaging

- **Chats tab** (third item in the bottom bar, with an unread badge): every thread you belong to,
  showing the last message, time and unread count. Blocked partners' direct threads are hidden.
- **One-to-one chats**: real-time text, image and document attachments (up to 25 MB), timestamps
  in the bubble, read receipts, typing indicators, online / last-seen status, paginated history
  (25 messages per page, older pages load as you scroll up).
- **Groups**: create with a name, photo and member list; admins can rename, add or remove members
  and promote or demote other admins; every member can mute, leave and read the full history.
- **Blocking and reporting**: blocking is mutual for direct threads (rules refuse new messages in
  either direction) and hides the thread; users and individual messages can be reported into the
  same `reports` queue administrators already review.
- **Notifications**: direct and group messages, and group invitations, arrive over FCM. The phone
  also honours the in-app switches in Settings for push, message and group-invite categories.

## Event radar and campus activities

- **Event radar** (Events tab): college notices entry point, upcoming events ordered by start date,
  live countdowns, category chips (academic, cultural, sports, workshop, tech, social, other),
  free-text search over title / description / venue / host, and date filters (today, this week,
  this month). Cancelled events and events that ended before today drop out of the feed.
- **Intercollege discovery**: every event carries a college and an `intercollege` flag. The
  "My college" chip narrows the radar to the student's own college, the college is shown on each
  detail page, and events flagged intercollege accept registrations from any college.
- **Hosting**: publish an event with title, description, venue, category, college, capacity
  (0 = unlimited), the intercollege flag and a start/end chosen with the Material date and time
  pickers. Hosts can post updates and cancel their event from the detail page.
- **Registration**: one transaction creates `events/{id}/registrations/{uid}` and moves the
  mirrored `registrationCount` / `attendeeIds` on the event. The registration document id is the
  uid, so a student can never register twice; the rules reject a counter move past `capacity` and
  refuse registrations for cancelled events.
- **Tickets and attendance**: each registration carries a random `qrToken`, rendered as a QR code
  (`campussync://event/{eventId}/registration/{uid}?token=…`) generated with ZXing. Only the
  organizer sees **Scan ticket**; the scan re-reads the registration inside a transaction and
  writes `attended` / `checkedInAt` only when the scanned token equals the stored one. Rules allow
  that write only from `attended == false` to `true`, by the organizer or an administrator, so
  duplicate check-ins and forged codes are rejected.
- **Participant lists**: the organizer (and administrators) can open the participant list with
  registration and attendance state; everyone else sees counts only, because the registrations
  subcollection is readable by its owner, the organizer and administrators only.
- **Notifications**: registration confirmation, organizer "new registration", event updates,
  schedule/venue changes, cancellation and an hourly reminder for events starting within 24 hours
  are sent by the functions in `functions/index.js` on the event notification channel.

Radar filtering happens on the client over a start-date-ordered window, so these screens need no
composite indexes; only `notices` and event `updates` are ordered server-side (`createdAt`).

## Academic hub and peer learning

Opened from the Home card or Profile → *Academic hub*; one screen with three tabs.

### Knowledge sharing

- **Library** (Resources tab): notes, PDFs, slide decks and previous-year question papers,
  organised by subject, department and semester. Filter chips for the resource type, *My
  department*, *My semester* and *Saved*, plus free-text search over title, subject, department,
  uploader and tags. The Firestore query only orders by `createdAt` and every filter runs on the
  client, so the library needs no composite index.
- **Uploads**: pick a file with the system picker; the app validates name, size and content type
  (`UploadValidator`, unit tested), uploads to `academic_resources/{uid}/…` and only then writes
  the library record. Storage rules repeat the 20 MB and document-type limits, and refuse writes
  outside the uploader's own folder. If the record cannot be written the uploaded object is
  deleted again, so there is no orphaned public file.
- **View and download**: the file is handed to the device's viewer through an `ACTION_VIEW`
  intent and the mirrored `downloadCount` moves by one (rules allow at most a single step).
- **Bookmarks**: a private `users/{uid}/resourceBookmarks/{resourceId}` document plus a mirrored
  `bookmarkCount`; the *Saved* chip filters the library by them.
- **Ratings and feedback**: one rating document per student (`resources/{id}/ratings/{uid}`) with
  1–5 stars and optional written feedback. The resource's `ratingSum`/`ratingCount` move in the
  same transaction, and rules keep the sum inside `0…5 × count`.
- **Reporting**: the existing moderation queue. A resource report is a normal `reports` document
  with `targetType: "resource"`, so administrators review it where they already review posts.

### Peer tutoring

- **Doubts** (Doubts tab): ask a question with subject, department and semester; peers answer in
  the thread. The asker accepts an answer (which marks the doubt solved) or closes it without an
  accepted answer. Only the asker may solve, and only the answer's author may edit the answer
  text; one answer moves `answerCount` by exactly one.
- **Tutor directory** (Tutors tab): search by subject, name or department. Any student can publish
  a listing (up to five subjects, a short bio) from **Offer tutoring**; the listing reuses the
  profile's name, photo, department and semester.
- **Tutor profile**: rating average, completed sessions, bio and the reviews left by students, plus
  **Request session** and **Message** — the message button reuses the existing direct-chat module
  (`MessagingRepository.getOrCreateDirectConversation`).
- **Requests** (*Tutoring requests* in the hub menu): the requests you sent and received. The tutor
  accepts or declines a pending request; the student cancels an open one or completes it with the
  single review the request can hold. Only the two participants can read a request.

### Karma

- Karma is a **read-only ledger** (`karmaAwards/{awardId}`), not a mutable balance. The award id is
  the contribution (`answer_accepted_{answerId}`, `resource_helpful_{resourceId}`,
  `tutor_review_{requestId}`), the amount is fixed in `functions/karma.js`, and only the Cloud
  Functions write it. A repeat therefore finds the same document and is ignored, and a client can
  neither award itself nor edit an award.
- Awards: an accepted answer **+15**, a tutoring session reviewed 4★ or better **+10**, a resource
  with at least three ratings averaging 4★ or better **+10**. Accepting your own answer earns
  nothing.
- **Balance and history** (hub → *Karma*): the balance is the sum of the account's ledger rows
  (newest 200), so it can never drift from the history. The rules only deliver the caller's own
  awards; an administrator can read all of them.

Deploy the award triggers together with the other functions:

```bash
cd functions && npm install
firebase deploy --only functions
```

## Setup

1. Create a Firebase project and add an Android app with package `com.campussync.app`.
2. Download `google-services.json` into `app/google-services.json`.
   It is git-ignored on purpose and **must not be committed**.
3. Enable **Authentication → Email/Password**, **Cloud Firestore** and **Cloud Storage** in the
   console. Storage hosts profile photos. On projects created after October 2024 a bucket can only
   be created on the Blaze plan; when no bucket exists the app reports a clear error on upload
   instead of crashing, and every other image is loaded from the `imageUrl` URL field.
4. Deploy the security rules in this repository:

   ```bash
   firebase deploy --only firestore:rules,storage
   ```

5. Create the first administrator by adding a document `admins/{uid}` in the Firebase console
   (Firestore → `admins` collection). The verification review menu appears for these accounts.
6. Build from Android Studio, or:

   ```bash
   ./gradlew :app:assembleDebug
   ```

   The Google Services plugin is applied only when `app/google-services.json` exists, so the project
   still compiles without credentials; the auth screen shows a configuration message instead.

## Firestore data model

| Collection | Fields |
| --- | --- |
| `users/{uid}` | `email`, `displayName`, `photoUrl`, `role` (`student`/`faculty`), `college`, `department`, `course`, `semester`, `academicYear`, `designation`, `bio`, `skills[]`, `interests[]`, `accountStatus`, `verificationStatus`, `verifiedRole`, `emailVerified`, `fcmTokens[]`, `createdAt`, `updatedAt` |
| `verificationRequests/{id}` | `uid`, `name`, `email`, `role`, `college`, `department`, `course`, `semester`, `academicYear`, `designation`, `idNumber`, `note`, `status` (`pending`/`approved`/`rejected`), `submittedAt`, `reviewedAt`, `reviewedBy`, `reviewNote` |
| `admins/{uid}` | Marker document; presence grants administrator access |
| `events/{eventId}` | `title`, `description`, `location`, `startDate`, `endDate`, `imageUrl`, `category`, `college`, `intercollege`, `status` (`published`/`cancelled`), `capacity` (0 = unlimited), `registrationCount`, `attendeeIds[]`, `organizerId`, `organizerName`, `createdAt`, `updatedAt` |
| `events/{eventId}/registrations/{uid}` | `uid` (document id), `name`, `email`, `college`, `status` (`registered`/`cancelled`), `qrToken`, `attended`, `checkedInAt`, `reminderOptIn`, `createdAt`, `updatedAt` |
| `events/{eventId}/updates/{updateId}` | `title`, `body`, `authorId`, `authorName`, `createdAt` |
| `notices/{noticeId}` | `title`, `body`, `college`, `authorId`, `authorName`, `pinned`, `createdAt` |
| `groups/{groupId}` | `name`, `description`, `imageUrl`, `memberIds[]`, `createdAt` |
| `posts/{postId}` | `authorId` (absent for ghost posts), `authorName`, `authorPhotoUrl`, `authorRole`, `college`, `anonymous`, `content`, `imageUrls[]`, `likeCount`, `commentCount`, `shareCount`, `edited`, `createdAt`, `updatedAt` |
| `posts/{postId}/likes/{uid}` | `createdAt` — one document per liker, id is the user id |
| `posts/{postId}/comments/{commentId}` | `authorId` (absent when anonymous), `authorName`, `authorPhotoUrl`, `anonymous`, `content`, `parentId`, `createdAt` |
| `users/{uid}/bookmarks/{postId}` | `postId`, `preview`, `authorName`, `savedAt` |
| `users/{uid}/following/{targetUid}` | `followingId`, `since` |
| `stories/{storyId}` | `authorId`, `authorName`, `authorPhotoUrl`, `mediaUrl`, `mediaType` (`image`/`video`), `viewCount`, `createdAt` (server timestamp) |
| `stories/{storyId}/views/{uid}` | `viewedAt` — idempotency key for view tracking |
| `stories/{storyId}/replies/{replyId}` | `authorId`, `authorName`, `content`, `createdAt` |
| `boards/{boardId}` | `name`, `nameLower`, `description`, `college`, `createdBy`, `discussionCount`, `createdAt` |
| `boards/{boardId}/discussions/{discussionId}` | `boardId`, `title`, `titleLower`, `body`, `authorId` (absent when anonymous), `authorName`, `college`, `anonymous`, `upvoteCount`, `downvoteCount`, `commentCount`, `createdAt`, `lastActivityAt` |
| `boards/{boardId}/discussions/{id}/votes/{uid}` | `value` (`1` / `-1`), `createdAt` |
| `boards/{boardId}/discussions/{id}/comments/{commentId}` | Same shape as post comments, plus `discussionId` in the client model |
| `anonymousAuthors/{contentId}` | `ownerId`, `contentType`, `targetId`, `createdAt` — restricted attribution |
| `reports/{reportId}` | `targetType` (also `user` and `message`), `targetId`, `targetLabel`, `reason`, `details`, `reporterId`, `status` (`open`/`resolved`/`dismissed`), `createdAt` |
| `conversations/{conversationId}` | `type` (`direct`/`group`), `title`, `photoUrl`, `memberIds[]`, `adminIds[]`, `memberNames{}`, `memberPhotos{}`, `unreadCounts{}`, `ownerId`, `directKey`, `directA`, `directB`, `memberCount`, `lastMessage`, `lastMessageAt`, `lastSenderId`, `createdAt`, `updatedAt` |
| `conversations/{id}/members/{uid}` | `name`, `photoUrl`, `role` (`admin`/`member`), `muted`, `unreadCount`, `lastReadAt`, `joinedAt` — the row security rules use to authorise access |
| `conversations/{id}/messages/{messageId}` | `conversationId`, `senderId`, `senderName`, `senderPhotoUrl`, `type` (`text`/`image`/`document`/`system`), `text`, `attachmentUrl`, `attachmentName`, `attachmentSize`, `mimeType`, `readBy[]`, `createdAt` |
| `conversations/{id}/typing/{uid}` | `name`, `typingAt` (stale after 6 seconds) |
| `users/{uid}/blocked/{targetUid}` | `name`, `blockedAt` — private to the blocker |
| `presence/{uid}` | `state` (`online`/`offline`), `lastSeen` (heartbeat, online window 2 minutes) |
| `resources/{resourceId}` | `title`, `description`, `type` (`notes`/`pdf`/`slides`/`questionPaper`), `subject`, `subjectLower`, `department`, `semester`, `college`, `tags[]`, `fileName`, `fileUrl`, `filePath`, `fileSize`, `mimeType`, `uploaderId`, `uploaderName`, `uploaderPhotoUrl`, `downloadCount`, `bookmarkCount`, `ratingSum`, `ratingCount`, `status`, `createdAt`, `updatedAt` |
| `resources/{id}/ratings/{uid}` | `stars` (1–5), `feedback`, `raterName`, `createdAt`, `updatedAt` |
| `users/{uid}/resourceBookmarks/{resourceId}` | `resourceId`, `title`, `subject`, `fileUrl`, `savedAt` |
| `doubts/{doubtId}` | `title`, `body`, `subject`, `department`, `semester`, `college`, `authorId`, `authorName`, `authorPhotoUrl`, `answerCount`, `solved`, `acceptedAnswerId`, `createdAt`, `lastActivityAt` |
| `doubts/{id}/answers/{answerId}` | `body`, `authorId`, `authorName`, `authorPhotoUrl`, `accepted`, `createdAt` |
| `tutors/{uid}` | `displayName`, `photoUrl`, `college`, `department`, `semester`, `subjects[]`, `bio`, `ratingSum`, `ratingCount`, `completedSessions`, `createdAt`, `updatedAt` |
| `tutoringRequests/{requestId}` | `studentId`, `studentName`, `studentPhotoUrl`, `tutorId`, `tutorName`, `subject`, `message`, `preferredTime`, `status` (`pending`/`accepted`/`declined`/`completed`/`cancelled`), `reviewRating`, `reviewComment`, `createdAt`, `updatedAt`, `reviewedAt`, `completedAt` |
| `karmaAwards/{awardId}` | `recipientId`, `sourceType`, `sourceId`, `amount`, `note`, `createdAt` — written only by the Cloud Functions |

`users/{uid}` is written as a single structured document. Role, `accountStatus` and the verified
fields are server-owned: security rules let an owner edit their academic/profile fields but never
grant themselves a role or badge. Owners may only move `verificationStatus` between `unverified`
and `pending`, and may only set `emailVerified` when Firebase Auth confirms the address.

`FirestoreManager.ensureDemoData()` seeds one event and one group when the collections are empty,
so every screen can be exercised before an admin authoring flow exists.

Required indexes (Firestore prints a create-index link in Logcat when one is missing):

- `verificationRequests`: `uid` ASC + `submittedAt` DESC.
- `posts`: `college` ASC + `createdAt` DESC (campus feed).
- Collection group `discussions`: `upvoteCount` DESC (trending) and `titleLower` ASC
  (discussion search). Enable the collection-group scope for both in the console.

Stories older than 24 hours are hidden immediately (the query only asks for newer documents and
the client hides anything older) and the author's own expired stories are deleted as they are
read. For a guaranteed server-side sweep, deploy the optional scheduled function:

```bash
cd functions && npm install
firebase deploy --only functions
```

`functions/index.js` deletes every story older than 24 hours hourly, including its views and
replies.

The same deploy adds the messaging pushes: `notifyNewMessage` reads the conversation's member rows
before sending, skips muted threads and members with `notifyMessages: false`, and `notifyGroupInvite`
tells newly added members about the group. Notification toggles are mirrored to the user document
from Settings so the backend can respect them, and the device also filters locally.

## Security rules

- `firestore.rules` — profile ownership, admin-only verification/suspension, one pending request
  per write, admin-only review. Membership writes on events/groups require a signed-in user.
  Academic hub rules: uploads are attributed to the uploader with a real file and bounded
  metadata (20 MB, allowed types), the file fields are immutable, and counters move by one step
  with the rating sum kept inside 0…5 × count. Ratings, bookmarks and doubts are per-user
  documents (id = uid) so nothing can be double-voted. A tutoring request is readable by its two
  participants only, the tutor may only accept/decline, and the student's single review is stored
  on the request. `karmaAwards` is read-only for every client, including administrators.
  Social rules: content is attributed to the signed-in user or to nobody (ghost posts), owners
  edit/delete only their own content (anonymous ownership is proven through the restricted
  `anonymousAuthors` document), reactions can only touch the counter fields, one like/vote
  document per user, and `anonymousAuthors` plus `reports` are readable by admins only.
- `storage.rules` — profile photos readable by signed-in users, writable only by their owner and
  only for images under 5 MB. Academic resources (`academic_resources/{uid}/…`) are readable by
  signed-in students and writable only by the uploader, inside their own folder, for PDF, Office,
  text and image types under 20 MB. Feed images (`post_images/{uid}/…`) and story media
  (`stories/{uid}/…`) follow the same owner-only write rule with larger size limits.
  Chat attachments (`chat_attachments/{uid}/{conversationId}/…`) are readable only by members of
  that conversation — the rule checks the affordance row in Firestore, so a shared URL alone is
  not enough. Group photos (`group_photos/{uid}/…`) are readable by signed-in users.
- Messaging rules: every read and write on a thread requires the caller's own
  `conversations/{id}/members/{uid}` document. That row is what makes membership authoritative:
  only the account itself (or a group admin) can create it for them, so renaming a `memberIds`
  array grants nothing. Only admins change membership, names, photos and roles; only the sender
  may edit a message (and only its `readBy` receipts); a direct message is refused while either
  participant has blocked the other.

Membership writes are transactional in the client, but a Cloud Function should enforce the same
rules server-side for trusted operations.

## Structure

```
app/src/main/java/com/campussync/app/
├── CampusSyncApp.java           Application, notification channels
├── firebase/                    AuthManager, FirestoreManager, UserRepository, SocialRepository,
│                                StoryRepository, BoardsRepository, FCM service
├── model/                       Event, Group, UserProfile, VerificationRequest, Post, Comment,
│                                Story, Board, Discussion, ContentReport
├── data/                        Colleges (college/department/course reference data)
├── util/                        ProfileValidator, SessionPrefs, Tags
└── ui/
    ├── MainActivity.java        Nav host, bottom bar, auth gate, suspended-account check
    ├── splash/                  SplashFragment (session router)
    ├── onboarding/              OnboardingFragment
    ├── auth/                    AuthFragment, VerifyEmailFragment, AuthViewModel
    ├── home/                    HomeFragment + HomeViewModel
    ├── events/                  Radar, detail, host event, participants, my events, notices
    │                           + adapters/ViewModels
    ├── groups/                  list, detail, adapter, ViewModels
    ├── profile/                 Profile, EditProfile, UserProfile + ViewModels
    ├── verification/            VerificationFragment + ViewModel
    ├── social/                  Feed, CreatePost, Comments, StoryViewer + adapters/ViewModels
    ├── boards/                  Boards, BoardDetail, DiscussionDetail + adapters/ViewModels
    ├── messages/                Conversations, Chat, UserPicker, CreateGroup, GroupInfo,
    │                           BlockedUsers + adapters/ViewModels
    ├── academics/               AcademicHub, UploadResource, ResourceDetail, CreateDoubt,
    │                           DoubtDetail, TutorProfile, TutoringRequests, Karma
    │                           + adapters/ViewModels
    ├── admin/                   AdminVerificationsFragment, AdminReportsFragment + ViewModels
    └── settings/                SettingsFragment + SettingsViewModel
```

## Known gaps

- Karma only moves once the Cloud Functions in `functions/index.js` are deployed; until then the
  ledger stays empty and the Karma screen shows zero. The award decisions themselves are covered
  by `node tools/karma-test.js` and the ledger's rules by the emulator suite.
- The balance is the sum of the newest 200 awards; the screen says so when the page is full.
- `storage.rules` could not be exercised locally: the Storage emulator reports a null
  `request.resource.contentType` for media uploads, which denies even the pre-existing
  profile-photo rule. The Firestore metadata rules, the upload validator (JVM unit test) and the
  emulator suite are green, but the Storage limits still need a pass on a deployed project.

- Story expiry relies on the optional scheduled function above; without it, expired stories stay
  in Firestore but are never shown by the app.
- Saved posts are stored per user but there is no dedicated bookmarks screen yet.
- Event creation, registration, QR scanning and attendance compile against the rules above but
  were not exercised at runtime in this workspace (no Firebase project / `app/google-services.json`
  and an unstable emulator). They need a manual pass on a device with a configured project.
- Presence is heartbeat-based: without the Realtime Database there is no server disconnect hook,
  so a contact can look online for up to two minutes after they close the app.
- Typing markers and unread counters are written by clients; security rules validate membership and
  the shape of those writes, but a custom attacker could still zero another member's counter. The
  counters are display state only, never an access control decision.
