# SKILL.md — Friday STM Engineering Guidelines

> **Audience:** Any AI coding assistant (Claude Code, Cursor, Copilot, etc.) working on this repository.
> **Status:** These are **strict, non-negotiable rules**. When a rule conflicts with a quick shortcut, the rule wins. If you believe a rule must be broken, stop and ask the human first.

---

## 0. Project Context

**Friday STM** is a native Android attendance app for enforcing student discipline during Friday-morning activities at SMK Negeri 1 Cimahi. It is **state-driven by time and student grade**:

- **Fase 1 (06:30–08:00) — Pembiasaan:** students split into Ta'lim / Larkam / Senam; UI is filtered by a **weekly rotation per grade**; geofence + selfie check-in.
- **Fase 2 (08:00–08:30) — Check-out:** mandatory before KBM.

> The legacy **Apel** phase (06:00–06:30) was removed; the Friday flow opens directly into Pembiasaan at 06:30. Apel artifacts remain read-only for historical attendance documents.

The full task breakdown lives in [`app/task.md`](app/task.md). Read it before implementing any feature. This file defines **how** to build; `task.md` defines **what** to build.

---

## 1. Tech Stack (Mandatory / Forbidden)

### ✅ Mandatory
| Concern | Technology |
|---|---|
| Language | **Kotlin only** (target JVM 11) |
| UI | **Jetpack Compose + Material 3** |
| Architecture | **MVVM** (unidirectional data flow) |
| Async | **Kotlin Coroutines + Flow** |
| Backend | **Firebase** — Auth, Firestore |
| Image storage | **Cloudinary** unsigned upload preset (OkHttp multipart) — *not* Firebase Storage |
| Location | `FusedLocationProviderClient` (Play Services Location) |
| Camera | **CameraX** |
| Image loading | **Coil** (`coil-compose`) |
| Navigation | **Navigation-Compose** |

### ❌ Forbidden (do not introduce without explicit human approval)
- **Java** source files. This is a Kotlin-only codebase.
- **XML layout files** (`res/layout/*.xml`), `findViewById`, `ViewBinding`, `DataBinding`.
- Legacy Android **Views** (`RecyclerView`, `Fragment`, `Activity`-per-screen), **except** where a Compose wrapper is genuinely required.
- Firebase **callback listeners** (`addOnSuccessListener` / `addOnCompleteListener`) in application code — use coroutines (see §5).
- `LiveData`, RxJava, AsyncTask, `runBlocking` in production code.
- Blocking the main thread with any I/O.

### ⚠️ Narrow, Approved Exceptions
- `AndroidView { }` is allowed **only** to host non-Compose surfaces that have no Compose equivalent: **CameraX `PreviewView`** and the **map view** (Google Maps / osmdroid). Wrap them; never leak them upward.
- One single `MainActivity` (`ComponentActivity`) is the only Activity. All screens are Composables.

---

## 2. Module / Package Structure

Root package: `com.gynda.fridaystm`. Respect these layer boundaries — **dependencies point downward only** (`ui → viewmodel → data → domain`). `domain` depends on nothing Android.

```
com.gynda.fridaystm
├── data/
│   ├── model/        // Firestore data classes (User, Geofence, AttendanceRecord…)
│   ├── remote/       // Firestore data sources (suspend fns)
│   └── repository/   // Repositories exposed to ViewModels
├── domain/           // PURE Kotlin. No android.*, no Firebase, no Compose imports.
│                     //   phase engine, rotation logic, geofence math
├── ui/
│   ├── screen/       // *Screen composables (stateful holders + stateless content)
│   ├── component/    // Reusable composables (BigActionButton, DynamicPhaseCard…)
│   ├── navigation/   // AppNavHost, Routes
│   └── theme/        // Color / Type / Theme
├── viewmodel/        // ViewModels + UiState models
└── util/             // TimeProvider, LocationUtil, Constants
```

