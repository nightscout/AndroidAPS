package app.aaps.pump.common.driver.refresh

enum class PumpDataRefreshAction {
    Add,
    GetData,
    Delete,
    AddSameAsOther  // this can be used if you want to add refresh that happens at same time as some other refreshType
}