package info.nightscout.androidaps.plugins.pump.medtronic.defs;

import java.util.HashMap;

/**
 * Application: GGC - GNU Gluco Control
 * Plug-in: Pump Tool (support for Pump devices)
 * <p>
 * See AUTHORS for copyright information.
 * <p>
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free
 * Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 * <p>
 * Filename: PumpBolusType Description: Pump Bolus Types
 * <p>
 * Author: Andy {andy@atech-software.com}
 */

public enum PumpBolusType // implements CodeEnumWithTranslation
{
    None(0, "NONE"), //
    Normal(1, "BOLUS_STANDARD"), //
    Audio(2, "BOLUS_AUDIO"), //
    Extended(3, "BOLUS_SQUARE", "AMOUNT_SQUARE=%s;DURATION=%s"), //
    Multiwave(4, "BOLUS_MULTIWAVE", "AMOUNT=%s;AMOUNT_SQUARE=%s;DURATION=%s");

    static String[] descriptions;
    // static HashMap<String, CodeEnumWithTranslation> translationMapping = new HashMap<String,
    // CodeEnumWithTranslation>();
    static HashMap<Integer, PumpBolusType> codeMapping = new HashMap<Integer, PumpBolusType>();
    private static boolean translated;

    static {
        for (PumpBolusType pbt : values()) {
            codeMapping.put(pbt.code, pbt);
        }
    }

    // public static void translateKeywords(I18nControlAbstract ic)
    // {
    // if (translated)
    // return;
    //
    // for (PumpBolusType pbt : values())
    // {
    // pbt.setTranslation(ic.getMessage(pbt.i18nKey));
    // translationMapping.put(pbt.getTranslation(), pbt);
    // }
    //
    // String[] bolusDescriptions = { ic.getMessage("SELECT_BOLUS_TYPE"), //
    // ic.getMessage("BOLUS_STANDARD"), //
    // ic.getMessage("BOLUS_AUDIO"), //
    // ic.getMessage("BOLUS_SQUARE"), //
    // ic.getMessage("BOLUS_MULTIWAVE"), };
    //
    // descriptions = bolusDescriptions;
    //
    // translated = true;
    // }

    int code;
    String i18nKey;
    String translation;
    String valueTemplate;


    PumpBolusType(int code, String i18nKey) {
        this.code = code;
        this.i18nKey = i18nKey;
    }


    PumpBolusType(int code, String i18nKey, String valueTemplate) {
        this.code = code;
        this.i18nKey = i18nKey;
        this.valueTemplate = valueTemplate;
    }


    public static PumpBolusType getByCode(int code) {
        if (codeMapping.containsKey(code)) {
            return codeMapping.get(code);
        } else {
            return PumpBolusType.None;
        }
    }


    /**
     * Get Descriptions (array)
     *
     * @return array of strings with description
     */
    public static String[] getDescriptions() {
        return descriptions;
    }


    public String getTranslation() {
        return translation;
    }


    public void setTranslation(String translation) {
        this.translation = translation;
    }


    public int getCode() {
        return code;
    }


    public String getI18nKey() {
        return i18nKey;
    }


    public String getName() {
        return this.name();
    }
}
