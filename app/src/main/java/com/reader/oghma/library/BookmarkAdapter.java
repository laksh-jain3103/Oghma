package com.reader.oghma.library;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.reader.oghma.R;
import com.reader.oghma.data.entity.Bookmark;

import java.util.ArrayList;
import java.util.List;

public class BookmarkAdapter
        extends RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder> {

    private final List<Bookmark> bookmarks =
            new ArrayList<>();

    @NonNull
    @Override
    public BookmarkViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        View view =
                LayoutInflater.from(parent.getContext())
                        .inflate(
                                R.layout.item_bookmark,
                                parent,
                                false
                        );

        return new BookmarkViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull BookmarkViewHolder holder,
            int position
    ) {

        Bookmark bookmark =
                bookmarks.get(position);

        holder.bookTitle.setText(
                "Book #" + bookmark.bookId
        );

        holder.chapter.setText(
                bookmark.chapterTitle != null
                        && !bookmark.chapterTitle.trim().isEmpty()
                        ? bookmark.chapterTitle
                        : "Unknown chapter"
        );

        holder.selectedText.setText(
                bookmark.selectedText != null
                        ? bookmark.selectedText
                        : ""
        );
    }

    @Override
    public int getItemCount() {
        return bookmarks.size();
    }

    public void setBookmarks(
            List<Bookmark> newBookmarks
    ) {

        bookmarks.clear();

        if (newBookmarks != null) {
            bookmarks.addAll(newBookmarks);
        }

        notifyDataSetChanged();
    }

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