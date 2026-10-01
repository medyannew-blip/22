# Tasker

A minimalist to-do app for Android phones and tablets, built with Kotlin and Jetpack Compose.

**Download:** [`dist/Tasker.apk`](dist/Tasker.apk). CI rebuilds it on every push. To install it, open the file on your phone and allow "Install unknown apps" for your browser or file manager. Android 8.0 (API 26) or newer is required.

## Features

- **Sidebar navigation**: a slide-out drawer on phones and a permanent sidebar on tablets.
- **Views**: Today (with Top 3 priorities), Inbox, All tasks, Day (timeline), 3 days, Week, Planner (TeuxDeux-style day columns with "someday" lists underneath), Calendar (month), Kanban, Eisenhower matrix, GTD, Logbook, and Search.
- **Drag & drop**: long-press any task and drag it.
  - Drop it in the same list to reorder it.
  - Drop it on another day, time slot, calendar cell, Kanban column, Eisenhower quadrant, GTD list or context, Top 3 box, or someday list to reschedule or move it.
- **Scheduling**: a start date and time, plus either an end date and time or a duration. Tasks can repeat daily, on weekdays, weekly, every 2 weeks, monthly or yearly.
- **Reminders and notifications**:
  - Any number of reminders per task.
  - A "nearly due" alert at a lead time you choose.
  - An overdue alert with *Done / Tomorrow / Reschedule…* actions.
  - Snooze and +1 hour actions on reminders.
  - A morning summary with *Move overdue to today*.
  - Alarms survive reboots.
- **Organisation**: unlimited tasks, folders, projects and tags. Tags that start with `@` are GTD contexts.
- **Subtasks and notes** on every task.
- **GTD**: Inbox capture, a guided *Clarify* flow (actionable? → 2-minute rule → delegate / defer / next action), Next actions grouped by context, Waiting for, Scheduled, Someday/Maybe, Reference, a project health check, and a Weekly review checklist.
- **Top 3 priorities of the day**: tap the star on a task or drag it into the Top 3 box.
- **Themes**: 15 built-in themes (7 light, 8 dark) and a custom theme editor with a colour picker. You can also follow the system dark mode with your chosen light and dark themes.
- **RTL**: the layout mirrors fully. Arabic, Hebrew and Persian translations are included, the in-app language picker works per app, and you can force RTL layout in any language.
- **Sync across devices**: see below.
- **Backup**: export and import JSON.
- **Extras**: a quick-add FAB, a launcher shortcut "Add task", and "Share → Tasker" to capture text from other apps.

## Natural-language quick add

Type naturally in the quick add box. Tasker shows what it understood as chips before you save.

| You type | Meaning |
|---|---|
| `today`, `tonight`, `tomorrow`, `friday`, `next mon`, `this weekend`, `next week`, `in 3 days`, `dec 25`, `25 march`, `12/25`, `2026-12-25` | date |
| `at 5pm`, `17:00`, `noon`, `morning`, `in 30 min` | time |
| `3-5pm`, `from 10:00 to 11:30` | start and end time |
| `from mon to fri` | start and end date |
| `for 2h`, `for 45 min`, `for 1h30m` | duration |
| `every day`, `every weekday`, `every monday`, `every 2 weeks`, `monthly` | repeat |
| `#tag`, `@context`, `+Project`, `+"Multi word project"` | tags, contexts, project |
| `!1` / `!2` / `!3`, `p1`, `!!!` | priority |
| `*` or `!top` | add to today's Top 3 |
| `remind me 30m before`, `remind at 4pm` | reminder |
| `waiting for Sam`, `/someday`, `/next`, `/ref` | GTD list |

Example: `Call Anna tomorrow 5pm #home +Work !1 * remind me 30m before`

## Sync across devices

Open **Settings → Sync across devices** and pick one option:

1. **Cloud file** (no account needed)
   1. Tap **Create sync file** and save `tasker-sync.json` in Google Drive, Dropbox, OneDrive, Nextcloud or a Syncthing folder.
   2. On your other devices, tap **Open existing file** and pick the same file.

   Tasker merges changes from each device (last edit wins per item), syncs in the background every 15 minutes, and also syncs when you open the app or edit something.
2. **Realtime (Firebase)**: uses your own free Firebase project.
   1. Create a project at <https://console.firebase.google.com>.
   2. Add an Android app with package `com.tasker.app`.
   3. Under **Authentication**, enable *Email/Password*.
   4. Create a **Cloud Firestore** database with these rules:
      ```
      rules_version = '2';
      service cloud.firestore {
        match /databases/{db}/documents {
          match /users/{uid}/{document=**} {
            allow read, write: if request.auth != null && request.auth.uid == uid;
          }
        }
      }
      ```
   5. Copy the **Project ID**, **Web API key** and **App ID** from Project settings into Tasker, then create an account or sign in. Use the same account on every device.

Reminders are scheduled on every device. Tasker can also notify you when another device changes your tasks.

## Building

```
./gradlew assembleRelease     # APK in app/build/outputs/apk/release/
./gradlew testDebugUnitTest   # parser, repository and Robolectric screen tests
```

The release APK is signed with the keystore in `app/tasker.keystore`, which is committed so that every build can install over the previous one. Replace it with your own key if you plan to publish the app.
