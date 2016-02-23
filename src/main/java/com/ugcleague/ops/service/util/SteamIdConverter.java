package com.ugcleague.ops.service.util;

/**
 * Utility class to convert between SteamIDs.
 */
public class SteamIdConverter {

    public static String steamId64To2(long communityId) {
        long steamId1 = communityId % 2;
        long steamId2 = communityId - 76561197960265728L;

        if (steamId2 <= 0) {
            return null;
        }

        steamId2 = (steamId2 - steamId1) / 2;

        return "STEAM_0:" + steamId1 + ":" + steamId2;
    }

    public static long steam2To64(String steamId32) {
        if (steamId32.equals("STEAM_ID_LAN") || steamId32.equals("BOT")) {
            return 0;
        }
        if (steamId32.matches("^STEAM_[0-1]:[0-1]:[0-9]+$")) {
            String[] tmpId = steamId32.substring(8).split(":");
            return Long.valueOf(tmpId[0]) + Long.valueOf(tmpId[1]) * 2 + 76561197960265728L;
        } else if (steamId32.matches("^U:[0-1]:[0-9]+$")) {
            String[] tmpId = steamId32.substring(2, steamId32.length()).split(":");
            return Long.valueOf(tmpId[0]) + Long.valueOf(tmpId[1]) + 76561197960265727L;
        } else if (steamId32.matches("^\\[U:[0-1]:[0-9]+\\]+$")) {
            String[] tmpId = steamId32.substring(3, steamId32.length() - 1).split(":");
            return Long.valueOf(tmpId[0]) + Long.valueOf(tmpId[1]) + 76561197960265727L;
        } else {
            return 0;
        }
    }

    public static String steam2To3(String steamId32) {
        String[] tokens = steamId32.split(":");
        if (tokens.length != 3) {
            return "";
        }
        int universe = Integer.parseInt(tokens[0].replace("STEAM_", ""));
        if (universe == 0) {
            universe = 1;
        }
        int account = Integer.parseInt(tokens[1]) + (Integer.parseInt(tokens[2]) << 1);
        return "[U:" + universe + ":" + account + "]";
    }

    public static String steamId64To3(long steamId64) {
        return steam2To3(steamId64To2(steamId64));
    }
}