**Hard rule:** `domain/` must remain compilable as plain Kotlin and unit-testable on the JVM with **zero** Android/Firebase imports. The phase engine and rotation logic live here.

---

## 3. Architecture & State Management (MVVM)

### 3.1 Unidirectional Data Flow
```
User event ──▶ ViewModel (event handler) ──▶ Repository/domain
                     │
                     ▼
             updates StateFlow<UiState>
                     │
                     ▼
UI observes ◀── collectAsStateWithLifecycle()
```
State flows **down**, events flow **up**. Never the reverse.

### 3.2 The ViewModel — strict rules
- **MUST NOT** reference the Android framework: **no `Context`, no `Activity`, no `View`, no `Resources`, no Compose types, no `android.*` imports** (except `androidx.lifecycle.ViewModel`/`viewModelScope`).
  - Need a string? Expose a state field / resource id, resolve it in the Composable.
  - Need location or camera? That lives behind a repository/util interface injected in — the ViewModel calls a `suspend` function, it does not touch `FusedLocationProviderClient` directly.
- **MUST** expose immutable UI state as a single `StateFlow<SomeUiState>`. Use a private `MutableStateFlow` backing field:
  ```kotlin
  private val _uiState = MutableStateFlow(HomeUiState.Loading)
  val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
  ```
- **MUST** run work in `viewModelScope`. Never `GlobalScope`, never `runBlocking`.
- **MUST** model state as an exhaustive `sealed interface` / `data class` — no scattered `Boolean` flags:
  ```kotlin
  sealed interface HomeUiState {
      data object Loading : HomeUiState
      data class Ready(
          val phase: FridayPhase,
          val activeActivity: Activity?,
          val geofenceTarget: Geofence?,
          val isInsideGeofence: Boolean,
          val record: AttendanceRecord?,
      ) : HomeUiState
      data class Error(val messageResId: Int) : HomeUiState
  }
  ```
- **Time is injected, never read directly.** Use the `TimeProvider` interface, never `LocalDateTime.now()` / `System.currentTimeMillis()` inside ViewModel or domain — so the phase engine is testable.

### 3.3 The View (Composable) — strict rules
- The UI layer **only observes** state and **emits events via callbacks**. It contains no business logic, no Firestore calls, no distance math.
- Combine derived/streamed state inside the ViewModel with `combine(...)` + `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)` — **do not** stitch multiple flows together inside a Composable.

---

## 4. Jetpack Compose Best Practices

### 4.1 State Hoisting (mandatory pattern)
Every screen is split into a **stateful holder** (talks to the ViewModel) and a **stateless content** (pure UI). Only the holder knows the ViewModel exists.

```kotlin
// ✅ Stateful holder — the ONLY place that sees the ViewModel
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToCamera: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = uiState,
        onCheckInClick = viewModel::onCheckInClick,
        onCheckOutClick = viewModel::onCheckOutClick,
        onNavigateToCamera = onNavigateToCamera,
    )
}

// ✅ Stateless content — no ViewModel, fully previewable & testable
@Composable
fun HomeContent(
    state: HomeUiState,
    onCheckInClick: () -> Unit,
    onCheckOutClick: () -> Unit,
    onNavigateToCamera: () -> Unit,
    modifier: Modifier = Modifier,
) { /* render from `state`, invoke callbacks */ }
```

