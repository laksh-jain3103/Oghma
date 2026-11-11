package com.reader.oghma.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.reader.oghma.R;
import com.reader.oghma.api.model.GoogleBookItem;
import com.reader.oghma.util.TagChipHelper;

import java.util.ArrayList;
import java.util.List;

public class GoogleBooksAdapter
        extends RecyclerView.Adapter<GoogleBooksAdapter.BookViewHolder> {

    public interface OnBookClickListener {
        void onBookClick(GoogleBookItem book);
    }

    public interface OnTagClickListener {
        void onTagClick(String tag);
    }

    private final List<GoogleBookItem> books =
            new ArrayList<>();

    private final OnBookClickListener listener;
    private OnTagClickListener tagClickListener;

    public GoogleBooksAdapter(
            OnBookClickListener listener
    ) {
        this.listener = listener;
    }

    public void setOnTagClickListener(OnTagClickListener listener) {
        this.tagClickListener = listener;
    }

    public void setBooks(
            List<GoogleBookItem> newBooks
    ) {

        books.clear();

        if (newBooks != null) {
            books.addAll(newBooks);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        View view =
                LayoutInflater.from(
                        parent.getContext()
                ).inflate(
                        R.layout.item_google_book,
                        parent,
                        false
                );

        return new BookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull BookViewHolder holder,
            int position
    ) {

        GoogleBookItem book =
                books.get(position);

        if (book.volumeInfo == null) {

            holder.title.setText(
                    "Unknown title"
            );

            holder.author.setText(
                    "Unknown author"
            );

            holder.publishedDate.setText("");

            holder.cover.setImageResource(
                    android.R.drawable.ic_menu_gallery
            );

            holder.itemView.setOnClickListener(
                    null
            );

            return;
        }

        // -----------------------------
        // Title
        // -----------------------------

        holder.title.setText(
                book.volumeInfo.title != null
                        ? book.volumeInfo.title
                        : "Unknown title"
        );


        // -----------------------------
        // Author
        // -----------------------------

        if (
                book.volumeInfo.authors != null
                        && !book.volumeInfo.authors.isEmpty()
        ) {

            holder.author.setText(
                    String.join(
                            ", ",
                            book.volumeInfo.authors
                    )
            );

        } else {

            holder.author.setText(
                    "Unknown author"
            );
        }


        // -----------------------------
        // Published date
        // -----------------------------

        if (
                book.volumeInfo.publishedDate != null
                        && !book.volumeInfo.publishedDate.isEmpty()
        ) {

            holder.publishedDate.setText(
                    book.volumeInfo.publishedDate
            );

        } else {

            holder.publishedDate.setText("");
        }


        // -----------------------------
        // Cover
        // -----------------------------

        String coverUrl = null;

        if (
                book.volumeInfo.imageLinks != null
        ) {

            coverUrl =
                    book.volumeInfo.imageLinks.thumbnail;

            if (
                    coverUrl == null
                            || coverUrl.isEmpty()
            ) {

                coverUrl =
                        book.volumeInfo.imageLinks
                                .smallThumbnail;
            }
        }


        if (
                coverUrl != null
                        && !coverUrl.isEmpty()
        ) {

            // Google Books commonly returns HTTP
            // thumbnail URLs. Glide can load HTTPS
            // directly, so upgrade HTTP to HTTPS.

            if (coverUrl.startsWith("http://")) {

                coverUrl =
                        coverUrl.replaceFirst(
                                "http://",
                                "https://"
                        );
            }

            Glide.with(holder.cover.getContext())
                    .load(coverUrl)
                    .placeholder(
                            android.R.drawable.ic_menu_gallery
                    )
                    .error(
                            android.R.drawable.ic_menu_gallery
                    )
                    .into(holder.cover);

        } else {

            holder.cover.setImageResource(
                    android.R.drawable.ic_menu_gallery
            );
        }


        // -----------------------------
        // Categories / Tags
        // -----------------------------

        holder.bookTagContainer.removeAllViews();
        if (book.volumeInfo.categories != null && !book.volumeInfo.categories.isEmpty()) {
            List<String> mappedTags = com.reader.oghma.api.metadata.TagNormalizer.map(book.volumeInfo.categories);
            if (!mappedTags.isEmpty()) {
                holder.bookTagContainer.setVisibility(View.VISIBLE);
                int showCount = Math.min(mappedTags.size(), 3);
                for (int i = 0; i < showCount; i++) {
                    String tag = mappedTags.get(i);
                    TextView chip = TagChipHelper.createChip(holder.itemView.getContext(), tag, 11f);
                    chip.setOnClickListener(v -> {
                        if (tagClickListener != null) {
                            tagClickListener.onTagClick(tag);
                        }
                    });
                    holder.bookTagContainer.addView(chip);
                }
            } else {
                holder.bookTagContainer.setVisibility(View.GONE);
            }
        } else {
            holder.bookTagContainer.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(
                v -> {

                    if (listener != null) {

                        listener.onBookClick(
                                book
                        );
                    }
                }
        );
    }

    @Override
    public int getItemCount() {

        return books.size();
    }

    static class BookViewHolder
            extends RecyclerView.ViewHolder {

        final ImageView cover;
        final TextView title;
        final TextView author;
        final TextView publishedDate;
        final LinearLayout bookTagContainer;

        BookViewHolder(
                @NonNull View itemView
        ) {

            super(itemView);

            cover =
                    itemView.findViewById(
                            R.id.bookCover
                    );

            title =
                    itemView.findViewById(
                            R.id.bookTitle
                    );

            author =
                    itemView.findViewById(
                            R.id.bookAuthor
                    );

            publishedDate =
                    itemView.findViewById(
                            R.id.bookPublishedDate
                    );

            bookTagContainer =
                    itemView.findViewById(
                            R.id.bookTagContainer
                    );
        }
    }
}