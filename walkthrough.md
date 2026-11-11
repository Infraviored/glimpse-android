# Walkthrough: Per-User "Hide This Folder" and "Show Only This Folder"

## Overview
Implemented the per-user "Hide this folder" and "Show only this folder" feature in the Glimpse Android app, connecting to the Nextcloud Glimpse app hidden folders API endpoints.

---

## Changes Implemented

### 1. Build Configuration & Version Bump
- **File**: `app/build.gradle.kts`
  - Bumped `versionCode` to `27`
  - Bumped `versionName` to `"2.6.0"`

### 2. Localization & Strings
- **Files**:
  - `app/src/main/res/values/strings.xml` (English)
  - `app/src/main/res/values-de/strings.xml` (German, informal "du")
- Added string keys:
  - `show_only_this_folder`: "Show only this folder" / "Nur diesen Ordner zeigen"
  - `hide_this_folder`: "Hide this folder" / "Diesen Ordner ausblenden"
  - `folder_hidden`: "“%1$s” hidden" / "„%1$s“ ausgeblendet"
  - `hidden_folders_title`: "Hidden folders" / "Ausgeblendete Ordner"
  - `hidden_folder_show_again`: "Show again" / "Wieder anzeigen"
  - `hidden_folder_notice`: "This folder is hidden from your timeline and search" / "Dieser Ordner ist in Timeline und Suche ausgeblendet"
  - `hidden_folders_empty`: "Hide a folder from a picture\'s menu." / "Blende einen Ordner über das Menü eines Bildes aus."
  - `hidden_folder_remove`: "Remove" / "Ausblenden aufheben"

### 3. Data Models & API Client
- **File**: `app/src/main/java/com/infraviored/glimpse/data/Models.kt`
  - Added `hidden: Boolean = false` to `Folder` model to reflect server folder listing responses.
  - Added `@Serializable data class HiddenFolders(val folders: List<String> = emptyList())`.
  - Added `@Serializable data class HiddenFolderResult(val folder: String = "", val folders: List<String> = emptyList())`.
- **File**: `app/src/main/java/com/infraviored/glimpse/data/Requests.kt`
  - Added `Requests.hideFolder(fileId: Long): JsonObject` (`{"fileId": 123}`).
  - Added `Requests.setHiddenFolders(folders: List<String>): JsonObject` (`{"folders": [...]}`).
- **File**: `app/src/main/java/com/infraviored/glimpse/data/Api.kt`
  - Added `hiddenFolders(): HiddenFolders` for `GET /api/hidden-folders`.
  - Added `hideFolder(fileId: Long): HiddenFolderResult` for `POST /api/hidden-folders`.
  - Added `setHiddenFolders(folders: List<String>): HiddenFolders` for `PUT /api/hidden-folders`.
  - Added `put(url: String, body: JsonObject)` HTTP helper method.
  - Enhanced API error handling in `call()` to check both `error` and `message` in JSON error responses (e.g. for root folder 400 responses).

### 4. Application State Management
- **File**: `app/src/main/java/com/infraviored/glimpse/ui/AppState.kt`
  - Added `hiddenFoldersChanged()` which increments both `privateVersion` and `version`. This mirrors `sensitiveModeChanged()` so that all screens using the `loadedForVersion` pattern (Timeline, Search, Duplicates, Map) and `FolderScreen` reload their lists.
- **File**: `app/src/main/java/com/infraviored/glimpse/ui/Actions.kt`
  - Added `PhotoActions.hideFolder(item: Item, onDone: () -> Unit = {})`:
    - Calls `api.hideFolder(item.fileId)`.
    - Triggers `app.hiddenFoldersChanged()`.
    - Invokes `onDone()`.
    - Shows notice "“<FolderName>” hidden" with "Undo" action.
    - When Undo is tapped, invokes `api.setHiddenFolders(previous)` and reloads via `app.hiddenFoldersChanged()`.

### 5. UI Features
- **Viewer** (`app/src/main/java/com/infraviored/glimpse/ui/viewer/ViewerScreen.kt`):
  - In `InfoSheet`, added "Show only this folder" with `Icons.Outlined.FolderOpen` navigating to `app.folder(folder)`.
  - Added "Hide this folder" with `Icons.Outlined.FolderOff` (hidden for the root folder `/`). Tapping hides the folder, displays the undo notice, closes the viewer, and reloads screens.
- **Selection Dock** (`app/src/main/java/com/infraviored/glimpse/ui/components/SelectionScaffold.kt`):
  - In `selectionMore`, added "Show only this folder" and "Hide this folder".
  - Follows the single-item subtitle/enabled pattern: enabled when exactly one item is selected (`similar_one` subtitle when multiple/none selected). "Hide this folder" is disabled for root folder items.
- **Folder Screen** (`app/src/main/java/com/infraviored/glimpse/ui/folders/FolderScreen.kt`):
  - When `folder.hidden == true`, renders a slim notice:
    "This folder is hidden from your timeline and search" with button "Show again".
  - Tapping "Show again" fetches current hidden folders, removes the matching path / prefix, calls `setHiddenFolders`, and notifies `hiddenFoldersChanged()` to refresh.
- **Settings & Hidden Folders Sheet**:
  - Created `app/src/main/java/com/infraviored/glimpse/ui/folders/HiddenFoldersSheet.kt`:
    - Renders bottom sheet with title "Hidden folders".
    - Shows empty state hint: "Hide a folder from a picture's menu."
    - Lists hidden folders with an `Icons.Outlined.Close` remove button per entry that updates the server list via PUT and reloads screens.
    - Tapping an item navigates to that folder.
  - Updated `AccountSheet` in `app/src/main/java/com/infraviored/glimpse/ui/Home.kt` with a row for "Hidden folders".

### 6. Automated Unit Tests
- **File**: `app/src/test/java/com/infraviored/glimpse/data/ModelsTest.kt`:
  - `hiddenFoldersAnswer`: Tests deserializing `{"folders": ["/Photos/Ads", "/Photos/Spam"]}` into `HiddenFolders`.
  - `hideFolderResultAnswer`: Tests deserializing `{"folder": "/Photos/Ads", "folders": ["/Photos/Ads"]}` into `HiddenFolderResult`.
  - `folderHiddenProperty`: Tests `Folder` deserialization with `hidden = true` and default `hidden = false`.
- **File**: `app/src/test/java/com/infraviored/glimpse/data/RequestsTest.kt`:
  - `hideFolderRequest`: Tests JSON serialization of `Requests.hideFolder(123L)`.
  - `setHiddenFoldersRequest`: Tests JSON serialization of `Requests.setHiddenFolders(...)`.

---

## Verification & Build Results
1. Executed unit tests and release assemble:
   ```bash
   buildlock ./gradlew assembleRelease testDebugUnitTest
   ```
   **Result**: `BUILD SUCCESSFUL` (all 26 unit tests passed, release APK built and signed).
2. Release Artifact:
   - Output path: `app/build/outputs/apk/release/app-release.apk` (signed release APK).
3. Git Status & Remote:
   - Branch `feat/hidden-folders` pushed to `origin/feat/hidden-folders`.
