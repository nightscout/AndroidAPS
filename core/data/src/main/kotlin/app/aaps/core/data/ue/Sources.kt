package app.aaps.core.data.ue

enum class Sources {
    TreatmentDialog,
    InsulinDialog,
    CarbDialog,
    WizardDialog,
    QuickWizard,
    ExtendedBolusDialog,
    TTDialog,
    ProfileSwitchDialog,
    LoopDialog,
    TempBasalDialog,
    CalibrationDialog,
    FillDialog,
    BgCheck,
    SensorInsert,
    BatteryChange,
    Note,
    Exercise,
    Question,
    Announcement,
    SettingsExport,
    Actions,            //From Actions plugin
    Automation,         //From Automation plugin
    Autotune,           //From Autotune plugin
    BG,                 //From BG plugin => Add One Source per BG Source for Calibration or Sensor Change
    Aidex,
    Dexcom,
    Eversense,
    Glimp,
    MM640g,
    NSClientSource,
    PocTech,
    Tomato,
    Glunovo,
    Intelligo,
    Xdrip,
    LocalProfile,       //From LocalProfile plugin
    Loop,               //From Loop plugin
    Maintenance,        //From Maintenance plugin
    NSClient,           //From NSClient plugin
    NSProfile,          //From NSProfile plugin
    Objectives,         //From Objectives plugin
    Pump,               //To update with one Source per pump
    Dana,               //Only one UserEntry in Common module Dana
    DanaR,
    DanaRC,
    DanaRv2,
    DanaRS,
    DanaI,
    DiaconnG8,
    Insight,
    Combo,
    Medtronic,
    Omnipod,            //No entry currently
    OmnipodEros,
    OmnipodDash,        //No entry currently
    EOPatch2,
    Equil,
    Medtrum,
    MDI,
    VirtualPump,
    Random,
    SMS,                //From SMS plugin
    Treatments,         //From Treatments plugin
    Wear,               //From Wear plugin
    Food,               //From Food plugin
    ConfigBuilder,      //From ConfigBuilder Plugin
    Overview,           //From OverViewPlugin
    Ottai,              //From Ottai Plugin
    Stats,              //From Stat Activity
    Aaps,               // MainApp
    BgFragment,
    Garmin,
    Database,           // for PersistenceLayer
    Unknown,             //if necessary
    SyaiTag
    ;
}