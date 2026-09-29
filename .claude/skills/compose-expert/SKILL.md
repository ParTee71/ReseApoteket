---
name: compose-expert
description: >
  Jetpack Compose-expert för ReseApotekets Android-UI (enbart Android, Material 3 Expressive,
  Navigation 3). Använd när användaren nämner Compose-API:er (@Composable, remember,
  LaunchedEffect, MaterialTheme, LazyColumn, Modifier, recomposition, NavDisplay, NavKey,
  MotionScheme, animation) eller skriver på svenska om skärm, vy, composable, rekomposition,
  animation, rörelse, navigering, skärmbyte, prestanda i UI eller "skärmen laggar". Laddas
  tillsammans med android-dev, och före shared-ui-components/ui-style när en skärm eller
  komponent byggs – de reglerna går före generella Compose-råd.
---

## When this skill applies

### Compose API mentions
`@Composable`, `remember`, `mutableStateOf`, `derivedStateOf`, `rememberSaveable`,
`LaunchedEffect`, `DisposableEffect`, `SideEffect`, `rememberCoroutineScope`,
`Scaffold`, `NavDisplay`, `NavKey`, `MaterialTheme`, `MotionScheme`, `ColorScheme`,
`Typography`, `LazyColumn`, `LazyRow`, `LazyVerticalGrid`, `HorizontalPager`,
`Modifier`, `Modifier.Node`, `recomposition`, `CompositionLocal`.

### Design system / design-to-code
`atomic design`, `atoms`, `molecules`, `organisms`, `templates`,
`design tokens`, `design system`, `component library`, `reusable component`,
`Figma to Compose`, `design to compose`, `build this UI`, `implement this design`.

### Casual phrasing
"my compose screen is slow", "my recomposition is broken",
"how do I pass data between screens", "Android UI", "Kotlin UI",
"compose layout", "compose navigation", "compose animation".

## Routing (skills som finns i detta repo)

| Fråga gäller | Gå till |
|---|---|
| Vilken komponent/ram ska användas, förbjudna M3-anrop, variant av en komponent | `shared-ui-components` (regel 4 – går före allt nedan) |
| Färger, typografi, former, rörelse, texter, mockup | `ui-style` |
| TalkBack, tryckytor, stor text | `accessibility-compose` |
| StateFlow, `stateIn`, `combine`, effekter | `kotlin-flows` |
| Coroutines i ViewModel, avbrytning | `kotlin-coroutines` |
| Skärmdumpar, Robolectric, kontraktstester | `testing-strategy` |

Allt annat (state, side effects, prestanda, listor, modifiers, animation, Navigation 3)
besvaras med principerna nedan.

## Workflow

When helping with Compose code:

### 1. Understand the request
- What Compose layer is involved? (Runtime, UI, Foundation, Material3, Navigation)
- Is this a state problem, layout problem, performance problem, or architecture question?

### 2. Analyze the design (if visual reference provided)
- If the user shares a mockup (Design canvas), screenshot, or design spec, decompose it into shared frames + catalog components
- Map the design to `ui/theme`-tokens and the component catalog (`shared-ui-components`); an element that is missing there becomes a new shared component, never a local one
- Identify animation needs

### 3. Apply and verify
- Write code that follows established patterns
- Flag anti-patterns in existing code
- Suggest the minimal correct solution — don't over-engineer

## Key Principles

1. **Compose thinks in three phases**: Composition → Layout → Drawing. State reads in each phase only trigger work for that phase and later ones.

2. **Recomposition is frequent and cheap** — but only if you help the compiler skip unchanged scopes. Use stable types, avoid allocations in composable bodies.

3. **Modifier order matters**. `Modifier.padding(16.dp).background(Color.Red)` is visually different from `Modifier.background(Color.Red).padding(16.dp)`.

4. **State should live as low as possible** and be hoisted only as high as needed. Don't put everything in a ViewModel just because you can.

5. **Side effects exist to bridge Compose's declarative world with imperative APIs**. Use the right one for the job — misusing them causes bugs that are hard to trace.

## ReseApotekets mönster

Projektet använder:
- **Material 3 Expressive** via `ui/theme` (`MaterialExpressiveTheme`, `MotionScheme.expressive()`, `AppTypography`, `AppShapes`, `Spacing`). Inga färger, former, textstilar eller hörn/höjd-dp i feature-kod.
- **Navigation 3**: `@Serializable` `NavKey`, `rememberNavBackStack`, `NavDisplay` + `entryProvider`, övergångar definierade en gång i `navigation/Transitions`. Ingen `NavHost`/`NavController`.
- **MVVM** med `StateFlow<UiState>` + `collectAsStateWithLifecycle()` och sealed events (`vm.onEvent(...)`).
- **Hilt**: `hiltViewModel()` på skärmnivå; ViewModel-scope per nav-post via `lifecycle-viewmodel-navigation3`.
- **Inga bildbibliotek** – avatarer är emoji, ikoner Material Symbols.

> Expressive- och Nav3-API:er är nya. Använd bara namn och signaturer som verifierats i
> Gradle-skelettet (#11) och står i `ARKITEKTUR.md`; gissa aldrig ett API-namn.

### Skärmstruktur
En funktionsskärm består av en `@Composable fun XScreen(vm: XViewModel = hiltViewModel())`
som samlar state och skickar det till en delad ram:
```kotlin
@Composable
fun PersonsScreen(vm: PersonsViewModel = hiltViewModel(), onOpen: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    EntityListScreen(
        title = stringResource(R.string.persons_title),
        state = state.list,                                  // ListUiState<Person>
        row = { person -> PersonRow(person, onClick = { onOpen(person.id) }) },
        onAdd = { vm.onEvent(PersonsEvent.Add) },
        onHide = { vm.onEvent(PersonsEvent.Archive(it.id)) },
        onUndo = { vm.onEvent(PersonsEvent.UndoArchive(it.id)) },
    )
}
```
Barn-composables tar typat state och callbacks – aldrig ViewModel. Laddning, tomt läge,
fel, lägg till och ångra hanteras av ramen, inte av skärmen.

### Fel, laddning och knappar
Hanteras av de delade delarna: `DataError.toMessage()` i snackbar, `AppLoading`,
`AppButton(loading = …)`. Skriv aldrig egen `CircularProgressIndicator` i en knapp eller
egen feltext med `colorScheme.error`.

### Kort och sektioner
`ApotekCard` + `SectionHeader`, aldrig `Card`/`ElevatedCard` + `HorizontalDivider` med egen padding.

## Anti-Patterns to Avoid

- Never call `remember` inside conditional blocks or loops
- Never read `StateFlow.value` in a composable — always use `collectAsStateWithLifecycle()`
- Never pass `Context` to a ViewModel constructor — use `@ApplicationContext` or `activityContext` passed from the composable for operations that need it
- Never mutate the Navigation 3 back stack from a ViewModel — use callbacks or effects
- `LaunchedEffect(Unit)` for one-time setup; `LaunchedEffect(key)` when the effect should re-run on key change
- `DisposableEffect` for resources that need cleanup (listeners, subscriptions)
- Use `rememberLauncherForActivityResult` for Activity Result API — never call `startActivityForResult` directly
