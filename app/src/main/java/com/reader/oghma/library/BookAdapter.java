package com.reader.oghma.library;

import android.graphics.BitmapFactory;
import android.content.Context;
import android.text.TextUtils;
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
import com.reader.oghma.util.AuthorUtils;
import com.reader.oghma.util.TagChipHelper;

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

    public interface OnTagClickListener {
        void onTagClick(Tag tag);
    }

    public interface OnTagLongClickListener {
        void onTagLongClick(Tag tag);
    }

    private OnTagClickListener onTagClickListener;
    private OnTagLongClickListener onTagLongClickListener;

    public void setOnTagClickListener(OnTagClickListener listener) {
        this.onTagClickListener = listener;
    }

    public void setOnTagLongClickListener(OnTagLongClickListener listener) {
        this.onTagLongClickListener = listener;
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

        String cleanAuthor = AuthorUtils.cleanAuthor(book.author);
        if (AuthorUtils.isUnknownOrEmpty(cleanAuthor)) {
            holder.bookAuthor.setVisibility(View.GONE);
        } else {
            holder.bookAuthor.setVisibility(View.VISIBLE);
            holder.bookAuthor.setText(cleanAuthor);
        }


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
        if (tags == null || tags.isEmpty()) {
            holder.bookTagContainer.setVisibility(View.GONE);
        } else {
            holder.bookTagContainer.setVisibility(View.VISIBLE);
            holder.bookTagContainer.setMinimumHeight(TagChipHelper.dpToPx(holder.itemView.getContext(), 24));
            holder.bookTagContainer.setTag(book.bookId);
            holder.bookTagContainer.post(() -> {
                if (holder.bookTagContainer.getTag() == null || (int) holder.bookTagContainer.getTag() != book.bookId) {
                    return;
                }
                int availableWidth = holder.bookTagContainer.getWidth() - holder.bookTagContainer.getPaddingLeft() - holder.bookTagContainer.getPaddingRight();
                if (availableWidth <= 0) {
                    availableWidth = holder.itemView.getWidth() - TagChipHelper.dpToPx(holder.itemView.getContext(), 48);
                }
                bindDynamicChips(holder.bookTagContainer, tags, availableWidth, book);
            });
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


    private void bindDynamicChips(LinearLayout container, List<Tag> tags, int availableWidth, Book book) {
        container.removeAllViews();
        Context context = container.getContext();
        int margin = TagChipHelper.dpToPx(context, 4);

        if (tags.size() == 1) {
            Tag tag = tags.get(0);
            TextView chip = TagChipHelper.createChip(context, tag.name, 11f);
            chip.setMaxWidth(availableWidth);
            chip.setOnClickListener(v -> {
                if (onTagClickListener != null) onTagClickListener.onTagClick(tag);
            });
            chip.setOnLongClickListener(v -> {
                if (onTagLongClickListener != null) onTagLongClickListener.onTagLongClick(tag);
                return true;
            });
            container.addView(chip);
            return;
        }

        List<View> chipsToAdd = new ArrayList<>();
        int fitCount = 0;
        int usedWidth = 0;

        for (int i = 0; i < tags.size(); i++) {
            Tag tag = tags.get(i);
            TextView chip = TagChipHelper.createChip(context, tag.name, 11f);
            chip.setMaxWidth(TagChipHelper.dpToPx(context, 120));
            chip.setOnClickListener(v -> {
                if (onTagClickListener != null) onTagClickListener.onTagClick(tag);
            });
            chip.setOnLongClickListener(v -> {
                if (onTagLongClickListener != null) onTagLongClickListener.onTagLongClick(tag);
                return true;
            });
            chip.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                         View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int w = chip.getMeasuredWidth() + margin;

            int remaining = tags.size() - i;
            int plusWidth = 0;
            if (remaining > 1) {
                TextView plusChip = TagChipHelper.createChip(context, "+" + remaining, 11f);
                plusChip.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                                 View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                plusWidth = plusChip.getMeasuredWidth() + margin;
            }

            if (usedWidth + w + (remaining > 1 ? plusWidth : 0) <= availableWidth || fitCount == 0) {
                usedWidth += w;
                chipsToAdd.add(chip);
                fitCount++;
            } else {
                if (fitCount == 0) {
                    TextView plusChip = TagChipHelper.createChip(context, "+" + (tags.size() - 1), 11f);
                    plusChip.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                                     View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    int pWidth = plusChip.getMeasuredWidth() + margin;

                    int maxFirstWidth = availableWidth - pWidth - margin;
                    int allowedWidth = Math.max(TagChipHelper.dpToPx(context, 40), maxFirstWidth);

                    chip.setMaxWidth(allowedWidth);
                    chip.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                                 View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    chipsToAdd.add(chip);
                    fitCount = 1;
                }
                break;
            }
        }

        for (View c : chipsToAdd) {
            container.addView(c);
        }

        if (fitCount < tags.size()) {
            int extra = tags.size() - fitCount;
            TextView plusChip = TagChipHelper.createChip(context, "+" + extra, 11f);
            plusChip.setOnClickListener(v -> {
                if (onBookMoreClickListener != null) {
                    onBookMoreClickListener.onBookMoreClick(book);
                }
            });
            container.addView(plusChip);
        }
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