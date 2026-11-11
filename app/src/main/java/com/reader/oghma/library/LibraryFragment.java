package com.reader.oghma.library;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewpager2.widget.ViewPager2;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.activity.OnBackPressedCallback;
import com.reader.oghma.BuildConfig;
import com.reader.oghma.R;
import com.reader.oghma.api.GoogleBooksClient;
import com.reader.oghma.api.model.GoogleBooksResponse;
import com.reader.oghma.api.model.GoogleBookItem;
import com.reader.oghma.data.entity.Book;
import com.reader.oghma.reader.HighlightDialog;
import com.reader.oghma.reader.ReaderFragment;
import com.reader.oghma.settings.SettingsFragment;
import com.reader.oghma.data.entity.Progress;
import com.reader.oghma.data.repository.ProgressRepository;
import com.reader.oghma.search.GoogleBooksAdapter;
import com.reader.oghma.search.UnifiedBookItem;
import com.reader.oghma.api.GutendexClient;
import com.reader.oghma.api.model.GutendexResponse;
import com.reader.oghma.api.model.GutendexBook;
import android.content.Intent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import com.reader.oghma.data.entity.Bookmark;
import com.reader.oghma.data.repository.BookmarkRepository;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LibraryFragment extends Fragment {

    // =========================================================
    // Library
    // =========================================================

    private LibraryViewModel viewModel;

    private BookAdapter adapter;

    private RecyclerView bookRecyclerView;

    private View emptyLibrary;

    private FloatingActionButton addBookButton;

    private ImageButton searchButton;

    private ImageButton viewModeButton;

    private ImageButton sortButton;

    private ImageButton menuButton;


    // =========================================================
    // Navigation & Bookmarks
    // =========================================================

    private androidx.drawerlayout.widget.DrawerLayout drawerLayout;

    private com.google.android.material.navigation.NavigationView navigationView;

    private ViewPager2 libraryPager;

    private View bookBookmarksPage;

    private RecyclerView bookBookmarksRecyclerView;

    private View bookBookmarksEmptyText;

    private BookAdapter bookBookmarksAdapter;

    private View bookmarksPage;

    private View findMorePage;

    private RecyclerView bookmarksRecyclerView;

    private View bookmarksEmptyText;

    private BookmarkAdapter bookmarkAdapter;

    private BookmarkRepository bookmarkRepository;

    private EditText googleBooksSearchInput;

    private ImageButton googleBooksSearchButton;

    private RecyclerView googleBooksRecyclerView;

    private ProgressBar googleBooksProgress;

    private View googleBooksEmptyText;

    private GoogleBooksAdapter googleBooksAdapter;

    // =========================================================
    // View state
    // =========================================================

    private boolean isGridView = false;

    private GridSpacingItemDecoration gridSpacing;


    // =========================================================
    // Search
    // =========================================================

    /*
     * This keeps the complete list of books.
     *
     * The adapter may contain only the search results,
     * but this list always contains everything from Room.
     */

    private final List<Book> allBooks =
            new ArrayList<>();


    private String searchQuery = "";

    private int searchRequestId = 0;

    private int sortMode = 0;

    private boolean sortDescending = false;


    // =========================================================
    // Import
    // =========================================================

    private ActivityResultLauncher<String[]> filePickerLauncher;

    private BookIngestor bookIngestor;

    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor();


    // =========================================================
    // Constructor
    // =========================================================

    public LibraryFragment() {
        // Required empty constructor
    }

    @Override
    public void onDestroyView() {
        searchRequestId++;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        searchRequestId++;
        downloadExecutor.shutdown();
        super.onDestroy();
    }



    // =========================================================
    // Fragment creation
    // =========================================================

    @Override
    public void onCreate(
            @Nullable Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);


        filePickerLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.OpenDocument(),
                        uri -> {

                            if (uri != null) {

                                importBook(uri);
                            }
                        }
                );
    }


    // =========================================================
    // Create layout
    // =========================================================

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.fragment_library,
                container,
                false
        );
    }


    // =========================================================
    // Setup
    // =========================================================

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {

        super.onViewCreated(
                view,
                savedInstanceState
        );


        viewModel =
                new ViewModelProvider(this)
                        .get(
                                LibraryViewModel.class
                        );


        // -----------------------------------------------------
        // Find views
        // -----------------------------------------------------

        bookRecyclerView =
                view.findViewById(
                        R.id.bookRecyclerView
                );


        emptyLibrary =
                view.findViewById(
                        R.id.emptyLibrary
                );


        addBookButton =
                view.findViewById(
                        R.id.addBookButton
                );


        searchButton =
                view.findViewById(
                        R.id.searchButton
                );


        viewModeButton =
                view.findViewById(
                        R.id.viewModeButton
                );


        sortButton =
                view.findViewById(
                        R.id.sortButton
                );


        menuButton =
                view.findViewById(
                        R.id.menuButton
                );


        drawerLayout =
                view.findViewById(
                        R.id.drawerLayout
                );


        navigationView =
                view.findViewById(
                        R.id.navigationView
                );


        bookBookmarksPage =
                view.findViewById(
                        R.id.bookBookmarksPage
                );

        bookBookmarksRecyclerView =
                view.findViewById(
                        R.id.bookBookmarksRecyclerView
                );

        bookBookmarksEmptyText =
                view.findViewById(
                        R.id.bookBookmarksEmptyText
                );

        bookBookmarksAdapter =
                new BookAdapter();

        bookBookmarksAdapter.setOnBookClickListener(
                book -> openBook(book)
        );

        bookBookmarksAdapter.setOnBookMoreClickListener(
                book -> showBookMenu(book)
        );

        bookBookmarksRecyclerView.setLayoutManager(
                new LinearLayoutManager(
                        requireContext()
                )
        );

        bookBookmarksRecyclerView.setAdapter(
                bookBookmarksAdapter
        );

        viewModel.getBookmarkedBooks().observe(
                getViewLifecycleOwner(),
                bookmarkedBooks -> {
                    if (!isAdded()) return;
                    bookBookmarksAdapter.setBooks(bookmarkedBooks);
                    boolean isEmpty = bookmarkedBooks == null || bookmarkedBooks.isEmpty();
                    bookBookmarksEmptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                    bookBookmarksRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                }
        );


        bookmarksPage =
                view.findViewById(
                        R.id.bookmarksPage
                );

        bookmarksRecyclerView =
                view.findViewById(
                        R.id.bookmarksRecyclerView
                );

        bookmarksEmptyText =
                view.findViewById(
                        R.id.bookmarksEmptyText
                );

        bookmarkRepository =
                new BookmarkRepository(
                        requireActivity()
                                .getApplication()
                );

        bookmarkAdapter =
                new BookmarkAdapter();

        bookmarkAdapter.setOnBookmarkClickListener(
                new BookmarkAdapter.OnBookmarkClickListener() {

                    @Override
                    public void onBookmarkClick(Bookmark bookmark) {

                        openBookmark(
                                bookmark
                        );
                    }

                    @Override
                    public void onBookmarkEdit(Bookmark bookmark) {

                        editBookmark(
                                bookmark
                        );
                    }
                }
        );

        bookmarksRecyclerView.setLayoutManager(
                new LinearLayoutManager(
                        requireContext()
                )
        );

        bookmarksRecyclerView.setAdapter(
                bookmarkAdapter
        );

        findMorePage =
                view.findViewById(
                        R.id.findMorePage
                );

        googleBooksSearchInput =
                view.findViewById(
                        R.id.googleBooksSearchInput
                );

        googleBooksSearchButton =
                view.findViewById(
                        R.id.googleBooksSearchButton
                );

        googleBooksRecyclerView =
                view.findViewById(
                        R.id.googleBooksRecyclerView
                );

        googleBooksProgress =
                view.findViewById(
                        R.id.googleBooksProgress
                );

        googleBooksEmptyText =
                view.findViewById(
                        R.id.googleBooksEmptyText
                );

        googleBooksAdapter =
                new GoogleBooksAdapter(
                        book -> {
                            if (book.source == UnifiedBookItem.Source.GUTENBERG || book.isDownloadable) {
                                downloadUnifiedBook(book);
                            } else if (book.previewUrl != null && !book.previewUrl.isEmpty()) {
                                openGoogleBooksPreview(book.previewUrl);
                            } else {
                                Toast.makeText(requireContext(), "This book is not available for download or preview.", Toast.LENGTH_SHORT).show();
                            }
                        }
                );

        googleBooksRecyclerView.setLayoutManager(
                new LinearLayoutManager(
                        requireContext()
                )
        );

        googleBooksRecyclerView.setAdapter(
                googleBooksAdapter
        );


        // -----------------------------------------------------
        // Grid spacing
        // -----------------------------------------------------

        gridSpacing =
                new GridSpacingItemDecoration(
                        16
                );


        // -----------------------------------------------------
        // Set everything up
        // -----------------------------------------------------

        setupRecyclerView();

        setupViewModel();


        bookIngestor =
                new BookIngestor(
                        requireContext(),
                        viewModel.getRepository()
                );


        // The pages must be inside the pager before the bottom bar starts driving it.
        setupPager(view);

        setupButtons();


        // -----------------------------------------------------
        // Start on Library
        // -----------------------------------------------------

        navigationView.setCheckedItem(
                R.id.nav_library
        );

        requireActivity()
                .getOnBackPressedDispatcher()
                .addCallback(
                        getViewLifecycleOwner(),
                        new OnBackPressedCallback(true) {

                            @Override
                            public void handleOnBackPressed() {

                                int selectedId =
                                        navigationView.getCheckedItem() != null
                                                ? navigationView.getCheckedItem().getItemId()
                                                : R.id.nav_library;

                                if (
                                        selectedId != R.id.nav_library
                                ) {

                                    int index = pageIndexForNav(R.id.nav_library);
                                    libraryPager.setCurrentItem(index, true);
                                    navigationView.setCheckedItem(
                                            R.id.nav_library
                                    );

                                    return;
                                }

                                setEnabled(false);

                                requireActivity()
                                        .onBackPressed();
                            }
                        }
                );
    }


    // =========================================================
    // RecyclerView
    // =========================================================

    private void setupRecyclerView() {

        adapter =
                new BookAdapter();


        // -----------------------------------------------------
        // Normal card click
        // -----------------------------------------------------

        adapter.setOnBookClickListener(
                book -> openBook(book)
        );


        // -----------------------------------------------------
        // Three-dot button
        // -----------------------------------------------------

        adapter.setOnBookMoreClickListener(
                book -> showBookMenu(book)
        );


        // -----------------------------------------------------
        // Default list layout
        // -----------------------------------------------------

        bookRecyclerView.setLayoutManager(
                new LinearLayoutManager(
                        requireContext()
                )
        );


        bookRecyclerView.setAdapter(
                adapter
        );
    }

    /** Change colour / note of a saved highlight, copy its text, or delete it. */
    private void editBookmark(
            Bookmark bookmark
    ) {

        HighlightDialog.show(
                requireContext(),
                bookmark.selectedText,
                bookmark.color,
                bookmark.note,
                true,
                new HighlightDialog.Listener() {

                    @Override
                    public void onSave(
                            int color,
                            String note
                    ) {

                        bookmarkRepository.updateNoteAndColor(
                                bookmark.bookmarkId,
                                note,
                                color
                        );

                        loadBookmarks();
                    }

                    @Override
                    public void onCopy() {

                        HighlightDialog.copyToClipboard(
                                requireContext(),
                                bookmark.selectedText
                        );
                    }

                    @Override
                    public void onDelete() {

                        bookmarkRepository.deleteBookmarkById(
                                bookmark.bookmarkId
                        );

                        loadBookmarks();
                    }
                }
        );
    }


    private void openBookmark(
            Bookmark bookmark
    ) {

        Bundle args =
                new Bundle();

        args.putInt(
                "bookId",
                bookmark.bookId
        );

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

    // =========================================================
    // Open book
    // =========================================================

    private void openBook(Book book) {

        Bundle args =
                new Bundle();


        args.putInt(
                "bookId",
                book.bookId
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


    // =========================================================
    // Three-dot menu
    // =========================================================

    private void showBookMenu(Book book) {

        String bookmarkOption =
                book.isBookmarked
                        ? "Remove Bookmark"
                        : "Add Bookmark";

        String[] options = {
                bookmarkOption,
                "Book details",
                "Remove from library"
        };


        new AlertDialog.Builder(
                requireContext()
        )
                .setTitle(
                        book.title
                )
                .setItems(
                        options,
                        (dialog, which) -> {

                            if (which == 0) {

                                boolean newStatus = !book.isBookmarked;

                                viewModel.getRepository()
                                        .updateBookmarkStatus(
                                                book.bookId,
                                                newStatus
                                        );

                                Toast.makeText(
                                        requireContext(),
                                        newStatus
                                                ? "Added to bookmarks"
                                                : "Removed from bookmarks",
                                        Toast.LENGTH_SHORT
                                ).show();

                            } else if (which == 1) {

                                showBookDetails(
                                        book
                                );

                            } else if (which == 2) {

                                confirmRemoveBook(
                                        book
                                );
                            }
                        }
                )
                .show();
    }


    // =========================================================
    // Book details
    // =========================================================

    private void showBookDetails(Book book) {

        // Load the saved reading progress first, then show the dialog.
        new ProgressRepository(
                requireActivity().getApplication()
        ).get(
                book.bookId,
                progress -> {

                    if (!isAdded() || getActivity() == null) {
                        return;
                    }

                    getActivity().runOnUiThread(
                            () -> showBookDetailsDialog(
                                    book,
                                    progress
                            )
                    );
                }
        );
    }


    private void showBookDetailsDialog(
            Book book,
            Progress progress
    ) {

        String progressText =
                progress == null
                        ? "Not started"
                        : Math.round(progress.percentage) + "%";

        String author =
                book.author != null
                        ? book.author
                        : "Unknown author";


        String format =
                book.format != null
                        ? book.format.toUpperCase()
                        : "Unknown";


        String details =
                "Title: "
                        + book.title
                        + "\n\n"
                        + "Author: "
                        + author
                        + "\n\n"
                        + "Format: "
                        + format
                        + "\n\n"
                        + "Series: Not set"
                        + "\n\n"
                        + "Progress: " + progressText
                        + "\n\n"
                        + "Tags: None";


        new AlertDialog.Builder(
                requireContext()
        )
                .setTitle(
                        "Book details"
                )
                .setMessage(
                        details
                )
                .setPositiveButton(
                        "Open book",
                        (dialog, which) ->
                                openBook(book)
                )
                .setNegativeButton(
                        "Close",
                        null
                )
                .show();
    }


    // =========================================================
    // Remove confirmation
    // =========================================================

    private void confirmRemoveBook(
            Book book
    ) {

        new AlertDialog.Builder(
                requireContext()
        )
                .setTitle(
                        "Remove book?"
                )
                .setMessage(
                        "Remove \""
                                + book.title
                                + "\" from your library?"
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Remove",
                        (dialog, which) ->
                                removeBook(book)
                )
                .show();
    }


    // =========================================================
    // Remove book
    // =========================================================

    private void removeBook(Book book) {

        // -----------------------------------------------------
        // Delete database record
        // -----------------------------------------------------

        viewModel
                .getRepository()
                .deleteBook(book);


        // -----------------------------------------------------
        // Delete local book file
        // -----------------------------------------------------

        if (
                book.filePath != null
                        && !book.filePath.isEmpty()
        ) {

            File bookFile =
                    new File(
                            book.filePath
                    );


            if (bookFile.exists()) {

                boolean deleted =
                        bookFile.delete();


                if (!deleted) {

                    bookFile.deleteOnExit();
                }
            }
        }


        // -----------------------------------------------------
        // Delete generated cover
        // -----------------------------------------------------

        if (
                book.coverPath != null
                        && !book.coverPath.isEmpty()
        ) {

            File coverFile =
                    new File(
                            book.coverPath
                    );


            if (coverFile.exists()) {

                boolean deleted =
                        coverFile.delete();


                if (!deleted) {

                    coverFile.deleteOnExit();
                }
            }
        }


        Toast.makeText(
                requireContext(),
                "Removed from library",
                Toast.LENGTH_SHORT
        ).show();
    }


    // =========================================================
    // Toggle list/grid
    // =========================================================

    private void toggleViewMode() {

        isGridView =
                !isGridView;


        adapter.setGridMode(
                isGridView
        );


        if (isGridView) {

            GridLayoutManager gridLayoutManager =
                    new GridLayoutManager(
                            requireContext(),
                            2
                    );


            bookRecyclerView.setLayoutManager(
                    gridLayoutManager
            );


            bookRecyclerView.addItemDecoration(
                    gridSpacing
            );


            viewModeButton.setImageResource(
                    R.drawable.ic_view_grid
            );


            viewModeButton.setContentDescription(
                    "Grid view"
            );

        } else {

            bookRecyclerView.setLayoutManager(
                    new LinearLayoutManager(
                            requireContext()
                    )
            );


            bookRecyclerView.removeItemDecoration(
                    gridSpacing
            );


            viewModeButton.setImageResource(
                    R.drawable.ic_view_list
            );


            viewModeButton.setContentDescription(
                    "List view"
            );
        }
    }


    // =========================================================
    // ViewModel
    // =========================================================

    private void setupViewModel() {

        viewModel.getBooks().observe(
                getViewLifecycleOwner(),
                books -> {

                    // -----------------------------------------
                    // Keep complete library list
                    // -----------------------------------------

                    allBooks.clear();

                    if (books != null) {

                        allBooks.addAll(
                                books
                        );
                    }

                    // Give BookmarkAdapter the complete
                    // library list, not search results.

                    bookmarkAdapter.setBooks(
                            allBooks
                    );

                    // -----------------------------------------
                    // Apply current search
                    // -----------------------------------------

                    applySearch();


                    // -----------------------------------------
                    // Empty state
                    // -----------------------------------------

                    boolean isEmpty =
                            allBooks.isEmpty();


                    emptyLibrary.setVisibility(
                            isEmpty
                                    ? View.VISIBLE
                                    : View.GONE
                    );


                    /*
                     * Only show the RecyclerView when the
                     * library actually contains books.
                     *
                     * The search itself can produce zero
                     * results, so applySearch() handles that
                     * separately.
                     */

                    bookRecyclerView.setVisibility(
                            isEmpty
                                    ? View.GONE
                                    : View.VISIBLE
                    );
                }
        );
    }


    // =========================================================
    // Search
    // =========================================================

    private void showSearchDialog() {

        final EditText searchInput =
                new EditText(
                        requireContext()
                );


        searchInput.setSingleLine(
                true
        );


        searchInput.setHint(
                "Title, author, or format"
        );


        searchInput.setInputType(
                InputType.TYPE_CLASS_TEXT
        );


        if (!searchQuery.isEmpty()) {

            searchInput.setText(
                    searchQuery
            );


            searchInput.setSelection(
                    searchInput.length()
            );
        }


        AlertDialog dialog =
                new AlertDialog.Builder(
                        requireContext()
                )
                        .setTitle(
                                "Search library"
                        )
                        .setView(
                                searchInput
                        )
                        .setPositiveButton(
                                "Search",
                                null
                        )
                        .setNeutralButton(
                                "Clear",
                                null
                        )
                        .setNegativeButton(
                                "Cancel",
                                null
                        )
                        .create();


        dialog.setOnShowListener(
                ignored -> {

                    dialog.getButton(
                            AlertDialog.BUTTON_POSITIVE
                    ).setOnClickListener(
                            v -> {

                                searchQuery =
                                        searchInput
                                                .getText()
                                                .toString()
                                                .trim();


                                applySearch();


                                dialog.dismiss();
                            }
                    );


                    dialog.getButton(
                            AlertDialog.BUTTON_NEUTRAL
                    ).setOnClickListener(
                            v -> {

                                searchQuery =
                                        "";


                                applySearch();


                                dialog.dismiss();
                            }
                    );


                    searchInput.requestFocus();


                    dialog.getWindow()
                            .setSoftInputMode(
                                    android.view.WindowManager
                                            .LayoutParams
                                            .SOFT_INPUT_STATE_ALWAYS_VISIBLE
                            );
                }
        );


        dialog.show();
    }


    private void applySearch() {

        if (adapter == null) {
            return;
        }


        // ---------------------------------------------------------
        // Start with the complete library
        // ---------------------------------------------------------

        List<Book> filteredBooks =
                new ArrayList<>();


        if (
                searchQuery == null
                        || searchQuery.isEmpty()
        ) {

            filteredBooks.addAll(
                    allBooks
            );

        } else {

            String query =
                    searchQuery
                            .toLowerCase(
                                    Locale.ROOT
                            );


            // -----------------------------------------------------
            // Search title, author, and format
            // -----------------------------------------------------

            for (Book book : allBooks) {

                if (book == null) {
                    continue;
                }


                String title =
                        book.title != null
                                ? book.title
                                : "";


                String author =
                        book.author != null
                                ? book.author
                                : "";


                String format =
                        book.format != null
                                ? book.format
                                : "";


                boolean matches =
                        title.toLowerCase(
                                Locale.ROOT
                        ).contains(query)
                                ||
                                author.toLowerCase(
                                        Locale.ROOT
                                ).contains(query)
                                ||
                                format.toLowerCase(
                                        Locale.ROOT
                                ).contains(query);


                if (matches) {

                    filteredBooks.add(
                            book
                    );
                }
            }
        }


        // ---------------------------------------------------------
        // Sort results
        // ---------------------------------------------------------

        sortBooks(
                filteredBooks
        );


        // ---------------------------------------------------------
        // Show results
        // ---------------------------------------------------------

        adapter.setBooks(
                filteredBooks
        );
    }

    private void sortBooks(
            List<Book> books
    ) {

        Comparator<Book> comparator;


        switch (sortMode) {

            case 1:

                // -------------------------------------------------
                // Author A-Z
                // -------------------------------------------------

                comparator =
                        Comparator.comparing(
                                book ->
                                        book.author != null
                                                ? book.author
                                                : "",
                                String.CASE_INSENSITIVE_ORDER
                        );

                break;


            case 2:

                // -------------------------------------------------
                // Date added
                // -------------------------------------------------

                comparator =
                        Comparator.comparingLong(
                                book ->
                                        book.addedAt
                        );

                break;


            case 3:

                // -------------------------------------------------
                // Format
                // -------------------------------------------------

                comparator =
                        Comparator.comparing(
                                book ->
                                        book.format != null
                                                ? book.format
                                                : "",
                                String.CASE_INSENSITIVE_ORDER
                        );

                break;


            case 0:

            default:

                // -------------------------------------------------
                // Title A-Z
                // -------------------------------------------------

                comparator =
                        Comparator.comparing(
                                book ->
                                        book.title != null
                                                ? book.title
                                                : "",
                                String.CASE_INSENSITIVE_ORDER
                        );

                break;
        }


        if (sortDescending) {

            comparator =
                    comparator.reversed();
        }


        books.sort(
                comparator
        );
    }


    private void showSortDialog() {

        String[] sortOptions = {
                "Title",
                "Author",
                "Date added",
                "Format"
        };


        String[] directionOptions = {
                "Ascending",
                "Descending"
        };


        new AlertDialog.Builder(
                requireContext()
        )
                .setTitle(
                        "Sort library"
                )
                .setSingleChoiceItems(
                        sortOptions,
                        sortMode,
                        (dialog, which) -> {

                            sortMode =
                                    which;

                            dialog.dismiss();

                            showSortDirectionDialog();
                        }
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private void showSortDirectionDialog() {

        String[] directionOptions = {
                "Ascending",
                "Descending"
        };


        int checked =
                sortDescending
                        ? 1
                        : 0;


        new AlertDialog.Builder(
                requireContext()
        )
                .setTitle(
                        "Sort direction"
                )
                .setSingleChoiceItems(
                        directionOptions,
                        checked,
                        (dialog, which) -> {

                            sortDescending =
                                    which == 1;


                            applySearch();


                            dialog.dismiss();
                        }
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    // =========================================================
    // Buttons
    // =========================================================

    private void setupButtons() {

        // -----------------------------------------------------
        // Add book
        // -----------------------------------------------------

        addBookButton.setOnClickListener(
                v -> {

                    filePickerLauncher.launch(
                            new String[]{
                                    "application/epub+zip",
                                    "application/pdf",
                                    "text/plain"
                            }
                    );
                }
        );


        // -----------------------------------------------------
        // Search
        // -----------------------------------------------------

        searchButton.setOnClickListener(
                v -> {

                    /*
                     * Search only applies to the local library.
                     *
                     * Find More will eventually have its own
                     * internet/source search system.
                     */

                    if (
                            navigationView.getCheckedItem() == null
                                    || navigationView.getCheckedItem().getItemId()
                                    != R.id.nav_library
                    ) {

                        int index = pageIndexForNav(R.id.nav_library);
                        libraryPager.setCurrentItem(index, true);
                        navigationView.setCheckedItem(
                                R.id.nav_library
                        );
                    }


                    showSearchDialog();
                }
        );


        // -----------------------------------------------------
        // List / Grid
        // -----------------------------------------------------

        viewModeButton.setOnClickListener(
                v ->
                        toggleViewMode()
        );


        // -----------------------------------------------------
        // Sort
        // -----------------------------------------------------

        sortButton.setOnClickListener(
                v -> showSortDialog()
        );


        // -----------------------------------------------------
        // Menu / Drawer
        // -----------------------------------------------------

        menuButton.setOnClickListener(
                v -> drawerLayout.openDrawer(
                        androidx.core.view.GravityCompat.START
                )
        );


        // -----------------------------------------------------
        // Navigation View
        // -----------------------------------------------------

        navigationView.setNavigationItemSelectedListener(
                item -> {

                    int itemId = item.getItemId();

                    if (itemId == R.id.nav_settings) {

                        drawerLayout.closeDrawers();

                        requireActivity()
                                .getSupportFragmentManager()
                                .beginTransaction()
                                .replace(
                                        R.id.fragmentContainer,
                                        new SettingsFragment()
                                )
                                .addToBackStack(null)
                                .commit();

                        return true;
                    }

                    int index =
                            pageIndexForNav(
                                    itemId
                            );

                    if (index < 0) {
                        return false;
                    }

                    if (libraryPager.getCurrentItem() != index) {

                        boolean adjacent =
                                Math.abs(
                                        libraryPager.getCurrentItem() - index
                                ) == 1;

                        libraryPager.setCurrentItem(
                                index,
                                adjacent
                        );
                    }

                    onPageShown(index);

                    drawerLayout.closeDrawers();

                    return true;
                }
        );

        // -----------------------------------------------------
        // Google Books Search
        // -----------------------------------------------------

        googleBooksSearchButton.setOnClickListener(
                v -> {

                    String query =
                            googleBooksSearchInput
                                    .getText()
                                    .toString()
                                    .trim();

                    if (query.isEmpty()) {

                        Toast.makeText(
                                requireContext(),
                                "Enter a book title or author",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    searchGoogleBooks(query);
                }
        );
    }


    // =========================================================
    // Library page
    // =========================================================

    private void showLibraryPage() {

        viewModeButton.setVisibility(
                View.VISIBLE
        );

        addBookButton.setVisibility(
                View.VISIBLE
        );

        viewModeButton.setAlpha(1f);

        addBookButton.setAlpha(1f);

        // Show the list or the "empty" message, depending on what is in the library.
        boolean isEmpty =
                allBooks.isEmpty();

        emptyLibrary.setVisibility(
                isEmpty
                        ? View.VISIBLE
                        : View.GONE
        );

        bookRecyclerView.setVisibility(
                isEmpty
                        ? View.GONE
                        : View.VISIBLE
        );
    }


    private void showBookBookmarksPage() {

        viewModeButton.setVisibility(
                View.GONE
        );

        addBookButton.setVisibility(
                View.GONE
        );
    }


    private void showBookmarksPage() {

        viewModeButton.setVisibility(
                View.GONE
        );

        addBookButton.setVisibility(
                View.GONE
        );

        loadBookmarks();
    }


    // =========================================================
    // Swipeable pages
    // =========================================================

    private static final int PAGE_LIBRARY = 0;

    private static final int PAGE_BOOKMARKS = 1;

    private static final int PAGE_NOTES = 2;

    private static final int PAGE_FIND_MORE = 3;


    private int pageIndexForNav(
            int navId
    ) {

        if (navId == R.id.nav_library) {
            return PAGE_LIBRARY;
        }

        if (navId == R.id.nav_book_bookmarks) {
            return PAGE_BOOKMARKS;
        }

        if (navId == R.id.nav_notes) {
            return PAGE_NOTES;
        }

        if (navId == R.id.nav_find_more) {
            return PAGE_FIND_MORE;
        }

        return -1;
    }


    private int navForPageIndex(
            int index
    ) {

        switch (index) {

            case PAGE_BOOKMARKS:
                return R.id.nav_book_bookmarks;

            case PAGE_NOTES:
                return R.id.nav_notes;

            case PAGE_FIND_MORE:
                return R.id.nav_find_more;

            default:
                return R.id.nav_library;
        }
    }


    private void onPageShown(
            int index
    ) {

        if (index == PAGE_LIBRARY) {

            showLibraryPage();

        } else if (index == PAGE_BOOKMARKS) {

            showBookBookmarksPage();

        } else if (index == PAGE_NOTES) {

            showBookmarksPage();

        } else {

            showFindMorePage();
        }
    }


    /**
     * Moves the four existing page views into a ViewPager2 so they can be swiped.
     *
     * The views themselves are untouched (same ids, same fields, same logic): they are only
     * re-parented, so everything that already works with them keeps working.
     */
    private void setupPager(
            View root
    ) {

        libraryPager =
                root.findViewById(
                        R.id.libraryPager
                );

        // Page 0: Library = the empty-state message + the book list, stacked.
        FrameLayout libraryContainer =
                new FrameLayout(
                        requireContext()
                );

        // Opaque, like the other pages, so nothing shows through while sliding.
        libraryContainer.setBackgroundColor(
                requireContext().getColor(
                        R.color.background
                )
        );

        moveInto(libraryContainer, emptyLibrary);

        moveInto(libraryContainer, bookRecyclerView);

        FrameLayout bookBookmarksContainer =
                new FrameLayout(
                        requireContext()
                );

        moveInto(bookBookmarksContainer, bookBookmarksPage);

        FrameLayout notesContainer =
                new FrameLayout(
                        requireContext()
                );

        moveInto(notesContainer, bookmarksPage);

        FrameLayout findMoreContainer =
                new FrameLayout(
                        requireContext()
                );

        moveInto(findMoreContainer, findMorePage);

        // These start hidden in the layout; inside the pager they are always "visible"
        // and the pager decides which one is on screen.
        bookBookmarksPage.setVisibility(View.VISIBLE);

        bookmarksPage.setVisibility(View.VISIBLE);

        findMorePage.setVisibility(View.VISIBLE);

        final View[] pages = {
                libraryContainer,
                bookBookmarksContainer,
                notesContainer,
                findMoreContainer
        };

        for (View page : pages) {

            // ViewPager2 insists that pages fill it completely.
            page.setLayoutParams(
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            );
        }

        libraryPager.setAdapter(
                new PagesAdapter(pages)
        );

        // Keep all three alive so scroll positions, search text and results are not lost.
        libraryPager.setOffscreenPageLimit(
                pages.length - 1
        );

        libraryPager.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {

                    @Override
                    public void onPageSelected(
                            int position
                    ) {

                        // Swiped to a new page: highlight its drawer item.
                        int navId =
                                navForPageIndex(position);

                        if (
                                navigationView.getCheckedItem() == null
                                        || navigationView.getCheckedItem().getItemId() != navId
                        ) {

                            navigationView.setCheckedItem(
                                    navId
                            );
                        }
                    }

                    @Override
                    public void onPageScrolled(
                            int position,
                            float positionOffset,
                            int positionOffsetPixels
                    ) {

                        // The round buttons belong to the Library page only: fade them out as it
                        // slides away and back in as it returns.
                        float alpha =
                                position == PAGE_LIBRARY
                                        ? 1f - positionOffset
                                        : 0f;

                        if (
                                alpha > 0f
                                        && addBookButton.getVisibility() != View.VISIBLE
                        ) {

                            addBookButton.setVisibility(View.VISIBLE);

                            viewModeButton.setVisibility(View.VISIBLE);
                        }

                        addBookButton.setAlpha(alpha);

                        viewModeButton.setAlpha(alpha);
                    }
                }
        );
    }


    private void moveInto(
            FrameLayout container,
            View child
    ) {

        ViewGroup parent =
                (ViewGroup) child.getParent();

        if (parent != null) {

            parent.removeView(child);
        }

        container.addView(
                child,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );
    }


    /** Hands the already-built page views to the ViewPager2, one per position. */
    private static class PagesAdapter
            extends RecyclerView.Adapter<PagesAdapter.PageHolder> {

        private final View[] pages;

        PagesAdapter(
                View[] pages
        ) {

            this.pages = pages;
        }

        @Override
        public int getItemViewType(
                int position
        ) {

            // One view type per page, so each page is only ever created once.
            return position;
        }

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(
                @NonNull ViewGroup parent,
                int viewType
        ) {

            return new PageHolder(
                    pages[viewType]
            );
        }

        @Override
        public void onBindViewHolder(
                @NonNull PageHolder holder,
                int position
        ) {
        }

        @Override
        public int getItemCount() {

            return pages.length;
        }

        static class PageHolder
                extends RecyclerView.ViewHolder {

            PageHolder(
                    @NonNull View itemView
            ) {

                super(itemView);
            }
        }
    }


    private void loadBookmarks() {

        bookmarkRepository.getAllBookmarks(
                bookmarks -> {

                    if (!isAdded()) {
                        return;
                    }

                    requireActivity().runOnUiThread(
                            () -> {

                                if (
                                        bookmarks == null
                                                || bookmarks.isEmpty()
                                ) {

                                    bookmarksRecyclerView
                                            .setVisibility(
                                                    View.GONE
                                            );

                                    bookmarksEmptyText
                                            .setVisibility(
                                                    View.VISIBLE
                                            );

                                    bookmarkAdapter
                                            .setBookmarks(
                                                    null
                                            );

                                    return;
                                }

                                bookmarksRecyclerView
                                        .setVisibility(
                                                View.VISIBLE
                                        );

                                bookmarksEmptyText
                                        .setVisibility(
                                                View.GONE
                                        );

                                bookmarkAdapter
                                        .setBookmarks(
                                                bookmarks
                                        );
                            }
                    );
                }
        );
    }

    private void showFindMorePage() {

        viewModeButton.setVisibility(
                View.GONE
        );

        addBookButton.setVisibility(
                View.GONE
        );
    }

    private void openGoogleBooksPreview(String url) {
        if (url == null || url.isEmpty()) return;
        if (url.startsWith("http://")) {
            url = url.replaceFirst("http://", "https://");
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Could not open preview", Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadUnifiedBook(UnifiedBookItem book) {
        if (book == null || book.source != UnifiedBookItem.Source.GUTENBERG
                || book.gutendexBook == null) {
            if (book != null && book.previewUrl != null && !book.previewUrl.isEmpty()) {
                openGoogleBooksPreview(book.previewUrl);
            } else {
                Toast.makeText(requireContext(), "This book is not available for EPUB download.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String downloadUrl = book.downloadUrl;
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            downloadUrl = book.gutendexBook.getEpubDownloadUrl();
        }
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            Toast.makeText(requireContext(), "No EPUB download is listed for this book.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (downloadUrl.startsWith("http://")) {
            downloadUrl = downloadUrl.replaceFirst("http://", "https://");
        }

        String title = book.title != null && !book.title.trim().isEmpty()
                ? book.title : "Unknown book";
        String author = book.author != null && !book.author.trim().isEmpty()
                ? book.author : "Unknown author";
        String uniqueTitle = title + " - PG" + book.gutendexBook.id
                + "-" + System.currentTimeMillis();
        String fileName = createSafeBookFileName(uniqueTitle, ".epub");

        downloadGutendexEpub(downloadUrl, fileName, title, author);
    }

    private void searchGoogleBooks(String query) {
        final int requestId = ++searchRequestId;

        googleBooksProgress.setVisibility(View.VISIBLE);
        googleBooksEmptyText.setVisibility(View.GONE);

        List<GoogleBookItem> googleBooksList = new ArrayList<>();
        List<GutendexBook> gutendexBooksList = new ArrayList<>();

        final boolean[] googleFinished = {false};
        final boolean[] gutendexFinished = {false};

        Runnable checkComplete = () -> {
            if (requestId != searchRequestId || !googleFinished[0] || !gutendexFinished[0]) {
                return;
            }
            if (!isAdded()) {
                return;
            }
            googleBooksProgress.setVisibility(View.GONE);
            processCombinedResults(googleBooksList, gutendexBooksList);
        };

        // Google Books supplies broad discovery results and preview links.
        fetchGoogleBooksPages(query, 0, 40, googleBooksList, requestId, () -> {
            googleFinished[0] = true;
            checkComplete.run();
        });

        // Gutendex supplies EPUB links for records explicitly marked public domain in the US.
        fetchGutendexPages(query, 1, 3, gutendexBooksList, requestId, () -> {
            gutendexFinished[0] = true;
            checkComplete.run();
        });
    }

    private void fetchGoogleBooksPages(
            String query,
            int startIndex,
            int maxResults,
            List<GoogleBookItem> accumulated,
            int requestId,
            Runnable onComplete
    ) {
        if (!isAdded() || requestId != searchRequestId) {
            onComplete.run();
            return;
        }

        GoogleBooksClient.getApi()
                .searchBooks(
                        query,
                        BuildConfig.GOOGLE_BOOKS_API_KEY,
                        BuildConfig.APPLICATION_ID,
                        BuildConfig.GOOGLE_BOOKS_CERT,
                        maxResults,
                        startIndex
                )
                .enqueue(new Callback<GoogleBooksResponse>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<GoogleBooksResponse> call,
                            @NonNull Response<GoogleBooksResponse> response
                    ) {
                        if (!isAdded() || requestId != searchRequestId) {
                            onComplete.run();
                            return;
                        }

                        if (!response.isSuccessful() || response.body() == null) {
                            onComplete.run();
                            return;
                        }

                        GoogleBooksResponse body = response.body();
                        if (body.items != null) {
                            for (GoogleBookItem item : body.items) {
                                if (item != null) {
                                    accumulated.add(item);
                                }
                            }
                        }

                        int nextIndex = startIndex + maxResults;
                        if (nextIndex < body.totalItems
                                && nextIndex < 80
                                && body.items != null
                                && !body.items.isEmpty()) {
                            fetchGoogleBooksPages(
                                    query, nextIndex, maxResults, accumulated, requestId, onComplete
                            );
                        } else {
                            onComplete.run();
                        }
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<GoogleBooksResponse> call,
                            @NonNull Throwable t
                    ) {
                        onComplete.run();
                    }
                });
    }

    private void fetchGutendexPages(
            String query,
            int page,
            int maxPages,
            List<GutendexBook> accumulated,
            int requestId,
            Runnable onComplete
    ) {
        if (!isAdded() || requestId != searchRequestId) {
            onComplete.run();
            return;
        }

        GutendexClient.getApi()
                .searchBooks(query, "false", "application/epub+zip", page)
                .enqueue(new Callback<GutendexResponse>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<GutendexResponse> call,
                            @NonNull Response<GutendexResponse> response
                    ) {
                        if (!isAdded() || requestId != searchRequestId) {
                            onComplete.run();
                            return;
                        }

                        if (!response.isSuccessful() || response.body() == null) {
                            onComplete.run();
                            return;
                        }

                        GutendexResponse body = response.body();
                        if (body.results != null) {
                            accumulated.addAll(body.results);
                        }

                        if (body.next != null
                                && page < maxPages
                                && body.results != null
                                && !body.results.isEmpty()) {
                            fetchGutendexPages(
                                    query, page + 1, maxPages, accumulated, requestId, onComplete
                            );
                        } else {
                            onComplete.run();
                        }
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<GutendexResponse> call,
                            @NonNull Throwable t
                    ) {
                        onComplete.run();
                    }
                });
    }

    private void processCombinedResults(
            List<GoogleBookItem> googleItems,
            List<GutendexBook> gutendexItems
    ) {
        List<UnifiedBookItem> unifiedList = new ArrayList<>();

        // Add Gutenberg editions first because these have the actual EPUB files.
        if (gutendexItems != null) {
            for (GutendexBook gBook : gutendexItems) {
                if (gBook == null || !Boolean.FALSE.equals(gBook.copyright)) {
                    continue;
                }

                String epubUrl = gBook.getEpubDownloadUrl();
                if (epubUrl == null || epubUrl.trim().isEmpty()) {
                    continue;
                }

                UnifiedBookItem item = new UnifiedBookItem();
                item.source = UnifiedBookItem.Source.GUTENBERG;
                item.title = gBook.title != null ? gBook.title : "Unknown title";

                String authorStr = "Unknown author";
                if (gBook.authors != null && !gBook.authors.isEmpty()) {
                    List<String> names = new ArrayList<>();
                    for (GutendexBook.Author author : gBook.authors) {
                        if (author != null && author.name != null && !author.name.trim().isEmpty()) {
                            names.add(author.name);
                        }
                    }
                    if (!names.isEmpty()) {
                        authorStr = String.join(", ", names);
                    }
                }

                item.author = authorStr;
                item.publishedDate = "";
                item.coverUrl = gBook.getCoverUrl();
                item.isDownloadable = true;
                item.downloadUrl = epubUrl;
                item.gutendexBook = gBook;
                unifiedList.add(item);
            }
        }

        // Google Books remains a discovery and preview source. When the same
        // edition exists in Gutenberg, merge its metadata into that entry.
        if (googleItems != null) {
            for (GoogleBookItem gbItem : googleItems) {
                if (gbItem == null || gbItem.volumeInfo == null) {
                    continue;
                }

                String title = gbItem.volumeInfo.title != null
                        ? gbItem.volumeInfo.title : "Unknown title";

                String author = "Unknown author";
                if (gbItem.volumeInfo.authors != null && !gbItem.volumeInfo.authors.isEmpty()) {
                    author = String.join(", ", gbItem.volumeInfo.authors);
                }

                String coverUrl = null;
                if (gbItem.volumeInfo.imageLinks != null) {
                    coverUrl = gbItem.volumeInfo.imageLinks.thumbnail != null
                            ? gbItem.volumeInfo.imageLinks.thumbnail
                            : gbItem.volumeInfo.imageLinks.smallThumbnail;
                }

                String previewUrl = null;
                if (gbItem.accessInfo != null) {
                    previewUrl = gbItem.accessInfo.webReaderLink;
                }
                if ((previewUrl == null || previewUrl.isEmpty())
                        && gbItem.volumeInfo.previewLink != null) {
                    previewUrl = gbItem.volumeInfo.previewLink;
                }

                UnifiedBookItem matchingGutenberg = findMatchingGutendexBook(
                        unifiedList, title, author
                );

                if (matchingGutenberg != null) {
                    if (matchingGutenberg.coverUrl == null || matchingGutenberg.coverUrl.isEmpty()) {
                        matchingGutenberg.coverUrl = coverUrl;
                    }
                    if (matchingGutenberg.publishedDate == null
                            || matchingGutenberg.publishedDate.isEmpty()) {
                        matchingGutenberg.publishedDate = gbItem.volumeInfo.publishedDate;
                    }
                    if (matchingGutenberg.previewUrl == null
                            || matchingGutenberg.previewUrl.isEmpty()) {
                        matchingGutenberg.previewUrl = previewUrl;
                    }
                    matchingGutenberg.googleBookItem = gbItem;
                    continue;
                }

                UnifiedBookItem item = new UnifiedBookItem();
                item.source = UnifiedBookItem.Source.GOOGLE_BOOKS;
                item.title = title;
                item.author = author;
                item.publishedDate = gbItem.volumeInfo.publishedDate != null
                        ? gbItem.volumeInfo.publishedDate : "";
                item.coverUrl = coverUrl;
                item.isDownloadable = false;
                item.downloadUrl = null;
                item.previewUrl = previewUrl;
                item.googleBookItem = gbItem;
                unifiedList.add(item);
            }
        }

        googleBooksAdapter.setBooks(unifiedList);

        if (unifiedList.isEmpty()) {
            googleBooksEmptyText.setVisibility(View.VISIBLE);
            if (googleBooksEmptyText instanceof android.widget.TextView) {
                ((android.widget.TextView) googleBooksEmptyText)
                        .setText("No books or previews found");
            }
        } else {
            googleBooksEmptyText.setVisibility(View.GONE);
        }
    }

    private UnifiedBookItem findMatchingGutendexBook(
            List<UnifiedBookItem> books,
            String title,
            String author
    ) {
        String titleKey = normalizeTitleForMatch(title);
        if (titleKey.isEmpty()) {
            return null;
        }

        String authorKey = normalizeAuthorForMatch(author);
        for (UnifiedBookItem candidate : books) {
            if (candidate.source != UnifiedBookItem.Source.GUTENBERG) {
                continue;
            }
            if (!titleKey.equals(normalizeTitleForMatch(candidate.title))) {
                continue;
            }

            String candidateAuthorKey = normalizeAuthorForMatch(candidate.author);
            if (authorKey.isEmpty() || candidateAuthorKey.isEmpty()
                    || authorKey.equals(candidateAuthorKey)) {
                return candidate;
            }
        }
        return null;
    }

    private String normalizeTitleForMatch(String value) {
        return value == null ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private String normalizeAuthorForMatch(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();

        if (normalized.isEmpty() || "unknown author".equals(normalized)) {
            return "";
        }

        String[] parts = normalized.split("\\s+");
        java.util.Arrays.sort(parts);
        return String.join(" ", parts);
    }

    private void downloadGutendexEpub(
            String downloadUrl,
            String fileName,
            String title,
            String author
    ) {
        if (!isAdded()) {
            return;
        }

        Context appContext = requireContext().getApplicationContext();
        BookIngestor ingestor = bookIngestor;
        if (ingestor == null) {
            Toast.makeText(requireContext(), "The library importer is not ready.", Toast.LENGTH_SHORT).show();
            return;
        }

        googleBooksProgress.setVisibility(View.VISIBLE);

        GutendexClient.getApi()
                .downloadEpub(downloadUrl)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<ResponseBody> call,
                            @NonNull Response<ResponseBody> response
                    ) {
                        if (!isAdded()) {
                            if (response.body() != null) {
                                response.body().close();
                            }
                            return;
                        }

                        if (!response.isSuccessful() || response.body() == null) {
                            if (response.errorBody() != null) {
                                response.errorBody().close();
                            }
                            postDownloadMessage(
                                    "EPUB download failed (HTTP " + response.code() + ")."
                            );
                            return;
                        }

                        ResponseBody body = response.body();
                        try {
                            downloadExecutor.execute(
                                    () -> saveDownloadedEpub(
                                            appContext, body, fileName, title, author, ingestor
                                    )
                            );
                        } catch (java.util.concurrent.RejectedExecutionException e) {
                            body.close();
                            postDownloadMessage("The download could not be started.");
                        }
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<ResponseBody> call,
                            @NonNull Throwable t
                    ) {
                        if (!isAdded()) {
                            return;
                        }
                        Log.e("GutendexDownload", "EPUB download request failed", t);
                        postDownloadMessage(
                                "Could not download this EPUB. Check your connection and try again."
                        );
                    }
                });
    }

    private void saveDownloadedEpub(
            Context appContext,
            ResponseBody responseBody,
            String fileName,
            String title,
            String author,
            BookIngestor ingestor
    ) {
        File tempFile = new File(
                appContext.getCacheDir(),
                "gutendex_epub_" + System.currentTimeMillis() + ".epub"
        );

        try (
                ResponseBody body = responseBody;
                InputStream inputStream = body.byteStream();
                FileOutputStream outputStream = new FileOutputStream(tempFile)
        ) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            Log.e("GutendexDownload", "Could not save downloaded EPUB", e);
            if (tempFile.exists()) {
                tempFile.delete();
            }
            postDownloadMessage("The EPUB could not be saved.");
            return;
        }

        if (!isValidEpub(tempFile)) {
            Log.e("GutendexDownload", "The server response is not a valid EPUB file.");
            if (tempFile.exists()) {
                tempFile.delete();
            }
            postDownloadMessage(
                    "The server did not return a valid EPUB. Try another result."
            );
            return;
        }

        try {
            FileInputStream importStream = new FileInputStream(tempFile) {
                @Override
                public void close() throws java.io.IOException {
                    try {
                        super.close();
                    } finally {
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                    }
                }
            };

            ingestor.importDownloadedBook(
                    importStream,
                    fileName,
                    "epub",
                    title,
                    author
            );

            postDownloadMessage(
                    "EPUB downloaded. Adding it to your library."
            );
        } catch (java.io.IOException e) {
            Log.e("GutendexDownload", "Could not pass EPUB to the library importer", e);
            if (tempFile.exists()) {
                tempFile.delete();
            }
            postDownloadMessage("The EPUB could not be imported.");
        }
    }

    private boolean isValidEpub(File file) {
        if (file == null || !file.isFile() || file.length() < 100) {
            return false;
        }

        try (ZipFile zipFile = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            if (!entries.hasMoreElements()) {
                return false;
            }

            ZipEntry firstEntry = entries.nextElement();
            if (!"mimetype".equals(firstEntry.getName())
                    || firstEntry.getMethod() != ZipEntry.STORED) {
                return false;
            }

            ZipEntry mimetypeEntry = zipFile.getEntry("mimetype");
            ZipEntry containerEntry = zipFile.getEntry("META-INF/container.xml");
            if (mimetypeEntry == null || containerEntry == null) {
                return false;
            }

            byte[] expected = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            byte[] actual = new byte[expected.length];

            try (InputStream mimetypeInput = zipFile.getInputStream(mimetypeEntry)) {
                int offset = 0;
                while (offset < actual.length) {
                    int read = mimetypeInput.read(actual, offset, actual.length - offset);
                    if (read < 0) {
                        return false;
                    }
                    offset += read;
                }
                if (mimetypeInput.read() != -1) {
                    return false;
                }
            }

            return java.util.Arrays.equals(expected, actual);
        } catch (java.io.IOException e) {
            Log.e("GutendexDownload", "EPUB ZIP validation failed", e);
            return false;
        }
    }

    private void postDownloadMessage(String message) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (!isAdded()) {
                return;
            }
            if (getView() != null && googleBooksProgress != null) {
                googleBooksProgress.setVisibility(View.GONE);
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        });
    }

    private String createSafeBookFileName(
            String title,
            String extension
    ) {

        if (title == null || title.isEmpty()) {
            title = "book_" + System.currentTimeMillis();
        }

        // Replace invalid filename characters
        String safe =
                title.replaceAll(
                        "[\\\\/:*?\"<>|]",
                        "_"
                );

        return safe + extension;
    }


    // =========================================================
    // Import book
    // =========================================================

    private void importBook(Uri uri) {

        String fileName =
                getFileName(uri);


        if (fileName == null) {

            Toast.makeText(
                    requireContext(),
                    "Could not read file name",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        String format =
                getFormat(fileName);


        if (format == null) {

            Toast.makeText(
                    requireContext(),
                    "Unsupported book format",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        try {

            InputStream inputStream =
                    requireContext()
                            .getContentResolver()
                            .openInputStream(uri);


            if (inputStream == null) {

                Toast.makeText(
                        requireContext(),
                        "Could not open selected file",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }


            bookIngestor.importBook(
                    inputStream,
                    fileName,
                    format
            );


        } catch (Exception e) {

            e.printStackTrace();


            Toast.makeText(
                    requireContext(),
                    "Could not import book",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    // =========================================================
    // Get filename
    // =========================================================

    private String getFileName(
            Uri uri
    ) {

        String fileName =
                null;


        Cursor cursor =
                requireContext()
                        .getContentResolver()
                        .query(
                                uri,
                                null,
                                null,
                                null,
                                null
                        );


        if (cursor != null) {

            try {

                if (cursor.moveToFirst()) {

                    int nameIndex =
                            cursor.getColumnIndex(
                                    OpenableColumns
                                            .DISPLAY_NAME
                            );


                    if (nameIndex >= 0) {

                        fileName =
                                cursor.getString(
                                        nameIndex
                                );
                    }
                }


            } finally {

                cursor.close();
            }
        }


        return fileName;
    }


    // =========================================================
    // Get format
    // =========================================================

    private String getFormat(
            String fileName
    ) {

        String lowerName =
                fileName.toLowerCase(
                        Locale.ROOT
                );


        if (
                lowerName.endsWith(
                        ".epub"
                )
        ) {

            return "epub";
        }


        if (
                lowerName.endsWith(
                        ".pdf"
                )
        ) {

            return "pdf";
        }


        if (
                lowerName.endsWith(
                        ".txt"
                )
        ) {

            return "txt";
        }


        return null;
    }
}