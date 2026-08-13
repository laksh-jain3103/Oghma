package com.reader.oghma.library;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.reader.oghma.R;
import com.reader.oghma.data.entity.Book;

import java.util.ArrayList;
import java.util.List;

public class BookAdapter
        extends RecyclerView.Adapter<BookAdapter.BookViewHolder> {

    private OnBookClickListener onBookClickListener;

    private final List<Book> books = new ArrayList<>();

    public void setBooks(List<Book> newBooks) {

        books.clear();

        if (newBooks != null) {
            books.addAll(newBooks);
        }

        notifyDataSetChanged();
    }

    public interface OnBookClickListener {
        void onBookClick(Book book);
    }

    public void setOnBookClickListener(
            OnBookClickListener listener) {

        this.onBookClickListener = listener;
    }

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(
                        R.layout.item_book,
                        parent,
                        false
                );

        return new BookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull BookViewHolder holder,
            int position) {

        Book book = books.get(position);

        holder.bookTitle.setText(book.title);
        holder.bookAuthor.setText(
                book.author != null
                        ? book.author
                        : "Unknown author"
        );

        holder.bookFormat.setText(
                book.format != null
                        ? book.format.toUpperCase()
                        : "BOOK"
        );

        holder.itemView.setOnClickListener(v -> {

            if (onBookClickListener != null) {
                onBookClickListener.onBookClick(book);
            }

        });

        // Cover loading will be added later.
    }

    @Override
    public int getItemCount() {
        return books.size();
    }

    public static class BookViewHolder
            extends RecyclerView.ViewHolder {

        ImageView bookCover;
        TextView bookTitle;
        TextView bookAuthor;
        TextView bookFormat;

        public BookViewHolder(@NonNull View itemView) {

            super(itemView);

            bookCover = itemView.findViewById(
                    R.id.bookCover
            );

            bookTitle = itemView.findViewById(
                    R.id.bookTitle
            );

            bookAuthor = itemView.findViewById(
                    R.id.bookAuthor
            );

            bookFormat = itemView.findViewById(
                    R.id.bookFormat
            );
        }
    }
}