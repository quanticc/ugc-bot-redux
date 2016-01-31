package com.ugcleague.ops.util;

public class Util {

    public static String humanizeBytes(long bytes) {
        int unit = 1000; // 1024 for non-SI units
        if (bytes < unit)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), "kMGTPE".charAt(exp - 1));
    }

    public static String toLowerCamelCase(String s) {
        s = toCamelCase(s);
        return s.substring(0, 1).toLowerCase() + s.substring(1);
    }

    public static String toCamelCase(String s) {
        String[] parts = s.split("_| ");
        String camelCaseString = "";
        for (String part : parts) {
            camelCaseString = camelCaseString + toProperCase(part);
        }
        return camelCaseString;
    }

    public static String toProperCase(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    public static String padRight(String s, int n) {
        return String.format("%1$-" + n + "s", s);
    }

    public static String padLeft(String s, int n) {
        return String.format("%1$" + n + "s", s);
    }

    private Util() {
    }
}