### 4.2 Non-negotiable Compose rules
- **Never pass a `ViewModel` into a reusable/child composable.** Pass plain state values + `() -> Unit` callbacks. Only the top-level screen holder receives the ViewModel.
- **Always** collect flows with `collectAsStateWithLifecycle()` (from `lifecycle-runtime-compose`). Never plain `collectAsState()` for lifecycle-aware app state.
- **Every** composable that draws layout accepts a `modifier: Modifier = Modifier` as its **last parameter with a default**, and applies it to the root node.
- **State hoisting for local UI state:** use `remember { mutableStateOf(...) }`; survive config changes with `rememberSaveable`.
- **Avoid unnecessary recomposition:**
  - Prefer immutable, `@Stable`/`@Immutable` state classes; avoid passing `List` where an immutable/`ImmutableList` is intended.
  - Never read a `MutableState` at a scope higher than needed; keep reads as deep in the tree as possible.
  - Use lambda references (`viewModel::onClick`) and stable keys in `LazyColumn(items, key = { it.id })`.
  - Do not allocate objects/lambdas that capture unstable params in hot paths; hoist with `remember`.
  - No side effects in composition — use `LaunchedEffect`, `rememberCoroutineScope`, `DisposableEffect`.
- **Previews:** every stateless `*Content` composable should have at least one `@Preview` driven by fake state. Previews must never touch Firebase or the ViewModel.
- **Theme:** all colors/typography come from `MaterialTheme`. No hard-coded hex colors or `sp`/`dp` magic numbers scattered in screens — centralize in `ui/theme/`.

---

## 5. Firebase & Coroutines (Repository Layer)

**Rule:** Repositories expose **`suspend` functions** (or `Flow`), never callbacks. Convert every Firebase `Task<T>` with `.await()` from `kotlinx-coroutines-play-services`.

```kotlin
// ❌ FORBIDDEN — callback listener leaks into app code
fun submitPembiasaan(stamp: PembiasaanStamp) {
    firestore.collection("attendance").document(id)
        .set(stamp)
        .addOnSuccessListener { /* ... */ }
        .addOnFailureListener { /* ... */ }
}

// ✅ REQUIRED — suspend + await, wrapped in Result
suspend fun submitPembiasaan(stamp: PembiasaanStamp): Result<Unit> = runCatching {
    firestore.collection("attendance")
        .document(docId)
        .set(stamp, SetOptions.merge())
        .await()
}
```

