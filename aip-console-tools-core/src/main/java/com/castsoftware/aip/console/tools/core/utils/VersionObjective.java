package com.castsoftware.aip.console.tools.core.utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.lang3.StringUtils;

import java.util.EnumSet;
import java.util.Set;

import static java.util.stream.Collectors.joining;

public enum VersionObjective {
    GLOBAL_RISK,
    SECURITY,
    FUNCTIONAL_POINTS,
    BLUEPRINT,
    DATA_SAFETY;

    public static final String OBJECTIVES_DELIMITER = ",";

    @JsonCreator
    public static VersionObjective fromString(String value) {
        return value == null ? null : VersionObjective.valueOf(value.toUpperCase());
    }

    @Override
    @JsonValue
    public String toString() {
        return name().toLowerCase();
    }

    public static EnumSet<VersionObjective> fromStrings(String... strings) {
        EnumSet<VersionObjective> result = EnumSet.noneOf(VersionObjective.class);
        for (String string : strings) {
            result.add(fromString(string));
        }
        return result;
    }

    public static String toJoinedString(Set<VersionObjective> objectives) {
        return objectives.stream().map(VersionObjective::toString).collect(joining(OBJECTIVES_DELIMITER));
    }

    public static EnumSet<VersionObjective> fromJoinedString(String objectives) {
        if (StringUtils.isNotEmpty(objectives)) {
            return fromStrings(objectives.split(OBJECTIVES_DELIMITER));
        }
        return EnumSet.noneOf(VersionObjective.class);
    }
}
