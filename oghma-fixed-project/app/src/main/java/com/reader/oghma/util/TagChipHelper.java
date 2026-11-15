package com.reader.oghma.util;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.reader.oghma.R;

public class TagChipHelper {

    public static TextView createChip(Context context, String text, float textSizeSp) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextColor(context.getResources().getColor(R.color.text_secondary));
        chip.setTextSize(textSizeSp);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setPadding(dpToPx(context, 6), dpToPx(context, 2), dpToPx(context, 6), dpToPx(context, 2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMarginEnd(dpToPx(context, 4));
        chip.setLayoutParams(params);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(context.getResources().getColor(R.color.surface_variant));
        bg.setCornerRadius(dpToPx(context, 4));
        chip.setBackground(bg);
        return chip;
    }

    public static int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
