# AndroidAPS Architecture

> **AndroidAPS** is an open-source artificial pancreas system (APS) for Android devices. It reads continuous glucose monitor (CGM) data, runs a control algorithm, and commands an insulin pump to automate insulin delivery for people with Type 1 diabetes.

## Table of Contents

- [High-Level Overview](#high-level-overview)
- [System Architecture Diagram](#system-architecture-diagram)
- [Core Design Principles](#core-design-principles)
- [Layered Architecture](#layered-architecture)
- [Plugin System](#plugin-system)
- [Closed-Loop Algorithm Flow](#closed-loop-algorithm-flow)
- [Data Flow](#data-flow)
- [Event-Driven Communication](#event-driven-communication)
- [Dependency Injection](#dependency-injection)
- [Constraint System](#constraint-system)
- [Build System](#build-system)

---

## High-Level Overview

AndroidAPS connects three physical components through software:

```
┌─────────────┐      ┌──────────────┐      ┌──────────────┐
│  CGM Sensor  │─────▶│  AndroidAPS  │─────▶│  Insulin     │
│  (Dexcom,    │ BG   │  (Android    │ Cmds │  Pump        │
│   Libre...)  │ data │   Phone)     │      │  (Dana, Omni │
└─────────────┘      └──────────────┘      │   pod, etc.) │
                            │               └──────────────┘
                            │
                     ┌──────▼──────┐
                     │  Cloud Sync  │
                     │  (Nightscout,│
                     │   Tidepool)  │
                     └─────────────┘
```

## System Architecture Diagram

```mermaid
graph TB
    subgraph "User Interface Layer"
        MA[MainActivity]
        Frag[Plugin Fragments]
        Wear[Wear OS]
        Widget[Home Widget]
    end

    subgraph "Application Layer"
        MainApp[MainApp<br/>DaggerApplication]
        CB[ConfigBuilder]
        AP[ActivePlugin]
    end

    subgraph "Plugin Layer"
        subgraph "APS Plugins"
            AMA[OpenAPS AMA]
            SMB[OpenAPS SMB]
            AISF[AutoISF]
        end
        subgraph "Source Plugins"
            Dex[Dexcom]
            Libre[Libre]
            NSS[NS Client Source]
        end
        subgraph "Pump Plugins"
            Dana[Dana RS/i]
            Omni[Omnipod]
            Med[Medtronic]
            VP[Virtual Pump]
        end
        subgraph "Other Plugins"
            Auto[Automation]
            Sens[Sensitivity]
            Ins[Insulin Curves]
            Smooth[Smoothing]
        end
    end

    subgraph "Core Layer"
        Loop[Loop Plugin]
        IOB[IobCobCalculator]
        CQ[CommandQueue]
        Constr[ConstraintsChecker]
        RxBus[RxBus Event Bus]
        Prof[Profile Manager]
    end

    subgraph "Data Layer"
        DB[(Room Database<br/>v31)]
        Repo[AppRepository]
        PL[PersistenceLayer]
    end

    subgraph "Sync Layer"
        NS[Nightscout Sync]
        TP[Tidepool]
        XD[xDrip+]
        Garmin[Garmin]
    end

    MA --> AP
    Frag --> AP
    MainApp --> CB
    CB --> AP

    AP --> Loop
    Loop --> SMB
    Loop --> AMA
    Loop --> AISF
    Loop --> IOB
    Loop --> CQ
    Loop --> Constr

    CQ --> Dana
    CQ --> Omni
    CQ --> Med
    CQ --> VP

    Dex --> DB
    Libre --> DB
    NSS --> DB

    IOB --> Repo
    Loop --> Repo
    Repo --> DB

    NS --> PL
    TP --> PL
    PL --> DB

    RxBus -.->|Events| MA
    RxBus -.->|Events| Loop
    RxBus -.->|Events| NS
```

## Core Design Principles

| Principle | Implementation |
|-----------|---------------|
| **Modularity** | 49 Gradle modules with clear boundaries |
| **Plugin Architecture** | All major features are swappable plugins |
| **Safety First** | Multi-layer constraint system limits all insulin delivery |
| **Reactive** | RxJava3 event bus for decoupled communication |
| **Offline-First** | Room database as single source of truth |
| **Testability** | Interface-driven design with Dagger DI |

## Layered Architecture

```mermaid
graph LR
    subgraph "Layers (top to bottom)"
        direction TB
        UI["<b>UI Layer</b><br/>Activities, Fragments, Wear, Widgets"]
        APP["<b>Application Layer</b><br/>MainApp, ConfigBuilder, ActivePlugin"]
        PLUGIN["<b>Plugin Layer</b><br/>APS, Sources, Pumps, Constraints, Sync"]
        CORE["<b>Core Layer</b><br/>Interfaces, Data Models, Utilities"]
        DATA["<b>Data Layer</b><br/>Room DB, Repository, PersistenceLayer"]
    end

    UI --> APP
    APP --> PLUGIN
    PLUGIN --> CORE
    CORE --> DATA
```

### Layer Responsibilities

**UI Layer** (`/app`, `/ui`, `/wear`)
- Activities and Fragments for user interaction
- Wear OS companion app
- Home screen widget
- No business logic — delegates to plugins

**Application Layer** (`/app`)
- `MainApp` bootstraps Dagger, Firebase, and plugin lifecycle
- `ConfigBuilder` manages plugin selection and initialization
- `ActivePlugin` provides access to currently selected plugin instances

**Plugin Layer** (`/plugins`, `/pump`)
- Self-contained features implementing core interfaces
- Each plugin has its own Dagger module, preferences, and optional UI fragment
- 10 plugin types: APS, Pump, Source, Insulin, Sensitivity, Constraints, Sync, Automation, Smoothing, Configuration

**Core Layer** (`/core`, `/implementation`)
- Interface contracts (`/core/interfaces`)
- Data models (`/core/data`)
- Shared utilities (`/core/utils`)
- Preference key definitions (`/core/keys`)
- Graph rendering (`/core/graph`)

**Data Layer** (`/database`)
- Room database with 20+ entities
- Repository pattern with reactive queries (RxJava3)
- Transaction system for atomic operations
- Migration support (version 20 → 31)

## Plugin System

### Plugin Lifecycle

```mermaid
stateDiagram-v2
    [*] --> NOT_INITIALIZED : App starts
    NOT_INITIALIZED --> ENABLED : setPluginEnabled(true)
    NOT_INITIALIZED --> DISABLED : setPluginEnabled(false)
    ENABLED --> DISABLED : setPluginEnabled(false)
    DISABLED --> ENABLED : setPluginEnabled(true)

    ENABLED : onStart() called
    ENABLED : Plugin is operational
    DISABLED : onStop() called
    DISABLED : Plugin is dormant
```

### Plugin Type Hierarchy

```mermaid
classDiagram
    class PluginBase {
        <<abstract>>
        +pluginDescription: PluginDescription
        +isEnabled(): Boolean
        +setPluginEnabled(type, state)
        +onStart()
        +onStop()
    }

    class PluginBaseWithPreferences {
        <<abstract>>
        +preferences: Preferences
        +preferenceKeys: List
    }

    class PumpPluginBase {
        <<abstract>>
        +commandQueue: CommandQueue
        +pumpDescription: PumpDescription
    }

    PluginBase <|-- PluginBaseWithPreferences
    PluginBaseWithPreferences <|-- PumpPluginBase
    PluginBase <|.. APS
    PluginBase <|.. BgSource
    PluginBase <|.. Insulin
    PluginBase <|.. Sensitivity
    PumpPluginBase <|.. DanaRSPlugin
    PumpPluginBase <|.. OmnipodDashPlugin
    PumpPluginBase <|.. MedtronicPlugin
```

### Plugin Types

| Type | Purpose | Examples |
|------|---------|---------|
| `APS` | Closed-loop algorithm | OpenAPS SMB, AMA, AutoISF |
| `PUMP` | Insulin pump driver | Dana RS, Omnipod Dash, Medtronic |
| `BGSOURCE` | CGM data source | Dexcom, Libre, NSClient |
| `INSULIN` | Insulin absorption curves | Rapid-Acting, Ultra-Rapid, Lyumjev |
| `SENSITIVITY` | Autosens algorithms | Oref1, AAPS, Weighted Average |
| `CONSTRAINTS` | Safety limits | Safety, Objectives |
| `SYNC` | Cloud synchronization | Nightscout, Tidepool, xDrip+ |
| `AUTOMATION` | Rule-based actions | Automation engine |
| `SMOOTHING` | BG data smoothing | Average, Exponential |
| `LOOP` | Loop coordinator | LoopPlugin |

## Closed-Loop Algorithm Flow

```mermaid
sequenceDiagram
    participant CGM as CGM Sensor
    participant DB as Database
    participant Bus as RxBus
    participant Loop as LoopPlugin
    participant APS as APS Algorithm
    participant IOB as IobCobCalculator
    participant CC as ConstraintsChecker
    participant CQ as CommandQueue
    participant Pump as Pump Driver

    CGM->>DB: Store GlucoseValue
    DB->>Bus: EventNewBgReading
    Bus->>Loop: Trigger invoke()

    Note over Loop: Validate preconditions:<br/>Mode != DISABLED<br/>Profile loaded<br/>Pump ready<br/>Queue not busy

    Loop->>APS: invoke(initiator, tempBasalFallback)
    APS->>IOB: calculateFromTreatmentsAndTemps()
    APS->>IOB: getCobInfo()
    APS->>IOB: getMealData()
    IOB-->>APS: IobTotal, CobInfo, MealData

    Note over APS: DetermineBasal algorithm:<br/>Calculate target rate<br/>Calculate SMB amount<br/>Generate predictions

    APS-->>Loop: APSResult (rate, SMB, predictions)

    Loop->>CC: applyBasalConstraints(rate)
    Loop->>CC: applyBolusConstraints(smb)
    CC-->>Loop: Constrained values + reasons

    alt Closed Loop Mode
        Loop->>CQ: tempBasalAbsolute(rate)
        CQ->>Pump: setTempBasalAbsolute()
        Pump-->>CQ: PumpEnactResult
        CQ-->>Loop: TBR success

        opt SMB Requested
            Loop->>CQ: bolus(smbAmount)
            CQ->>Pump: deliverTreatment()
            Pump-->>CQ: PumpEnactResult
        end
    else Open Loop Mode
        Loop->>Bus: EventNewOpenLoopNotification
        Note over Bus: User must approve
    end

    Loop->>DB: Store APSResult
    Loop->>Bus: EventLoopUpdateGui
```

### Algorithm Steps (DetermineBasal)

1. **Read current glucose** — latest GV + delta + trend
2. **Calculate IOB** — insulin on board from boluses + temp basals
3. **Calculate COB** — carbs on board from meal entries
4. **Run autosensitivity** — adjust ISF/CR based on recent data
5. **Determine target** — apply temp targets if active
6. **Calculate eventual BG** — predict where BG is heading
7. **Set temp basal rate** — increase/decrease basal to reach target
8. **Calculate SMB** — optional micro-bolus for faster correction
9. **Apply safety constraints** — max IOB, max basal, max SMB limits

## Data Flow

### Blood Glucose Data Pipeline

```mermaid
flowchart LR
    A[CGM Sensor] -->|Bluetooth/App| B[Source Plugin]
    B -->|GlucoseValue| C[Database]
    C -->|Observable| D[Smoothing Plugin]
    D -->|Smoothed data| E[GlucoseStatusProvider]
    E -->|delta, trend| F[APS Algorithm]
    F -->|APSResult| G[Loop Plugin]
    G -->|Commands| H[Pump]

    C -->|Observable| I[Overview UI]
    C -->|Observable| J[Graph Rendering]
    C -->|Sync| K[Nightscout]
```

### Treatment Data Flow

```mermaid
flowchart TB
    subgraph "Input Sources"
        User[User Input<br/>Bolus Wizard]
        APS[APS Algorithm<br/>SMB/TBR]
        Import[NS Import]
    end

    subgraph "Command Processing"
        CQ[CommandQueue]
        Pump[Pump Driver]
    end

    subgraph "Data Storage"
        DB[(Database)]
        PS[PumpSync]
    end

    subgraph "Consumers"
        IOB[IOB Calculator]
        COB[COB Calculator]
        UI[Overview UI]
        Sync[NS/Tidepool Sync]
    end

    User -->|DetailedBolusInfo| CQ
    APS -->|TBR/SMB| CQ
    CQ -->|Execute| Pump
    Pump -->|Confirm| PS
    PS -->|Store| DB
    Import -->|Store| DB

    DB -->|Query| IOB
    DB -->|Query| COB
    DB -->|Observable| UI
    DB -->|Sync| Sync
```

## Event-Driven Communication

AndroidAPS uses an **RxBus** (publish-subscribe event bus built on RxJava3) for decoupled communication between components.

```mermaid
flowchart LR
    subgraph "Publishers"
        DB[Database Changes]
        Pump[Pump Events]
        Loop[Loop Results]
        Prefs[Preference Changes]
    end

    RxBus((RxBus))

    subgraph "Subscribers"
        UI[UI Fragments]
        Sync[Sync Plugins]
        Auto[Automation]
        Wear[Wear Plugin]
    end

    DB -->|EventNewBgReading<br/>EventTempBasalChange| RxBus
    Pump -->|EventPumpStatusChanged<br/>EventQueueChanged| RxBus
    Loop -->|EventLoopUpdateGui<br/>EventAPSCalculationFinished| RxBus
    Prefs -->|EventPreferenceChange| RxBus

    RxBus -->|subscribe| UI
    RxBus -->|subscribe| Sync
    RxBus -->|subscribe| Auto
    RxBus -->|subscribe| Wear
```

### Key Events

| Event | Trigger | Consumers |
|-------|---------|-----------|
| `EventNewBgReading` | New CGM value stored | Loop, Overview |
| `EventTempBasalChange` | TBR started/stopped | Overview, NS Sync |
| `EventLoopUpdateGui` | Loop cycle complete | Overview UI |
| `EventAPSCalculationFinished` | APS result ready | Loop, Overview |
| `EventQueueChanged` | Command queue update | Overview status |
| `EventPreferenceChange` | Setting changed | All plugins |
| `EventAppExit` | App shutting down | All plugins |

## Dependency Injection

AndroidAPS uses **Dagger 2** with `dagger.android` for dependency injection across 49 modules.

```mermaid
graph TB
    subgraph "AppComponent (Singleton)"
        AppModule[AppModule]
        ActModule[ActivitiesModule]
        RecModule[ReceiversModule]
    end

    subgraph "Feature Modules"
        DBMod[DatabaseModule]
        ImplMod[ImplementationModule]
        SharedMod[SharedImplModule]
    end

    subgraph "Plugin Modules"
        APSMod[ApsModule]
        SrcMod[SourceModule]
        SyncMod[SyncModule]
        AutoMod[AutomationModule]
    end

    subgraph "Pump Modules"
        DanaMod[DanaModule]
        OmniMod[OmnipodModule]
        MedMod[MedtronicModule]
    end

    AppModule --> DBMod
    AppModule --> ImplMod
    AppModule --> SharedMod
    AppModule --> APSMod
    AppModule --> SrcMod
    AppModule --> SyncMod
    AppModule --> AutoMod
    AppModule --> DanaMod
    AppModule --> OmniMod
    AppModule --> MedMod
```

### Key Bindings

- `ActivePlugin` — resolves to currently selected plugin for each type
- `CommandQueue` — singleton serializing pump commands
- `AppRepository` — singleton database access
- `RxBus` — singleton event bus
- `Preferences` — type-safe preference access
- `ResourceHelper` — string/resource resolution

## Constraint System

The constraint system is a **chain-of-responsibility** pattern where every enabled plugin implementing `PluginConstraints` can restrict insulin delivery values.

```mermaid
flowchart LR
    Input["Requested Value<br/>(e.g. 5.0 U SMB)"] --> Safety[Safety Plugin]
    Safety -->|"max 3.0 U"| Objectives[Objectives Plugin]
    Objectives -->|"max 3.0 U"| Pump[Pump Constraints]
    Pump -->|"max 2.0 U"| APS[APS Plugin]
    APS -->|"max 2.0 U"| Output["Final Value<br/>2.0 U + reasons"]

    style Output fill:#f96,stroke:#333
```

### Constraint Types

- **Boolean constraints**: Is loop allowed? Is SMB allowed? Is closed loop allowed?
- **Value constraints**: Max basal rate, max bolus, max IOB, max carbs
- **Each constraint carries reasons**: Human-readable explanations of why a value was limited

## Build System

- **Gradle 8.13** with Kotlin DSL and KSP annotation processing
- **49 modules** included in `settings.gradle`
- **Centralized versions** in `gradle/libs.versions.toml`
- **Product flavors**: `full`, `pumpcontrol`, `aapsclient`, `aapsclient2`
- **Target**: compileSdk 36, minSdk 31, Java 21
- **Key dependencies**: Dagger 2.57, Room 2.8, RxJava3, Retrofit 3, OkHttp 5, Kotlin 2.2
