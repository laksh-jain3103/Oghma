package com.reader.oghma.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.reader.oghma.R;

import java.util.ArrayList;
import java.util.List;

public class GoogleBooksAdapter
        extends RecyclerView.Adapter<GoogleBooksAdapter.BookViewHolder> {

    public interface OnBookClickListener {
        void onBookClick(UnifiedBookItem book);
    }

    private final List<UnifiedBookItem> books =
            new ArrayList<>();

    private final OnBookClickListener listener;

    public GoogleBooksAdapter(
            OnBookClickListener listener
    ) {
        this.listener = listener;
    }

    public void setBooks(
            List<UnifiedBookItem> newBooks
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

        UnifiedBookItem book =
                books.get(position);

        // -----------------------------
        // Title
        // -----------------------------

        holder.title.setText(
                book.title != null && !book.title.isEmpty()
                        ? book.title
                        : "Unknown title"
        );


        // -----------------------------
        // Author
        // -----------------------------

        holder.author.setText(
                book.author != null && !book.author.isEmpty()
                        ? book.author
                        : "Unknown author"
        );


        // -----------------------------
        // Published date
        // -----------------------------

        if (
                book.publishedDate != null
                        && !book.publishedDate.isEmpty()
        ) {

            holder.publishedDate.setText(
                    book.publishedDate
            );

        } else {

            holder.publishedDate.setText("");
        }


        // -----------------------------
        // Source & Status
        // -----------------------------

        if (book.source == UnifiedBookItem.Source.GUTENBERG) {
            holder.sourceStatus.setText("Project Gutenberg • EPUB download");
            holder.sourceStatus.setTextColor(0xFF00C853);
        } else {
            holder.sourceStatus.setText("Google Books • Preview only");
            holder.sourceStatus.setTextColor(0xFFA6ADC8);
        }


        // -----------------------------
        // Cover
        // -----------------------------

        String coverUrl = book.coverUrl;

        if (
                coverUrl != null
                        && !coverUrl.isEmpty()
        ) {

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
        // Book click
        // -----------------------------

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
        final TextView sourceStatus;

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

            sourceStatus =
                    itemView.findViewById(
                            R.id.bookSourceStatus
                    );
        }
    }
}
