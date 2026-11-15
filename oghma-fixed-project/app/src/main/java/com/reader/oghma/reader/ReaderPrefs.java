package com.reader.oghma.reader;

import android.content.Context;

/** Small reader settings that should survive closing the app. */
public final class ReaderPrefs {

    private static final String PREFS = "oghma_reader_prefs";

    private static final String KEY_EPUB_FONT_SIZE = "epub_font_size";

    private static final String KEY_TTS_SPEED = "tts_speed";

    private static final String KEY_TTS_VOICE = "tts_voice";

    private static final String KEY_TTS_VOICE_LANGUAGE = "tts_voice_language";

    /** Reading speeds offered by the read-aloud bar. 1.0 = normal. */
    public static final double[] TTS_SPEEDS = {0.75, 1.0, 1.25, 1.5, 2.0};

    public static final double MIN_FONT_SIZE = 0.5;

    public static final double MAX_FONT_SIZE = 3.0;


    private ReaderPrefs() {
    }


    /** EPUB text scale, 1.0 = 100%. */
    public static double getEpubFontSize(Context context) {

        float stored =
                context.getSharedPreferences(
                                PREFS,
                                Context.MODE_PRIVATE
                        )
                        .getFloat(
                                KEY_EPUB_FONT_SIZE,
                                1.0f
                        );

        return Math.max(
                MIN_FONT_SIZE,
                Math.min(MAX_FONT_SIZE, stored)
        );
    }


    public static void setEpubFontSize(
            Context context,
            double size
    ) {

        context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                )
                .edit()
                .putFloat(
                        KEY_EPUB_FONT_SIZE,
                        (float) size
                )
                .apply();
    }


    /** Read-aloud speed, 1.0 = normal. */
    public static double getTtsSpeed(Context context) {

        return context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                )
                .getFloat(
                        KEY_TTS_SPEED,
                        1.0f
                );
    }


    public static void setTtsSpeed(
            Context context,
            double speed
    ) {

        context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                )
                .edit()
                .putFloat(
                        KEY_TTS_SPEED,
                        (float) speed
                )
                .apply();
    }


    /** Name of the chosen read-aloud voice, or null for "Automatic". */
    public static String getTtsVoice(Context context) {

        return context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                )
                .getString(
                        KEY_TTS_VOICE,
                        null
                );
    }


    /** Language tag (for example "en-US") of the chosen voice, or null. */
    public static String getTtsVoiceLanguage(Context context) {

        return context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                )
                .getString(
                        KEY_TTS_VOICE_LANGUAGE,
                        null
                );
    }


    /** Pass null to go back to "Automatic". */
    public static void setTtsVoice(
            Context context,
            String voiceName,
            String languageTag
    ) {

        context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                )
                .edit()
                .putString(KEY_TTS_VOICE, voiceName)
                .putString(KEY_TTS_VOICE_LANGUAGE, languageTag)
                .apply();
    }
}
