package com.reader.oghma.reader;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The highlight colour palette, shared by the EPUB reader, the PDF reader and the Notes list.
 *
 * Colours are stored in Bookmark.color as ARGB ints. 0 means "never chosen" (rows that
 * existed before colours were added) and is shown as yellow.
 */
public final class HighlightColors {

    public static final int YELLOW = 0xFFFFD60A;
    public static final int GREEN = 0xFF5DD97C;
    public static final int BLUE = 0xFF5AA9FF;
    public static final int PINK = 0xFFFF7AB8;
    public static final int PURPLE = 0xFFB48CFF;

    public static final int[] ALL = {
            YELLOW,
            GREEN,
            BLUE,
            PINK,
            PURPLE
    };

    private static final String PREFS = "oghma_reader_prefs";

    private static final String KEY_LAST_COLOR = "last_highlight_color";


    private HighlightColors() {
    }


    /** Turns a stored value (possibly 0) into a real colour. */
    public static int resolve(int stored) {

        return stored == 0
                ? YELLOW
                : stored;
    }


    /** "#RRGGBB", used by the PDF viewer. */
    public static String toHex(int stored) {

        return String.format(
                "#%06X",
                resolve(stored) & 0xFFFFFF
        );
    }


    /** The colour used last time, so repeated highlighting stays one-tap. */
    public static int getLast(Context context) {

        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                );

        return resolve(
                prefs.getInt(
                        KEY_LAST_COLOR,
                        YELLOW
                )
        );
    }


    public static void setLast(
            Context context,
            int color
    ) {

        context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                )
                .edit()
                .putInt(
                        KEY_LAST_COLOR,
                        color
                )
                .apply();
    }
}
