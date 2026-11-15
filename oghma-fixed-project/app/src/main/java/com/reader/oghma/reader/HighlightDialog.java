package com.reader.oghma.reader;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

/**
 * One dialog for everything about a highlight: pick a colour, write a note, copy the text.
 *
 * Used when the reader highlights text (create mode) and when editing an entry from the
 * Notes list (edit mode, which also offers Delete).
 */
public final class HighlightDialog {

    public interface Listener {

        void onSave(
                int color,
                String note
        );

        void onCopy();

        /** Edit mode only. */
        default void onDelete() {
        }

        /** Create mode only: dialog closed without saving. */
        default void onCancel() {
        }
    }


    private HighlightDialog() {
    }


    /**
     * @param editing true when changing an existing entry (shows Delete instead of Cancel)
     */
    public static void show(
            Context context,
            String selectedText,
            int initialColor,
            String initialNote,
            boolean editing,
            Listener listener
    ) {

        final int[] chosen = {
                HighlightColors.resolve(initialColor)
        };

        int pad = dp(context, 20);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(context, 8), pad, 0);


        // ---------- preview of the selected text, with a colour bar ----------

        LinearLayout preview = new LinearLayout(context);
        preview.setOrientation(LinearLayout.HORIZONTAL);

        final View bar = new View(context);
        bar.setLayoutParams(
                new LinearLayout.LayoutParams(
                        dp(context, 4),
                        LinearLayout.LayoutParams.MATCH_PARENT
                )
        );
        setRounded(bar, chosen[0], 0, 0, dp(context, 2));

        TextView previewText = new TextView(context);
        previewText.setText(
                selectedText != null
                        ? selectedText
                        : ""
        );
        previewText.setMaxLines(5);
        previewText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        previewText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        previewText.setAlpha(0.8f);

        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );
        textParams.setMarginStart(dp(context, 12));

        preview.addView(bar);
        preview.addView(previewText, textParams);

        root.addView(
                preview,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );


        // ---------- colour swatches ----------

        LinearLayout swatches = new LinearLayout(context);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        swatches.setGravity(Gravity.CENTER_VERTICAL);

        final int ringColor =
                themeColor(
                        context,
                        android.R.attr.textColorPrimary,
                        Color.WHITE
                );

        final View[] swatchViews =
                new View[HighlightColors.ALL.length];

        for (int i = 0; i < HighlightColors.ALL.length; i++) {

            final int color = HighlightColors.ALL[i];

            View swatch = new View(context);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            dp(context, 38),
                            dp(context, 38)
                    );
            params.setMarginEnd(dp(context, 12));

            swatch.setLayoutParams(params);
            swatch.setContentDescription("Highlight colour " + (i + 1));

            swatch.setOnClickListener(v -> {

                chosen[0] = color;

                setRounded(bar, color, 0, 0, dp(context, 2));

                refreshSwatches(
                        context,
                        swatchViews,
                        chosen[0],
                        ringColor
                );
            });

            swatchViews[i] = swatch;
            swatches.addView(swatch);
        }

        refreshSwatches(
                context,
                swatchViews,
                chosen[0],
                ringColor
        );

        LinearLayout.LayoutParams swatchRowParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        swatchRowParams.topMargin = dp(context, 16);

        root.addView(swatches, swatchRowParams);


        // ---------- note ----------

        final EditText noteInput = new EditText(context);
        noteInput.setHint("Add a note (optional)");
        noteInput.setMinLines(3);
        noteInput.setMaxLines(8);
        noteInput.setGravity(Gravity.TOP | Gravity.START);
        noteInput.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );

        if (initialNote != null) {
            noteInput.setText(initialNote);
        }

        LinearLayout.LayoutParams noteParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        noteParams.topMargin = dp(context, 12);

        root.addView(noteInput, noteParams);


        // ---------- buttons ----------

        final boolean[] saved = {false};

        AlertDialog.Builder builder =
                new AlertDialog.Builder(context)
                        .setTitle(editing ? "Edit note" : "Highlight")
                        .setView(root)
                        .setPositiveButton(
                                "Save",
                                (dialog, which) -> {

                                    saved[0] = true;

                                    listener.onSave(
                                            chosen[0],
                                            noteInput.getText()
                                                    .toString()
                                                    .trim()
                                    );
                                }
                        )
                        .setNeutralButton(
                                "Copy",
                                (dialog, which) -> listener.onCopy()
                        );

        if (editing) {

            builder.setNegativeButton(
                    "Delete",
                    (dialog, which) ->
                            confirmDelete(context, listener)
            );

        } else {

            builder.setNegativeButton(
                    "Cancel",
                    null
            );

            builder.setOnDismissListener(d -> {

                if (!saved[0]) {
                    listener.onCancel();
                }
            });
        }

        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {

            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            );
        }

        dialog.show();
    }


    private static void confirmDelete(
            Context context,
            Listener listener
    ) {

        new AlertDialog.Builder(context)
                .setTitle("Delete this note?")
                .setMessage("The highlight and its note will be removed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Delete",
                        (dialog, which) -> listener.onDelete()
                )
                .show();
    }


    private static void refreshSwatches(
            Context context,
            View[] swatches,
            int selectedColor,
            int ringColor
    ) {

        for (int i = 0; i < swatches.length; i++) {

            int color = HighlightColors.ALL[i];

            boolean selected =
                    color == selectedColor;

            setRounded(
                    swatches[i],
                    color,
                    selected ? dp(context, 3) : 0,
                    ringColor,
                    dp(context, 19)
            );
        }
    }


    private static void setRounded(
            View view,
            int fill,
            int strokeWidth,
            int strokeColor,
            int radius
    ) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(fill);
        drawable.setCornerRadius(radius);

        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }

        view.setBackground(drawable);
    }


    private static int themeColor(
            Context context,
            int attr,
            int fallback
    ) {

        TypedArray array =
                context.obtainStyledAttributes(
                        new int[]{attr}
                );

        try {

            return array.getColor(0, fallback);

        } finally {

            array.recycle();
        }
    }


    private static int dp(
            Context context,
            int value
    ) {

        return Math.round(
                value * context.getResources().getDisplayMetrics().density
        );
    }


    // ---------------------------------------------------------------------
    // Clipboard helper shared by the readers and the Notes list
    // ---------------------------------------------------------------------

    public static void copyToClipboard(
            Context context,
            String text
    ) {

        if (text == null || text.isEmpty()) {
            return;
        }

        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(
                        Context.CLIPBOARD_SERVICE
                );

        if (clipboard != null) {

            clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                            "Oghma",
                            text
                    )
            );

            Toast.makeText(
                    context,
                    "Copied",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }
}
