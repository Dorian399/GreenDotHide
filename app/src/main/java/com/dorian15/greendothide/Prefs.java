package com.dorian15.greendothide;

/**
 * Remote preference keys shared by the module app and the SystemUI hook.
 * <p>
 * Deliberately free of any {@code io.github.libxposed.api} reference: the framework API is only
 * present in hooked processes, so constants the module app also reads must live in a class the
 * module app can load on its own.
 */
public final class Prefs {

    /** Group name in the Xposed framework's remote preference store. */
    public static final String GROUP = "prefs";

    public static final String KEY_LOCATION = "disable_location_indicator";
    public static final String KEY_MIC = "disable_microphone_indicator";
    public static final String KEY_CAMERA = "disable_camera_indicator";
    public static final String KEY_MEDIA = "disable_media_projection_indicator";

    private Prefs() {
    }
}
