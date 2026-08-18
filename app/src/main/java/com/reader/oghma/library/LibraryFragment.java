package com.reader.oghma.library;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
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
import com.reader.oghma.R;
import com.reader.oghma.data.entity.Book;
import com.reader.oghma.reader.ReaderFragment;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import androidx.recyclerview.widget.RecyclerView;


import com.reader.oghma.data.entity.Bookmark;
import com.reader.oghma.data.repository.BookmarkRepository;

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