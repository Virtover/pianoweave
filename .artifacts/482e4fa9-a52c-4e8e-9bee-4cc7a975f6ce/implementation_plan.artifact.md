# Architectural Refactoring & Adaptive UI Layout Plan

Refactor `MainActivity.kt` into separate domain-specific screens, components, and states to reduce its size and complexity. Simultaneously, transform the layout into a responsive adaptive design that dynamically adjusts between portrait and landscape orientations, fixing the layout overflow and clipping issues.

## User Review Required

> [!IMPORTANT]
> **Architectural Changes:**
> - Component code will be extracted from `MainActivity.kt` into sub-packages under `ui/components/`, `ui/screens/`, and `ui/theme/`.
> - State management will be centralized into a clean state holder structure or clean UI components to ensure screen re-composition runs smoothly in both form factors.
>
> **Adaptive Screen Adaptations:**
> - **Portrait View:** Replaces the side `NavigationRail` with a standard bottom navigation bar (`NavigationBar`) or an adaptive component layout to optimize horizontal real estate.
> - **Landscape View:** Retains the `NavigationRail` but restructures the internal contents of screens (like `LearnScreen`) into multi-column layouts (e.g., input card on the left half, status feed and guidelines on the right half) to guarantee everything fits without scrolling out of view.

## Proposed Changes

### Configuration & Theme Layer

#### [NEW] [Theme.kt](file:///C:/Users/Christopher/Desktop/yt-piano/app/src/main/java/com/example/ytpiano/ui/theme/Theme.kt)
Contains the definition of the premium piano dark color scheme and sets up typography config.

***

### Shared & Adaptive Components

#### [NEW] [AdaptiveNavigation.kt](file:///C:/Users/Christopher/Desktop/yt-piano/app/src/main/java/com/example/ytpiano/ui/components/AdaptiveNavigation.kt)
Detects configuration orientation (or layout aspect ratio) and automatically switches between a left-docked `NavigationRail` (Landscape) and a bottom-docked `NavigationBar` (Portrait).

***

### Screens Refactoring

#### [NEW] [LearnScreen.kt](file:///C:/Users/Christopher/Desktop/yt-piano/app/src/main/java/com/example/ytpiano/ui/screens/LearnScreen.kt)
Exclusively manages the audio transcription flow layout.
- In **Landscape**: Arranges the main Input Card on one side and splits the Status Feed and Guide Card on the other side using a balanced `Row` layout.
- In **Portrait**: Flows sequentially in a single column.

#### [NEW] [StorageScreen.kt](file:///C:/Users/Christopher/Desktop/yt-piano/app/src/main/java/com/example/ytpiano/ui/screens/StorageScreen.kt)
Exclusively manages the offline MIDI library list, filter search text field, and card actions.

#### [NEW] [StoredSongCard.kt](file:///C:/Users/Christopher/Desktop/yt-piano/app/src/main/java/com/example/ytpiano/ui/components/StoredSongCard.kt)
Isolated reusable individual song list card widget.

***

### Activity Simplification

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Christopher/Desktop/yt-piano/app/src/main/java/com/example/ytpiano/app/src/main/java/com/example/ytpiano/MainActivity.kt)
Stripped of all business views. Serves purely as the root window entry point hosting `PianoLearnerApp` and connecting the adaptive themes.

## Verification Plan

### Automated Tests
- Run `gradle build` / `app:assembleDebug` to verify no symbol mismatches or missing dependencies.

### Manual Verification
- Deploy to emulator/device. Rotate to Portrait mode to check bottom navigation bar layout.
- Rotate to Landscape mode to confirm the split two-column dashboard structure avoids any item or button vertical clipping.
