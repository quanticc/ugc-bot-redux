package sx.blah.discord.handle.obj;

import java.util.EnumSet;

/**
 * This represents Discord permissions.
 */
public enum Permissions {

    /**
     * Allows the user to create invites.
     */
    CREATE_INVITE(0),
    /**
     * Allows the user to kick users.
     */
    KICK(1),
    /**
     * Allows the user to ban users.
     */
    BAN(2),
    /**
     * Allows the user to manage roles.
     * NOTE: This supersedes any other permissions if true.
     */
    MANAGE_ROLES(3),
    /**
     * Allows the user to manage permissions.
     */
    MANAGE_PERMISSIONS(3),
    /**
     * Allows the user to manage channels.
     */
    MANAGE_CHANNELS(4),
    /**
     * Allows the user to manage a specific channel.
     */
    MANAGE_CHANNEL(4),
    /**
     * Allows the user to manage a server.
     */
    MANAGE_SERVER(5),
    /**
     * Allows the user to read messages.
     */
    READ_MESSAGES(10),
    /**
     * Allows the user to send messages.
     */
    SEND_MESSAGES(11),
    /**
     * Allows the user to send messages with tts.
     */
    SEND_TTS_MESSAGES(12),
    /**
     * Allows the user to manage messages.
     */
    MANAGE_MESSAGES(13),
    /**
     * Allows the user to embed links in messages.
     */
    EMBED_LINKS(14),
    /**
     * Allows the user to attach files in chat.
     */
    ATTACH_FILES(15),
    /**
     * Allows the user to read message history.
     */
    READ_MESSAGE_HISTORY(16),
    /**
     * Allows the user to @mention everyone.
     */
    MENTION_EVERYONE(17),
    /**
     * Allows the user to connect to a voice channel.
     */
    VOICE_CONNECT(20),
    /**
     * Allows the user to speak in a voice channel.
     */
    VOICE_SPEAK(21),
    /**
     * Allows the user to globally mute users in a voice channel.
     */
    VOICE_MUTE_MEMBERS(22),
    /**
     * Allows the user to globally deafen users in a voice channel.
     */
    VOICE_DEAFEN_MEMBERS(23),
    /**
     * Allows the user to move users to different voice channels.
     */
    VOICE_MOVE_MEMBERS(24),
    /**
     * Allows the user to use "voice activation detection".
     */
    VOICE_USE_VAD(25);

    /**
     * The bit offset in the permissions number
     */
    public final int offset;

    Permissions(int offset) {
        this.offset = offset;
    }

    /**
     * Checks whether a provided "permissions number" contains this permission.
     *
     * @param permissionsNumber The raw permissions number.
     * @param allow <code>true</code> to allow some permissions to supersede all others, <code>false</code> otherwise
     * @return <code>true</code> if the user has this permission, <code>false</code> if otherwise.
     */
    public boolean hasPermission(int permissionsNumber, boolean allow) {
        // simplify into a one-liner?
        return (1 << offset & permissionsNumber) > 0
            || allow && !this.equals(MANAGE_ROLES) && MANAGE_ROLES.hasPermission(permissionsNumber, true);
    }

    private static EnumSet<Permissions> getAllPermissionsForNumber(int permissionsNumber, boolean allow) {
        EnumSet<Permissions> permissionsSet = EnumSet.noneOf(Permissions.class);

        for (Permissions permission : EnumSet.allOf(Permissions.class)) {
            if (permission.hasPermission(permissionsNumber, allow))
                permissionsSet.add(permission);
        }

        return permissionsSet;
    }

    /**
     * Generates a set of allowed Permissions represented by the given raw permissions number.
     *
     * @param permissionsNumber The raw permissions number.
     * @return The set of permissions allowed by the number.
     */
    public static EnumSet<Permissions> getAllowPermissionsForNumber(int permissionsNumber) {
        return getAllPermissionsForNumber(permissionsNumber, true);
    }

    /**
     * Generates a set of denied Permissions represented by the given raw permissions number.
     *
     * @param permissionsNumber The raw permissions number.
     * @return The set of permissions denied by the number.
     */
    public static EnumSet<Permissions> getDenyPermissionsForNumber(int permissionsNumber) {
        return getAllPermissionsForNumber(permissionsNumber, false);
    }

    /**
     * Generates a raw permissions number for the provided set of permissions.
     *
     * @param permissions The permissions.
     * @return The raw permissions number.
     */
    public static int generatePermissionsNumber(EnumSet<Permissions> permissions) {
        int number = 0;
        for (Permissions permission : permissions) {
            number |= (1 << permission.offset);
        }
        return number;
    }
}
