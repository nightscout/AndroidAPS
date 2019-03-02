package info.nightscout.androidaps.plugins.pump.medtronic.comm.history;

/**
 * Application: GGC - GNU Gluco Control
 * Plug-in: GGC PlugIn Base (base class for all plugins)
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
 * Filename: Record Decode Status Description: Record Decode Status shows if entry was decoded. Used mostly for
 * statistics.
 * <p>
 * Author: Andy {andy@atech-software.com}
 */

public enum RecordDecodeStatus {
    OK("OK     "), //
    Ignored("IGNORE "), //
    NotSupported("N/A YET"), //
    Error("ERROR  "), //
    WIP("WIP    "), //
    Unknown("UNK    ");

    String description;


    RecordDecodeStatus(String description) {
        this.description = description;

    }


    public String getDescription() {
        return description;
    }
}
