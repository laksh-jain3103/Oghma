package com.reader.oghma.library;

import android.graphics.BitmapFactory;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.reader.oghma.R;
import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.entity.Tag;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BookAdapter
        extends RecyclerView.Adapter<BookAdapter.BookViewHolder> {

    private OnBookClickListener onBookClickListener;
    private OnBookMoreClickListener onBookMoreClickListener;

    private final List<Book> books =
            new ArrayList<>();

    private Map<Integer, List<Tag>> bookTagsMap = new HashMap<>();

    private boolean gridMode = false;


    // =========================================================
    // Books
    // =========================================================

    public void setBooks(List<Book> newBooks) {

        books.clear();

        if (newBooks != null) {
            books.addAll(newBooks);
        }

        notifyDataSetChanged();
    }

    public void setBookTags(Map<Integer, List<Tag>> map) {
        if (map != null) {
            this.bookTagsMap = map;
        } else {
            this.bookTagsMap.clear();
        }
        notifyDataSetChanged();
    }


    // =========================================================
    // View mode
    // =========================================================

    public void setGridMode(boolean gridMode) {

        this.gridMode = gridMode;

        notifyDataSetChanged();
    }


    // =========================================================
    // Normal book click
    // =========================================================

    public interface OnBookClickListener {

        void onBookClick(Book book);
    }


    public void setOnBookClickListener(
            OnBookClickListener listener
    ) {

        this.onBookClickListener = listener;
    }


    // =========================================================
    // Three-dot menu click
    // =========================================================

    public interface OnBookMoreClickListener {

        void onBookMoreClick(Book book);
    }


    public void setOnBookMoreClickListener(
            OnBookMoreClickListener listener
    ) {

        this.onBookMoreClickListener = listener;
    }


    // =========================================================
    // RecyclerView view type
    // =========================================================

    @Override
    public int getItemViewType(int position) {

        if (gridMode) {
            return 1;
        }

        return 0;
    }


    // =========================================================
    // Create ViewHolder
    // =========================================================

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        int layout;

        if (viewType == 1) {

            // Grid card
            layout = R.layout.item_book_grid;

        } else {

            // List card
            layout = R.layout.item_book;
        }


        View view =
                LayoutInflater
                        .from(parent.getContext())
                        .inflate(
                                layout,
                                parent,
                                false
                        );


        return new BookViewHolder(view);
    }


    // =========================================================
    // Bind book data
    // =========================================================

    @Override
    public void onBindViewHolder(
            @NonNull BookViewHolder holder,
            int position
    ) {

        Book book =
                books.get(position);


        // -----------------------------------------------------
        // Title
        // -----------------------------------------------------

        holder.bookTitle.setText(
                book.title != null
                        ? book.title
                        : "Untitled"
        );


        // -----------------------------------------------------
        // Author
        // -----------------------------------------------------

        holder.bookAuthor.setText(
                book.author != null
                        ? book.author
                        : "Unknown author"
        );


        // -----------------------------------------------------
        // Format
        // -----------------------------------------------------

        holder.bookFormat.setText(
                book.format != null
                        ? book.format.toUpperCase()
                        : "BOOK"
        );


        // -----------------------------------------------------
        // Tags
        // -----------------------------------------------------

        List<Tag> tags = bookTagsMap.get(book.bookId);
        holder.bookTagContainer.removeAllViews();
        if (tags != null && !tags.isEmpty()) {
            holder.bookTagContainer.setVisibility(View.VISIBLE);
            int showCount = Math.min(tags.size(), 3);
            for (int i = 0; i < showCount; i++) {
                TextView chip = createTagChip(holder.itemView.getContext(), tags.get(i).name);
                holder.bookTagContainer.addView(chip);
            }
            if (tags.size() > 3) {
                int extra = tags.size() - 3;
                TextView extraChip = createTagChip(holder.itemView.getContext(), "+" + extra);
                holder.bookTagContainer.addView(extraChip);
            }
        } else {
            holder.bookTagContainer.setVisibility(View.GONE);
        }


        // -----------------------------------------------------
        // Cover
        // -----------------------------------------------------

        loadCover(
                holder.bookCover,
                book.coverPath
        );


        // -----------------------------------------------------
        // Open book
        // -----------------------------------------------------

        holder.itemView.setOnClickListener(
                v -> {

                    if (onBookClickListener != null) {

                        onBookClickListener.onBookClick(
                                book
                        );
                    }
                }
        );


        // -----------------------------------------------------
        // Three-dot menu
        // -----------------------------------------------------

        holder.bookMoreButton.setOnClickListener(
                v -> {

                    if (onBookMoreClickListener != null) {

                        onBookMoreClickListener.onBookMoreClick(
                                book
                        );
                    }
                }
        );
    }


    // =========================================================
    // Load cover
    // =========================================================

    private void loadCover(
            ImageView imageView,
            String coverPath
    ) {

        // No cover path

        if (
                coverPath == null
                        || coverPath.isEmpty()
        ) {

            imageView.setImageDrawable(null);

            return;
        }


        File coverFile =
                new File(coverPath);


        // Cover file doesn't exist

        if (!coverFile.exists()) {

            imageView.setImageDrawable(null);

            return;
        }


        // Load the cover

        imageView.setImageBitmap(
                BitmapFactory.decodeFile(
                        coverFile.getAbsolutePath()
                )
        );
    }


    private TextView createTagChip(Context context, String text) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextColor(context.getResources().getColor(R.color.text_secondary));
        chip.setTextSize(10);
        chip.setPadding(dpToPx(context, 6), dpToPx(context, 2), dpToPx(context, 6), dpToPx(context, 2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMarginEnd(dpToPx(context, 4));
        chip.setLayoutParams(params);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(context.getResources().getColor(R.color.surface_variant));
        bg.setCornerRadius(dpToPx(context, 4));
        chip.setBackground(bg);
        return chip;
    }

    private int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {

        return books.size();
    }


    // =========================================================
    // ViewHolder
    // =========================================================

    public static class BookViewHolder
            extends RecyclerView.ViewHolder {

        ImageView bookCover;

        ImageButton bookMoreButton;

        TextView bookTitle;
        TextView bookAuthor;
        TextView bookFormat;
        TextView bookPreview;
        LinearLayout bookTagContainer;


        public BookViewHolder(
                @NonNull View itemView
        ) {

            super(itemView);


            // Cover

            bookCover =
                    itemView.findViewById(
                            R.id.bookCover
                    );


            // Three-dot menu

            bookMoreButton =
                    itemView.findViewById(
                            R.id.bookMoreButton
                    );


            // Title

            bookTitle =
                    itemView.findViewById(
                            R.id.bookTitle
                    );


            // Author

            bookAuthor =
                    itemView.findViewById(
                            R.id.bookAuthor
                    );


            // Format

            bookFormat =
                    itemView.findViewById(
                            R.id.bookFormat
                    );


            // TXT preview

            bookPreview =
                    itemView.findViewById(
                            R.id.bookPreview
                    );

            // Tag container

            bookTagContainer =
                    itemView.findViewById(
                            R.id.bookTagContainer
                    );
        }
    }
}