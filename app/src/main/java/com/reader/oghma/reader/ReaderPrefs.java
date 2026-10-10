package com.reader.oghma.reader;

import android.content.Context;

/** Small reader settings that should survive closing the app. */
public final class ReaderPrefs {

    private static final String PREFS = "oghma_reader_prefs";

    private static final String KEY_EPUB_FONT_SIZE = "epub_font_size";

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
}
