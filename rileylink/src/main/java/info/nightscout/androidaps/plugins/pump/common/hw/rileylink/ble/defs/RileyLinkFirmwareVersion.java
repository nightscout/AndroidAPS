package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum RileyLinkFirmwareVersion {

    Version_0_0(0, 0, "0.0"), // just for defaulting
    Version_0_9(0, 9, "0.9"), //
    Version_1_0(1, 0, "1.0"), //
    Version_1_x(1, null, "1.x"), //
    Version_2_0(2, 0, "2.0"), //
    Version_2_2(2, 2, "2.2"), //
    Version_2_x(2, null, "2.x"), //
    Version_3_x(3, null, "3.x"), //
    Version_4_x(4, null, "4.x"), //
    UnknownVersion(null, null, "???"), //
    Version1(Version_0_0, Version_0_9, Version_1_0, Version_1_x), //
    Version2(Version_2_0, Version_2_2, Version_2_x), //
    Version3(Version_3_x), //
    Version4(Version_4_x), //
    Version2AndHigher(Version2, Version3, Version4);

    private static final String FIRMWARE_IDENTIFICATION_PREFIX = "subg_rfspy ";
    private static final Pattern _version_pattern = Pattern.compile(FIRMWARE_IDENTIFICATION_PREFIX
            + "([0-9]+)\\.([0-9]+)");
    static Map<String, RileyLinkFirmwareVersion> mapByVersion;

    static {
        mapByVersion = new HashMap<>();
        for (RileyLinkFirmwareVersion version : values()) {
            if (version.familyMembers == null) {
                mapByVersion.put(version.versionKey, version);
            }
        }
    }

    private List<RileyLinkFirmwareVersion> familyMembers;
    private Integer major;
    private Integer minor;
    private String versionKey = "";

    RileyLinkFirmwareVersion(Integer major, Integer minor, String versionKey) {
        this.major = major;
        this.minor = minor;
        this.versionKey = versionKey;
    }


    RileyLinkFirmwareVersion(RileyLinkFirmwareVersion... familyMembers) {
        this.familyMembers = Arrays.asList(familyMembers);
    }

    public boolean hasFamilyMembers() {
        return familyMembers != null;
    }

    private List<RileyLinkFirmwareVersion> getFamilyMembersRecursive() {
        List<RileyLinkFirmwareVersion> members = new ArrayList<>();
        if (hasFamilyMembers()) {
            for (RileyLinkFirmwareVersion version : familyMembers) {
                members.add(version);
                if (version.hasFamilyMembers()) {
                    members.addAll(version.getFamilyMembersRecursive());
                }
            }
        }

        return members;
    }

    static boolean isSameVersion(RileyLinkFirmwareVersion versionWeCheck, RileyLinkFirmwareVersion versionSources) {
        if (versionWeCheck == versionSources) {
            return true;
        }

        if (versionSources.familyMembers != null) {
            return versionSources.getFamilyMembersRecursive().contains(versionWeCheck);
        }

        return false;
    }

    public static RileyLinkFirmwareVersion getByVersionString(String versionString) {
        if (versionString != null) {
            Matcher m = _version_pattern.matcher(versionString);
            if (m.find()) {
                int major = Integer.parseInt(m.group(1));
                int minor = Integer.parseInt(m.group(2));
                String versionKey = major + "." + minor;
                if (mapByVersion.containsKey(versionKey)) {
                    return mapByVersion.get(versionKey);
                } else {
                    return defaultToLowestMajorVersion(major); // just in case there is new release that we don't cover
                    // example: 2.3 etc
                }
            }
        }

        return RileyLinkFirmwareVersion.UnknownVersion;
    }


    private static RileyLinkFirmwareVersion defaultToLowestMajorVersion(int major) {
        if (mapByVersion.containsKey(major + ".x")) {
            return mapByVersion.get(major + ".x");
        }
        return UnknownVersion;
    }


    public boolean isSameVersion(RileyLinkFirmwareVersion versionSources) {
        return isSameVersion(this, versionSources);
    }


    @Override
    public String toString() {
        if (hasFamilyMembers()) {
            return FIRMWARE_IDENTIFICATION_PREFIX + name();
        }
        return FIRMWARE_IDENTIFICATION_PREFIX + versionKey;
    }
}
