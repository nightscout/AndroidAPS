package info.nightscout.androidaps.plugins.general.themeselector.model

/**
 * Created by Pankaj on 03-11-2017.
 */
class Theme {

    var id = 0
    var primaryColor: Int
    var primaryDarkColor: Int
    var accentColor: Int

    constructor(primaryColor: Int, primaryDarkColor: Int, accentColor: Int) {
        this.primaryColor = primaryColor
        this.primaryDarkColor = primaryDarkColor
        this.accentColor = accentColor
    }

    constructor(id: Int, primaryColor: Int, primaryDarkColor: Int, accentColor: Int) {
        this.id = id
        this.primaryColor = primaryColor
        this.primaryDarkColor = primaryDarkColor
        this.accentColor = accentColor
    }

}