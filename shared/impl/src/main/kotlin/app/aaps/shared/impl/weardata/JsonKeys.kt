package app.aaps.shared.impl.weardata

enum class JsonKeys(val key: String) {
    METADATA("metadata"),
    ENABLESECOND("enableSecond"),
    HIGHCOLOR("highColor"),
    MIDCOLOR("midColor"),
    LOWCOLOR("lowColor"),
    LOWBATCOLOR("lowBatColor"),
    CARBCOLOR("carbColor"),
    BASALBACKGROUNDCOLOR("basalBackgroundColor"),
    BASALCENTERCOLOR("basalCenterColor"),
    GRIDCOLOR("gridColor"),
    POINTSIZE("pointSize"),
    WIDTH("width"),
    HEIGHT("height"),
    TOPMARGIN("topmargin"),
    LEFTMARGIN("leftmargin"),
    ROTATION("rotation"),
    VISIBILITY("visibility"),
    TEXTSIZE("textsize"),
    TEXTVALUE("textvalue"),
    GRAVITY("gravity"),
    FONT("font"),
    FONTSTYLE("fontStyle"),
    FONTCOLOR("fontColor"),
    COLOR("color"),
    ALLCAPS("allCaps"),
    DAYNAMEFORMAT("dayNameFormat"),
    MONTHFORMAT("monthFormat"),
    BACKGROUND("background"),       // Background image for textView
    LEFTOFFSET("leftOffset"),
    TOPOFFSET("topOffset"),
    ROTATIONOFFSET("rotationOffset"),
    DYNDATA("dynData"),             //Bloc of DynDatas definition, and DynData keyValue within view
    VALUEKEY("valueKey"),           // Indentify which value (default is View Value)
    MINDATA("minData"),             // Min data Value (default defined for each value, note unit mg/dl for all bg, deltas)
    MAXDATA("maxData"),             // Max data idem min data (note all value below min or above max will be considered as equal min or mas)
    MINVALUE("minValue"),           // min returned value (when data value equals minData
    MAXVALUE("maxValue"),           //
    INVALIDVALUE("invalidValue"),
    IMAGE("image"),
    INVALIDIMAGE("invalidImage"),
    INVALIDCOLOR("invalidColor"),
    INVALIDFONTCOLOR("invalidFontColor"),
    INVALIDTEXTSIZE("invalidTextSize"),
    TWINVIEW("twinView"),
    TOPOFFSETTWINHIDDEN("topOffsetTwinHidden"),
    LEFTOFFSETTWINHIDDEN("leftOffsetTwinHidden"),
    DYNPREF("dynPref"),
    DYNPREFCOLOR("dynPrefColor"),
    PREFKEY("prefKey"),
    INVALIDTOPOFFSET("invalidTopOffset"),
    INVALIDLEFTOFFSET("invalidLeftOffset"),
    INVALIDROTATIONOFFSET("invalidRotationOffset"),
    DEFAULT("default")
}