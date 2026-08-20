package com.reader.oghma.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.reader.oghma.R;
import com.reader.oghma.api.model.GoogleBookItem;

import java.util.ArrayList;
import java.util.List;

public class GoogleBooksAdapter
        extends RecyclerView.Adapter<GoogleBooksAdapter.BookViewHolder> {

    private final List<GoogleBookItem> books = new ArrayList<>();

    public void setBooks(List<GoogleBookItem> newBooks) {

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
        View view = LayoutInflater.from(parent.getContext())
                .inflate(
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
        GoogleBookItem book = books.get(position);

        if (book.volumeInfo == null) {
            holder.title.setText("Unknown title");
            holder.author.setText("Unknown author");
            holder.publishedDate.setText("");
            holder.cover.setImageResource(
                    android.R.drawable.ic_menu_gallery
            );
            return;
        }

        holder.title.setText(
                book.volumeInfo.title != null
                        ? book.volumeInfo.title
                        : "Unknown title"
        );

        if (book.volumeInfo.authors != null
                && !book.volumeInfo.authors.isEmpty()) {

            holder.author.setText(
                    String.join(
                            ", ",
                            book.volumeInfo.authors
                    )
            );

        } else {
            holder.author.setText("Unknown author");
        }

        if (book.volumeInfo.publishedDate != null
                && !book.volumeInfo.publishedDate.isEmpty()) {

            holder.publishedDate.setText(
                    book.volumeInfo.publishedDate
            );

        } else {
            holder.publishedDate.setText("");
        }

        // Cover image loading will be added separately.
        holder.cover.setImageResource(
                android.R.drawable.ic_menu_gallery
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

        BookViewHolder(@NonNull View itemView) {
            super(itemView);

            cover = itemView.findViewById(
                    R.id.bookCover
            );

            title = itemView.findViewById(
                    R.id.bookTitle
            );

            author = itemView.findViewById(
                    R.id.bookAuthor
            );

            publishedDate = itemView.findViewById(
                    R.id.bookPublishedDate
            );
        }
    }
}