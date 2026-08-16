package com.reader.oghma.library;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.reader.oghma.R;
import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.entity.Bookmark;

import java.util.ArrayList;
import java.util.List;

public class BookmarkAdapter
        extends RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder> {

    private final List<Bookmark> bookmarks =
            new ArrayList<>();

    private OnBookmarkClickListener onBookmarkClickListener;

    public interface OnBookmarkClickListener {

        void onBookmarkClick(Bookmark bookmark);
    }

    public void setOnBookmarkClickListener(
            OnBookmarkClickListener listener
    ) {

        this.onBookmarkClickListener =
                listener;
    }

    private final List<Book> books =
            new ArrayList<>();


    // =========================================================
    // Create ViewHolder
    // =========================================================

    @NonNull
    @Override
    public BookmarkViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        View view =
                LayoutInflater
                        .from(parent.getContext())
                        .inflate(
                                R.layout.item_bookmark,
                                parent,
                                false
                        );

        return new BookmarkViewHolder(
                view
        );
    }


    // =========================================================
    // Bind bookmark
    // =========================================================

    @Override
    public void onBindViewHolder(
            @NonNull BookmarkViewHolder holder,
            int position
    ) {

        Bookmark bookmark =
                bookmarks.get(position);


        // -----------------------------------------------------
        // Find the actual book
        // -----------------------------------------------------

        Book book =
                findBook(
                        bookmark.bookId
                );


        // -----------------------------------------------------
        // Book title
        // -----------------------------------------------------

        if (book != null) {

            holder.bookTitle.setText(
                    book.title != null
                            ? book.title
                            : "Untitled"
            );

        } else {

            holder.bookTitle.setText(
                    "Unknown book"
            );
        }


        // -----------------------------------------------------
        // Chapter
        // -----------------------------------------------------

        holder.chapter.setText(
                bookmark.chapterTitle != null
                        && !bookmark.chapterTitle
                        .trim()
                        .isEmpty()
                        ? bookmark.chapterTitle
                        : "Unknown chapter"
        );


        // -----------------------------------------------------
        // Selected text
        // -----------------------------------------------------

        holder.selectedText.setText(
                bookmark.selectedText != null
                        ? bookmark.selectedText
                        : ""
        );

        holder.itemView.setOnClickListener(
                v -> {

                    if (onBookmarkClickListener != null) {

                        onBookmarkClickListener.onBookmarkClick(
                                bookmark
                        );
                    }
                }
        );
    }


    // =========================================================
    // Number of bookmarks
    // =========================================================

    @Override
    public int getItemCount() {

        return bookmarks.size();
    }


    // =========================================================
    // Set bookmarks
    // =========================================================

    public void setBookmarks(
            List<Bookmark> newBookmarks
    ) {

        bookmarks.clear();

        if (newBookmarks != null) {

            bookmarks.addAll(
                    newBookmarks
            );
        }

        notifyDataSetChanged();
    }


    // =========================================================
    // Find book by ID
    // =========================================================

    private Book findBook(
            int bookId
    ) {

        for (Book book : books) {

            if (book.bookId == bookId) {

                return book;
            }
        }

        return null;
    }


    // =========================================================
    // Set books
    // =========================================================

    public void setBooks(
            List<Book> newBooks
    ) {

        books.clear();

        if (newBooks != null) {

            books.addAll(
                    newBooks
            );
        }

        notifyDataSetChanged();
    }


    // =========================================================
    // ViewHolder
    // =========================================================

    static class BookmarkViewHolder
            extends RecyclerView.ViewHolder {

        TextView bookTitle;
        TextView chapter;
        TextView selectedText;


        BookmarkViewHolder(
                @NonNull View itemView
        ) {

            super(itemView);


            bookTitle =
                    itemView.findViewById(
                            R.id.bookmarkBookTitle
                    );


            chapter =
                    itemView.findViewById(
                            R.id.bookmarkChapter
                    );


            selectedText =
                    itemView.findViewById(
                            R.id.bookmarkText
                    );
        }
    }
}