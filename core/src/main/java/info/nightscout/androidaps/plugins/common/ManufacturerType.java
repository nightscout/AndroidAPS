package info.nightscout.androidaps.plugins.common;

public enum ManufacturerType {

    AndroidAPS("AndroidAPS"),
    Medtronic("Medtronic"),
    Sooil("SOOIL"),

    Tandem("Tandem"),
    Insulet("Insulet"),
    Animas("Animas"),
    Cellnovo("Cellnovo"),
    Roche("Roche"),
    Ypsomed("Ypsomed");



    private final String description;

    ManufacturerType(String description) {

        this.description = description;
    }

    public String getDescription() {
        return description;
    }


}
