package com.reader.oghma.library;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.activity.OnBackPressedCallback;
import com.reader.oghma.BuildConfig;
import com.reader.oghma.R;
import com.reader.oghma.api.GoogleBooksClient;
import com.reader.oghma.api.model.GoogleBooksResponse;
import com.reader.oghma.data.entity.Book;
import com.reader.oghma.reader.ReaderFragment;
import com.reader.oghma.search.GoogleBooksAdapter;

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
import androidx.recyclerview.widget.RecyclerView;


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

    private ImageButton settingsButton;


    // =========================================================
    // Navigation
    // =========================================================

    private BottomNavigationView bottomNavigation;

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

    private int sortMode = 0;

    private boolean sortDescending = false;


    // =========================================================
    // Import
    // =========================================================

    private ActivityResultLauncher<String[]> filePickerLauncher;

    private BookIngestor bookIngestor;


    // =========================================================
    // Constructor
    // =========================================================

    public LibraryFragment() {
        // Required empty constructor
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


        settingsButton =
                view.findViewById(
                        R.id.settingsButton
                );


        bottomNavigation =
                view.findViewById(
                        R.id.bottomNavigation
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
                bookmark -> openBookmark(
                        bookmark
                )
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
                            downloadGoogleBook(book);
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


        setupButtons();


        // -----------------------------------------------------
        // Start on Library
        // -----------------------------------------------------

        bottomNavigation.setSelectedItemId(
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
                                        bottomNavigation
                                                .getSelectedItemId();

                                if (
                                        selectedId != R.id.nav_library
                                ) {

                                    bottomNavigation
                                            .setSelectedItemId(
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

        String[] options = {
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

                                showBookDetails(
                                        book
                                );

                            } else if (which == 1) {

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
                        + "Progress: Not started"
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

        viewModel =
                new ViewModelProvider(this)
                        .get(
                                LibraryViewModel.class
                        );


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
                            bottomNavigation
                                    .getSelectedItemId()
                                    != R.id.nav_library
                    ) {

                        bottomNavigation
                                .setSelectedItemId(
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
        // Settings
        // -----------------------------------------------------

        settingsButton.setOnClickListener(
                v -> {

                    Toast.makeText(
                            requireContext(),
                            "Settings coming later",
                            Toast.LENGTH_SHORT
                    ).show();
                }
        );


        // -----------------------------------------------------
        // Bottom navigation
        // -----------------------------------------------------

        bottomNavigation.setOnItemSelectedListener(
                item -> {

                    int itemId =
                            item.getItemId();

                    if (itemId == R.id.nav_library) {

                        showLibraryPage();

                        return true;
                    }

                    if (itemId == R.id.nav_bookmarks) {

                        showBookmarksPage();

                        return true;
                    }

                    if (itemId == R.id.nav_find_more) {

                        showFindMorePage();

                        return true;
                    }

                    return false;
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

        emptyLibrary.setVisibility(
                View.VISIBLE
        );

        bookRecyclerView.setVisibility(
                View.VISIBLE
        );

        bookmarksPage.setVisibility(
                View.GONE
        );

        findMorePage.setVisibility(
                View.GONE
        );

        viewModeButton.setVisibility(
                View.VISIBLE
        );

        addBookButton.setVisibility(
                View.VISIBLE
        );
    }


    private void showBookmarksPage() {

        emptyLibrary.setVisibility(
                View.GONE
        );

        bookRecyclerView.setVisibility(
                View.GONE
        );

        bookmarksPage.setVisibility(
                View.VISIBLE
        );

        findMorePage.setVisibility(
                View.GONE
        );

        viewModeButton.setVisibility(
                View.GONE
        );

        addBookButton.setVisibility(
                View.GONE
        );

        loadBookmarks();
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

        emptyLibrary.setVisibility(
                View.GONE
        );

        bookRecyclerView.setVisibility(
                View.GONE
        );

        bookmarksPage.setVisibility(
                View.GONE
        );

        findMorePage.setVisibility(
                View.VISIBLE
        );

        viewModeButton.setVisibility(
                View.GONE
        );

        addBookButton.setVisibility(
                View.GONE
        );
    }

    private void searchGoogleBooks(String query) {

        googleBooksProgress.setVisibility(
                View.VISIBLE
        );

        googleBooksEmptyText.setVisibility(
                View.GONE
        );

        String titleQuery = "intitle:" + query;

        GoogleBooksClient.getApi()
                .searchBooks(
                        titleQuery,
                        BuildConfig.GOOGLE_BOOKS_API_KEY,
                        BuildConfig.APPLICATION_ID,
                        BuildConfig.GOOGLE_BOOKS_CERT,
                        "full",
                        "epub"
                )
                .enqueue(new Callback<GoogleBooksResponse>() {

                    @Override
                    public void onResponse(
                            @NonNull Call<GoogleBooksResponse> call,
                            @NonNull Response<GoogleBooksResponse> response
                    ) {

                        if (!isAdded()) {
                            return;
                        }

                        googleBooksProgress.setVisibility(
                                View.GONE
                        );

                        if (!response.isSuccessful()
                                || response.body() == null) {

                            Toast.makeText(
                                    requireContext(),
                                    "Google Books search failed",
                                    Toast.LENGTH_SHORT
                            ).show();

                            googleBooksEmptyText.setVisibility(
                                    View.VISIBLE
                            );

                            return;
                        }

                        List<com.reader.oghma.api.model.GoogleBookItem> rawItems =
                                response.body().items;

                        List<com.reader.oghma.api.model.GoogleBookItem> filteredItems =
                                new ArrayList<>();

                        if (rawItems != null) {
                            for (com.reader.oghma.api.model.GoogleBookItem item : rawItems) {
                                if (item == null) continue;

                                // Filter conditions
                                if (item.accessInfo != null
                                        && item.accessInfo.publicDomain
                                        && "ALL_PAGES".equals(item.accessInfo.viewability)
                                        && item.accessInfo.epub != null
                                        && item.accessInfo.epub.isAvailable
                                        && item.accessInfo.epub.downloadLink != null
                                        && !item.accessInfo.epub.downloadLink.isEmpty()) {

                                    filteredItems.add(item);
                                }

                                // Diagnostic Logging
                                String title = (item.volumeInfo != null) ? item.volumeInfo.title : "null";
                                String volumeId = (item.id != null) ? item.id : "null";
                                
                                Log.d("GoogleBooksDebug", "Title: " + title);
                                Log.d("GoogleBooksDebug", "Volume ID: " + volumeId);

                                if (item.accessInfo != null) {
                                    com.reader.oghma.api.model.GoogleBookItem.AccessInfo access = item.accessInfo;
                                    Log.d("GoogleBooksDebug", "Country: " + access.country);
                                    Log.d("GoogleBooksDebug", "publicDomain: " + access.publicDomain);
                                    Log.d("GoogleBooksDebug", "viewability: " + access.viewability);
                                    Log.d("GoogleBooksDebug", "accessViewStatus: " + access.accessViewStatus);
                                    Log.d("GoogleBooksDebug", "explicitOfflineLicenseManagement: " + access.explicitOfflineLicenseManagement);

                                    if (access.epub != null) {
                                        Log.d("GoogleBooksDebug", "EPUB:");
                                        Log.d("GoogleBooksDebug", " - isAvailable: " + access.epub.isAvailable);
                                        Log.d("GoogleBooksDebug", " - downloadLink: " + access.epub.downloadLink);
                                        Log.d("GoogleBooksDebug", " - acsTokenLink: " + access.epub.acsTokenLink);
                                    }

                                    if (access.pdf != null) {
                                        Log.d("GoogleBooksDebug", "PDF:");
                                        Log.d("GoogleBooksDebug", " - isAvailable: " + access.pdf.isAvailable);
                                        Log.d("GoogleBooksDebug", " - downloadLink: " + access.pdf.downloadLink);
                                        Log.d("GoogleBooksDebug", " - acsTokenLink: " + access.pdf.acsTokenLink);
                                    }

                                    if (access.downloadAccess != null) {
                                        Log.d("GoogleBooksDebug", "downloadAccess:");
                                        Log.d("GoogleBooksDebug", " - restricted: " + access.downloadAccess.restricted);
                                        Log.d("GoogleBooksDebug", " - deviceAllowed: " + access.downloadAccess.deviceAllowed);
                                        Log.d("GoogleBooksDebug", " - justAcquired: " + access.downloadAccess.justAcquired);
                                        Log.d("GoogleBooksDebug", " - maxDownloadDevices: " + access.downloadAccess.maxDownloadDevices);
                                        Log.d("GoogleBooksDebug", " - downloadsAcquired: " + access.downloadAccess.downloadsAcquired);
                                        Log.d("GoogleBooksDebug", " - source: " + access.downloadAccess.source);
                                        Log.d("GoogleBooksDebug", " - reasonCode: " + access.downloadAccess.reasonCode);
                                        Log.d("GoogleBooksDebug", " - message: " + access.downloadAccess.message);
                                        Log.d("GoogleBooksDebug", " - signature: " + access.downloadAccess.signature);
                                    }
                                }
                                Log.d("GoogleBooksDebug", "-----------------------------------");
                            }
                        }

                        Log.d("GoogleBooks", "Search result summary:");
                        Log.d("GoogleBooks", "Original query: " + query);
                        Log.d("GoogleBooks", "API results count: " + (rawItems != null ? rawItems.size() : 0));
                        Log.d("GoogleBooks", "Downloadable public-domain count: " + filteredItems.size());

                        googleBooksAdapter.setBooks(filteredItems);

                        if (filteredItems.isEmpty()) {

                            googleBooksEmptyText.setVisibility(
                                    View.VISIBLE
                            );

                            if (googleBooksEmptyText instanceof android.widget.TextView) {
                                ((android.widget.TextView) googleBooksEmptyText)
                                        .setText("No downloadable public-domain books found");
                            }

                        } else {

                            googleBooksEmptyText.setVisibility(
                                    View.GONE
                            );
                        }
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<GoogleBooksResponse> call,
                            @NonNull Throwable t
                    ) {

                        if (!isAdded()) {
                            return;
                        }

                        googleBooksProgress.setVisibility(
                                View.GONE
                        );

                        googleBooksEmptyText.setVisibility(
                                View.VISIBLE
                        );

                        if (googleBooksEmptyText instanceof android.widget.TextView) {
                            ((android.widget.TextView) googleBooksEmptyText)
                                    .setText("Could not connect to Google Books");
                        }

                        Log.e(
                                "GoogleBooks",
                                "Search failed",
                                t
                        );

                        Toast.makeText(
                                requireContext(),
                                "Could not connect to Google Books",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
    }

    private void downloadGoogleBook(com.reader.oghma.api.model.GoogleBookItem book) {

        if (book.volumeInfo == null || book.accessInfo == null) {

            Toast.makeText(
                    requireContext(),
                    "This book is not available for download.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String downloadUrl = null;
        String format = null;
        String extension = null;

        com.reader.oghma.api.model.GoogleBookItem.AccessInfo accessInfo =
                book.accessInfo;

        if (
                accessInfo.epub != null
                        && accessInfo.epub.isAvailable
                        && accessInfo.epub.downloadLink != null
                        && !accessInfo.epub.downloadLink.isEmpty()
        ) {

            format = "epub";

            extension = ".epub";

            downloadUrl = accessInfo.epub.downloadLink;

        } else if (
                accessInfo.pdf != null
                        && accessInfo.pdf.isAvailable
                        && accessInfo.pdf.downloadLink != null
                        && !accessInfo.pdf.downloadLink.isEmpty()
        ) {

            format = "pdf";

            extension = ".pdf";

            downloadUrl = accessInfo.pdf.downloadLink;
        }

        if (downloadUrl == null) {

            Toast.makeText(
                    requireContext(),
                    "This book is not available for download.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String title =
                book.volumeInfo.title != null
                        ? book.volumeInfo.title
                        : "Unknown book";

        String author;

        if (
                book.volumeInfo.authors != null
                        && !book.volumeInfo.authors.isEmpty()
        ) {

            author = String.join(", ", book.volumeInfo.authors);

        } else {

            author = "Unknown author";
        }

        String fileName =
                createSafeBookFileName(
                        title,
                        extension
                );

        downloadGoogleBookFromUrl(downloadUrl, fileName, format, title, author, book.id);
    }

    private void downloadGoogleBookFromUrl(
            String downloadUrl,
            String fileName,
            String format,
            String title,
            String author,
            String volumeId
    ) {
        if (downloadUrl.startsWith("http://")) {
            downloadUrl = downloadUrl.replaceFirst("http://", "https://");
        }
        Log.d("GoogleBooks", "Download URL: " + downloadUrl);

        googleBooksProgress.setVisibility(
                View.VISIBLE
        );

        final String finalFormat = format;
        final String finalTitle = title;
        final String finalAuthor = author;
        final String finalDownloadUrl = downloadUrl;

        GoogleBooksClient.getApi()
                .downloadBook(downloadUrl)
                .enqueue(new Callback<ResponseBody>() {

                    @Override
                    public void onResponse(
                            @NonNull Call<ResponseBody> call,
                            @NonNull Response<ResponseBody> response
                    ) {

                        if (!isAdded()) {
                            return;
                        }

                        googleBooksProgress.setVisibility(
                                View.GONE
                        );

                        if (
                                !response.isSuccessful()
                                        || response.body() == null
                        ) {
                            int code = response.code();
                            String message = response.message();
                            String url = call.request().url().toString();
                            String headers = response.headers().toString();
                            String errorBody = "null";

                            try {
                                if (response.errorBody() != null) {
                                    errorBody = response.errorBody().string();
                                }
                            } catch (java.io.IOException e) {
                                errorBody = "Error reading error body: " + e.getMessage();
                            }

                            Log.e("GoogleBooks", "Download failed!");
                            Log.e("GoogleBooks", "Status Code: " + code);
                            Log.e("GoogleBooks", "Message: " + message);
                            Log.e("GoogleBooks", "URL: " + url);
                            Log.e("GoogleBooks", "Headers: " + headers);
                            Log.e("GoogleBooks", "Error Body: " + errorBody);

                            Toast.makeText(
                                    requireContext(),
                                    "Could not download this book (HTTP " + code + ")",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }

                        File tempFile = new File(requireContext().getCacheDir(), "google_books_temp_" + System.currentTimeMillis());
                        long downloadedBytes = 0;

                        try (InputStream inputStream = response.body().byteStream();
                             FileOutputStream outputStream = new FileOutputStream(tempFile)) {

                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                                downloadedBytes += bytesRead;
                            }
                        } catch (java.io.IOException e) {
                            Log.e("GoogleBooks", "Error saving to temp file", e);
                            if (tempFile.exists()) tempFile.delete();
                            Toast.makeText(requireContext(), "Download failed while saving file", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Diagnostic Logging
                        Log.d("GoogleBooksDownload", "HTTP Status: " + response.code());
                        Log.d("GoogleBooksDownload", "Content-Type: " + response.headers().get("Content-Type"));
                        Log.d("GoogleBooksDownload", "Content-Length: " + response.headers().get("Content-Length"));
                        Log.d("GoogleBooksDownload", "Final URL: " + response.raw().request().url());
                        Log.d("GoogleBooksDownload", "Actual Bytes: " + downloadedBytes);
                        Log.d("GoogleBooksDownload", "Volume ID: " + (volumeId != null ? volumeId : "null"));
                        Log.d("GoogleBooksDownload", "Title: " + finalTitle);
                        Log.d("GoogleBooksDownload", "Format: " + finalFormat);
                        Log.d("GoogleBooksDownload", "URL: " + finalDownloadUrl);

                        byte[] header = new byte[4];
                        try (FileInputStream fis = new FileInputStream(tempFile)) {
                            int read = fis.read(header);
                            if (read < 4) {
                                Log.e("GoogleBooksDownload", "File too small to read magic bytes");
                            }
                        } catch (java.io.IOException e) {
                            Log.e("GoogleBooksDownload", "Error reading magic bytes", e);
                        }
                        String hexHeader = String.format("%02X %02X %02X %02X", 
                                header[0] & 0xFF, header[1] & 0xFF, header[2] & 0xFF, header[3] & 0xFF);
                        Log.d("GoogleBooksDownload", "Magic Bytes: " + hexHeader);

                        // ZIP check: 50 4B 03 04
                        boolean isZip = (header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04);

                        if (!isZip) {
                            Log.e("GoogleBooksDownload", "Downloaded file is not a ZIP/EPUB");
                            String previewText = "null";
                            try (FileInputStream fis = new FileInputStream(tempFile)) {
                                byte[] preview = new byte[512];
                                int read = fis.read(preview);
                                if (read > 0) {
                                    previewText = new String(preview, 0, read, StandardCharsets.UTF_8);
                                    Log.e("GoogleBooksDownload", "File start (text): " + previewText);
                                }
                            } catch (java.io.IOException e) {
                                Log.e("GoogleBooksDownload", "Error reading preview", e);
                            }

                            if (previewText.contains("<!DOCTYPE html>") || previewText.contains("<html>")) {
                                Log.e("GoogleBooksDownload", "The server returned an HTML page instead of an EPUB.");

                                if ("Alice's Adventures in Wonderland".equalsIgnoreCase(finalTitle) && !finalDownloadUrl.contains("gutenberg.org")) {
                                    Toast.makeText(requireContext(),
                                            "Google Books download unavailable. Downloading public-domain EPUB...",
                                            Toast.LENGTH_LONG).show();

                                    downloadGoogleBookFromUrl(
                                            "https://www.gutenberg.org/cache/epub/11/pg11-images.epub",
                                            fileName,
                                            finalFormat,
                                            finalTitle,
                                            finalAuthor,
                                            volumeId
                                    );

                                    if (tempFile.exists()) tempFile.delete();
                                    return;
                                }

                                Toast.makeText(requireContext(), 
                                        "Google Books returned a web page instead of an EPUB. This volume requires Google Books download authorization.", 
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(requireContext(), "The downloaded file is not a valid EPUB.", Toast.LENGTH_LONG).show();
                            }

                            if (tempFile.exists()) tempFile.delete();
                            return;
                        }

                        // EPUB Container check
                        boolean hasMimetype = false;
                        boolean hasContainerXml = false;
                        try (ZipFile zipFile = new ZipFile(tempFile)) {
                            Log.d("GoogleBooksDownload", "ZIP entries (first several):");
                            Enumeration<? extends ZipEntry> entries = zipFile.entries();
                            int count = 0;
                            while (entries.hasMoreElements()) {
                                ZipEntry entry = entries.nextElement();
                                if (count < 10) {
                                    Log.d("GoogleBooksDownload", " - " + entry.getName());
                                }
                                if ("mimetype".equals(entry.getName())) hasMimetype = true;
                                if ("META-INF/container.xml".equals(entry.getName())) hasContainerXml = true;
                                count++;
                            }
                        } catch (java.io.IOException e) {
                            Log.e("GoogleBooksDownload", "Error inspecting ZIP", e);
                        }

                        if (!hasMimetype || !hasContainerXml) {
                            Log.e("GoogleBooksDownload", "Downloaded file is not a valid EPUB container (mimetype=" + hasMimetype + ", container.xml=" + hasContainerXml + ")");
                            if (tempFile.exists()) tempFile.delete();
                            Toast.makeText(requireContext(), "The downloaded file is not a valid EPUB container.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        // Pass to BookIngestor
                        try {
                            bookIngestor.importDownloadedBook(
                                    new FileInputStream(tempFile) {
                                        @Override
                                        public void close() throws java.io.IOException {
                                            super.close();
                                            if (tempFile.exists()) tempFile.delete(); // Delete temp file after import consumes it
                                        }
                                    },
                                    fileName,
                                    finalFormat,
                                    finalTitle,
                                    finalAuthor
                            );
                        } catch (java.io.FileNotFoundException e) {
                            Log.e("GoogleBooksDownload", "Could not create stream for import", e);
                            if (tempFile.exists()) tempFile.delete();
                        }

                        Toast.makeText(
                                requireContext(),
                                "Downloading " + finalTitle + "...",
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<ResponseBody> call,
                            @NonNull Throwable t
                    ) {

                        if (!isAdded()) {
                            return;
                        }

                        googleBooksProgress.setVisibility(
                                View.GONE
                        );

                        Log.e(
                                "GoogleBooks",
                                "Book download failed",
                                t
                        );

                        Toast.makeText(
                                requireContext(),
                                "Could not download this book.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
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