Additional data-layer rules:
- **Real-time streams** (e.g. today's attendance) are exposed as **`Flow`** via `callbackFlow { ... }` wrapping `addSnapshotListener`, closing the registration in `awaitClose { }`. This is the *only* sanctioned place a snapshot listener may appear, and it must be wrapped — the ViewModel sees a `Flow`, never the listener.
- All Firebase I/O runs off the main thread; suspend functions inherit the caller's dispatcher, so callers use `viewModelScope` — do not hop to `Dispatchers.IO` unless doing CPU/file work (e.g. image compression before upload → `Dispatchers.Default`/`IO`).
- **Never** expose `DocumentSnapshot`/`Task`/`QuerySnapshot` above the `data/` layer. Map to domain/`data.model` types inside the repository.
- Every network op returns a `Result<T>` (or a sealed outcome). ViewModel maps failures into `UiState.Error`; **swallowing exceptions silently is forbidden.**
- Firestore document ids are deterministic where possible (`"${uid}_${date}"`) to prevent duplicate check-ins.
- Use `FieldValue.serverTimestamp()` for authoritative time on writes; do not trust device clock for stored attendance times.

---

## 6. Domain Logic Rules (the heart of the app)

- **`resolvePhase(now: LocalDateTime): FridayPhase`** and **`activityForGrade(grade: Int, weekIndex: Int): Activity`** live in `domain/`, are **pure functions**, and have **exhaustive unit tests** before any UI is wired to them. No Android, no Firebase, no I/O inside them.
- Time enters domain code **only** as a parameter (supplied by `TimeProvider`). This is what makes 06:29→06:30 transitions testable.
- Geofence validation uses `Location.distanceBetween(...)` compared to `radiusMeter`; keep the pure comparison (`isInsideGeofence`) separate from the Android location fetch so it is unit-testable.
- The Fase 1 → Fase 2 auto-transition at 06:30 is driven by a ticking flow in the ViewModel (`stateIn`), **not** by manual user refresh.

---

## 7. Naming & Clean Code Conventions

### Composables
- **PascalCase**, and they **return `Unit`** and emit UI. Name them as **nouns describing what they are**: `HomeScreen`, `BigActionButton`, `DynamicPhaseCard`, `MiniMap`. Not `getButton`, not `renderCard`.
- Screen holder = `XxxScreen`; its stateless body = `XxxContent`. Reusable pieces live in `ui/component/`.

### Everything else
- Classes/types: `PascalCase` (`AttendanceRepository`, `FridayPhase`).
- Functions/vars: `camelCase`; event handlers on ViewModel start with `on` (`onCheckInClick`).
- Constants: `UPPER_SNAKE_CASE` in `util/Constants.kt` (radii, time boundaries, collection names) — **no magic numbers/strings** inline.
- Firestore collection/field names centralized as constants, never typed as raw strings at call sites.
- State classes end in `UiState`; sealed event/result types are explicit.
- One top-level public declaration per file where reasonable; file name matches the primary type.

### General
- Small functions, single responsibility. Business rules never live inside a `@Composable`.
- Prefer immutability: `val` over `var`, `data class`, immutable collections. Nullability is meaningful — no `!!` in production code.
- Follow official Kotlin style guide; keep formatting consistent (ktlint-compatible). No commented-out dead code.
- KDoc on public repository/domain functions explaining behavior and failure modes.

---

## 8. Security & Permissions
- **Firestore Security Rules** must enforce that a student can only read/write their own `attendance`, `larkam_runs` and their own class's `talim_summaries`; `geofences`, `rotations` and `senam_sessions` are read-only to students. Never rely on client-side checks alone. Role wire values are `student | class_rep | instructor | admin` (`util/Constants.kt` → `UserRole`) — rules must test exactly these.
- Selfies live on **Cloudinary** via an unsigned upload preset, so there are no Firebase Storage rules; the preset itself is the trust boundary (keep it write-only and folder-scoped).
- Request `ACCESS_FINE_LOCATION` and `CAMERA` at runtime, right before the action needs them; handle permanent denial gracefully.
- Do not commit secrets. `google-services.json` and API keys are configuration, not source — keep keys out of version control where applicable.
- **Time on stored attendance is server-authored.** Every `attendance` write stamps `updatedAt` with `FieldValue.serverTimestamp()`, and the rules assert `request.resource.data.updatedAt == request.time` — a tampered device clock is rejected server-side. The `"HH:mm"` `time` field is display text, never evidence.
- **Mock location is fail-closed and loud.** A fix with `LocationFix.isMock == true` puts Home into `HomeUiState.Error` (no action buttons at all) and clears the cached fix, so no check-in path can write mocked coordinates.
- Release procedure (rules deploy, Rules Playground scenarios, reference-data seeding) lives in [`RELEASE.md`](RELEASE.md).

---

## 9. Testing
- `domain/` (phase engine + rotation) requires **unit tests** covering boundaries (05:59, 06:00, 06:30, 08:00, non-Friday) and a full 3-week rotation table proving each grade cycles through all three activities with no clash.
- ViewModels are tested with a fake `TimeProvider` and fake repositories (interfaces, not Firebase).
- Stateless `*Content` composables are the target for UI/preview tests; keep them free of external dependencies so they can be tested in isolation.

---

## 10. Definition of Done (checklist for every change)
- [ ] Kotlin only; no XML layout / Java / forbidden APIs introduced.
- [ ] ViewModel free of `Context`/Android-framework/Compose references.
- [ ] UI observes `StateFlow` via `collectAsStateWithLifecycle()`; screens receive callbacks, not ViewModels.
- [ ] Repository uses `suspend`/`await()` / `callbackFlow`, not raw listeners; returns `Result`.
- [ ] New domain logic is pure and unit-tested; time is injected.
- [ ] Naming, constants, and modifiers follow §7 & §4.
- [ ] No warnings introduced; no dead/commented code.
```
