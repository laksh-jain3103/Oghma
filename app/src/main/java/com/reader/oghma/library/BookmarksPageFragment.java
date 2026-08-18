package com.reader.oghma.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.reader.oghma.R;
import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.entity.Bookmark;
import com.reader.oghma.data.repository.BookRepository;
import com.reader.oghma.data.repository.BookmarkRepository;
import com.reader.oghma.reader.ReaderFragment;

import java.util.List;

public class BookmarksPageFragment extends Fragment {

    private RecyclerView bookmarksRecyclerView;
    private TextView bookmarksEmptyText;

    private BookmarkAdapter bookmarkAdapter;

    private BookmarkRepository bookmarkRepository;
    private BookRepository bookRepository;


    public BookmarksPageFragment() {
        // Required empty constructor
    }


    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.fragment_bookmarks_page,
                container,
                false
        );
    }


    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {

        super.onViewCreated(
                view,
                savedInstanceState
        );


        // ---------------------------------------------------------
        // Views
        // ---------------------------------------------------------

        bookmarksRecyclerView =
                view.findViewById(
                        R.id.bookmarksRecyclerView
                );

        bookmarksEmptyText =
                view.findViewById(
                        R.id.bookmarksEmptyText
                );


        // ---------------------------------------------------------
        // Repositories
        // ---------------------------------------------------------

        bookmarkRepository =
                new BookmarkRepository(
                        requireActivity().getApplication()
                );

        bookRepository =
                new BookRepository(
                        requireActivity().getApplication()
                );


        // ---------------------------------------------------------
        // RecyclerView
        // ---------------------------------------------------------

        bookmarkAdapter =
                new BookmarkAdapter();


        bookmarksRecyclerView.setLayoutManager(
                new LinearLayoutManager(
                        requireContext()
                )
        );


        bookmarksRecyclerView.setAdapter(
                bookmarkAdapter
        );


        // ---------------------------------------------------------
        // Bookmark click
        // ---------------------------------------------------------

        bookmarkAdapter.setOnBookmarkClickListener(
                this::openBookmark
        );


        // ---------------------------------------------------------
        // Load books
        //
        // BookmarkAdapter needs these to display the book title.
        // ---------------------------------------------------------

        bookRepository
                .getAllBooks()
                .observe(
                        getViewLifecycleOwner(),
                        books -> {

                            bookmarkAdapter.setBooks(
                                    books
                            );
                        }
                );


        // ---------------------------------------------------------
        // Load bookmarks
        // ---------------------------------------------------------

        bookmarkRepository.getAllBookmarks(
                bookmarks -> {

                    if (!isAdded()) {
                        return;
                    }


                    requireActivity().runOnUiThread(
                            () -> {

                                bookmarkAdapter.setBookmarks(
                                        bookmarks
                                );


                                updateEmptyState(
                                        bookmarks
                                );
                            }
                    );
                }
        );
    }


    // =========================================================
    // OPEN BOOKMARK
    // =========================================================

    private void openBookmark(
            Bookmark bookmark
    ) {

        if (bookmark == null) {
            return;
        }


        // ---------------------------------------------------------
        // Find the book belonging to the bookmark
        // ---------------------------------------------------------

        bookRepository.getBook(
                bookmark.bookId,
                book -> {

                    if (book == null) {

                        if (!isAdded()) {
                            return;
                        }


                        requireActivity().runOnUiThread(
                                () -> {

                                    if (!isAdded()) {
                                        return;
                                    }


                                    android.widget.Toast.makeText(
                                            requireContext(),
                                            "Book not found.",
                                            android.widget.Toast.LENGTH_SHORT
                                    ).show();
                                }
                        );


                        return;
                    }


                    if (!isAdded()) {
                        return;
                    }


                    // -------------------------------------------------
                    // Open ReaderFragment on the UI thread
                    // -------------------------------------------------

                    requireActivity().runOnUiThread(
                            () -> {

                                if (!isAdded()) {
                                    return;
                                }


                                Bundle args =
                                        new Bundle();


                                // Tell ReaderFragment which book
                                // to open.

                                args.putInt(
                                        "bookId",
                                        book.bookId
                                );


                                // Tell ReaderFragment the exact
                                // Readium locator belonging to
                                // this bookmark.

                                args.putString(
                                        "bookmarkLocator",
                                        bookmark.locatorJson
                                );


                                ReaderFragment readerFragment =
                                        new ReaderFragment();


                                readerFragment.setArguments(
                                        args
                                );


                                requireActivity()
                                        .getSupportFragmentManager()
                                        .beginTransaction()
                                        .replace(
                                                R.id.fragmentContainer,
                                                readerFragment
                                        )
                                        .addToBackStack(null)
                                        .commit();
                            }
                    );
                }
        );
    }


    // =========================================================
    // EMPTY STATE
    // =========================================================

    private void updateEmptyState(
            List<Bookmark> bookmarks
    ) {

        if (
                bookmarks == null ||
                        bookmarks.isEmpty()
        ) {

            bookmarksRecyclerView.setVisibility(
                    View.GONE
            );

            bookmarksEmptyText.setVisibility(
                    View.VISIBLE
            );

        } else {

            bookmarksRecyclerView.setVisibility(
                    View.VISIBLE
            );

            bookmarksEmptyText.setVisibility(
                    View.GONE
            );
        }
    }
}