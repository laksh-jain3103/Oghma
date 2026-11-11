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
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
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
import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.entity.Tag;
import com.reader.oghma.reader.HighlightDialog;
import com.reader.oghma.reader.ReaderFragment;
import com.reader.oghma.settings.SettingsFragment;
import com.reader.oghma.data.entity.Progress;
import com.reader.oghma.data.repository.ProgressRepository;
import com.reader.oghma.search.GoogleBooksAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private int sortMode = 0;

    private boolean sortDescending = false;

    private Integer selectedTagId = null;
    private final Map<Integer, List<Tag>> currentBookTagsMap = new HashMap<>();
    private List<com.reader.oghma.data.dao.TagWithCount> currentTagCounts = new ArrayList<>();
    private HorizontalScrollView tagFilterScrollView;
    private LinearLayout tagFilterContainer;


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


        viewModel =
                new ViewModelProvider(this)
                        .get(
                                LibraryViewModel.class
                        );

        viewModel.getRepository().schedulePendingTagWorkers(requireContext().getApplicationContext());


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

        tagFilterScrollView = view.findViewById(R.id.tagFilterScrollView);
        tagFilterContainer = view.findViewById(R.id.tagFilterContainer);

        viewModel.getRepository().observeAllBookTags().observe(getViewLifecycleOwner(), bookTagItems -> {
            currentBookTagsMap.clear();
            if (bookTagItems != null) {
                for (com.reader.oghma.data.dao.BookTagItem item : bookTagItems) {
                    List<Tag> list = currentBookTagsMap.computeIfAbsent(item.bookId, k -> new ArrayList<>());
                    Tag t = new Tag();
                    t.tagId = item.tagId;
                    t.name = item.name;
                    t.normalizedName = item.normalizedName;
                    list.add(t);
                }
            }
            if (adapter != null) {
                adapter.setBookTags(currentBookTagsMap);
            }
            applySearch();
        });

        viewModel.getRepository().observeTagsWithCounts().observe(getViewLifecycleOwner(), tagCounts -> {
            currentTagCounts = tagCounts != null ? tagCounts : new ArrayList<>();
            updateTagFilterRow();
        });


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
                "Edit tags",
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

                                showTagEditorDialog(
                                        book
                                );

                            } else if (which == 3) {

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

        // Load the saved reading progress and tags first, then show the dialog.
        new ProgressRepository(
                requireActivity().getApplication()
        ).get(
                book.bookId,
                progress -> {
                    viewModel.getRepository().getTagsForBook(
                            book.bookId,
                            tags -> {
                                if (!isAdded() || getActivity() == null) {
                                    return;
                                }

                                getActivity().runOnUiThread(
                                        () -> showBookDetailsDialog(
                                                book,
                                                progress,
                                                tags
                                        )
                                );
                            }
                    );
                }
        );
    }


    private void showBookDetailsDialog(
            Book book,
            Progress progress,
            List<Tag> tags
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


        String tagsText = "None";
        if (tags != null && !tags.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Tag t : tags) {
                if (t.name != null) names.add(t.name);
            }
            if (!names.isEmpty()) {
                tagsText = String.join(", ", names);
            }
        }


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
                        + "Tags: " + tagsText;


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

    private void showTagEditorDialog(Book book) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16));

        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout tagsListLayout = new LinearLayout(requireContext());
        tagsListLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(tagsListLayout);

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(200)
        );
        root.addView(scrollView, scrollParams);

        LinearLayout inputRow = new LinearLayout(requireContext());
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setPadding(0, dpToPx(12), 0, dpToPx(8));

        EditText inputTagBox = new EditText(requireContext());
        inputTagBox.setHint("Add tag...");
        inputTagBox.setMaxLines(1);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        inputRow.addView(inputTagBox, inputParams);

        Button addTagBtn = new Button(requireContext());
        addTagBtn.setText("Add");
        inputRow.addView(addTagBtn);
        root.addView(inputRow);

        Button refreshMetaBtn = new Button(requireContext());
        refreshMetaBtn.setText("Refresh metadata");
        root.addView(refreshMetaBtn);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Edit Tags: " + book.title)
                .setView(root)
                .setPositiveButton("Done", null)
                .create();

        androidx.lifecycle.LiveData<List<Tag>> tagsLiveData = viewModel.getRepository().observeTagsForBook(book.bookId);
        androidx.lifecycle.Observer<List<Tag>> observer = new androidx.lifecycle.Observer<List<Tag>>() {
            @Override
            public void onChanged(List<Tag> tags) {
                tagsListLayout.removeAllViews();
                if (tags != null) {
                    for (Tag tag : tags) {
                        LinearLayout row = new LinearLayout(requireContext());
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                        row.setPadding(0, dpToPx(4), 0, dpToPx(4));

                        TextView nameView = new TextView(requireContext());
                        nameView.setText(tag.name);
                        nameView.setTextSize(16);
                        nameView.setTextColor(getResources().getColor(R.color.text_primary));
                        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1.0f
                        );
                        row.addView(nameView, nameParams);

                        ImageButton removeBtn = new ImageButton(requireContext());
                        removeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                        removeBtn.setBackground(null);
                        removeBtn.setOnClickListener(v -> {
                            viewModel.getRepository().removeTag(book.bookId, tag.tagId);
                        });
                        row.addView(removeBtn);

                        tagsListLayout.addView(row);
                    }
                }
            }
        };
        tagsLiveData.observe(getViewLifecycleOwner(), observer);

        dialog.setOnDismissListener(d -> tagsLiveData.removeObserver(observer));

        addTagBtn.setOnClickListener(v -> {
            String raw = inputTagBox.getText().toString();
            String normalizedInput = com.reader.oghma.api.metadata.TagNormalizer.normalizeUserInput(raw);
            if (!normalizedInput.isEmpty()) {
                List<Tag> current = tagsLiveData.getValue();
                boolean exists = false;
                if (current != null) {
                    for (Tag t : current) {
                        if (t.name != null && t.name.equalsIgnoreCase(normalizedInput)) {
                            exists = true;
                            break;
                        }
                    }
                }
                if (!exists) {
                    viewModel.getRepository().addUserTag(book.bookId, normalizedInput);
                    inputTagBox.setText("");
                } else {
                    Toast.makeText(requireContext(), "Tag already exists", Toast.LENGTH_SHORT).show();
                }
            }
        });

        refreshMetaBtn.setOnClickListener(v -> {
            TagBookWorker.enqueue(requireContext(), book.bookId, true);
            Toast.makeText(requireContext(), "Refreshing metadata...", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
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

        if (selectedTagId != null) {
            List<Book> tagFiltered = new ArrayList<>();
            for (Book book : filteredBooks) {
                List<Tag> bookTags = currentBookTagsMap.get(book.bookId);
                boolean hasTag = false;
                if (bookTags != null) {
                    for (Tag t : bookTags) {
                        if (t.tagId == selectedTagId) {
                            hasTag = true;
                            break;
                        }
                    }
                }
                if (hasTag) {
                    tagFiltered.add(book);
                }
            }
            filteredBooks = tagFiltered;
        }

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

    private void updateTagFilterRow() {
        if (tagFilterScrollView == null || tagFilterContainer == null) return;
        tagFilterContainer.removeAllViews();
        if (currentTagCounts.isEmpty()) {
            tagFilterScrollView.setVisibility(View.GONE);
            if (selectedTagId != null) {
                selectedTagId = null;
                applySearch();
            }
            return;
        }
        tagFilterScrollView.setVisibility(View.VISIBLE);

        for (com.reader.oghma.data.dao.TagWithCount twc : currentTagCounts) {
            TextView chip = new TextView(requireContext());
            String label = twc.name + " (" + twc.bookCount + ")";
            chip.setText(label);
            boolean isSelected = (selectedTagId != null && selectedTagId == twc.tagId);
            chip.setTextColor(getResources().getColor(isSelected ? R.color.primary : R.color.text_secondary));
            chip.setTextSize(12);
            chip.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd(dpToPx(8));
            chip.setLayoutParams(params);

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(getResources().getColor(isSelected ? R.color.surface_variant : R.color.surface));
            bg.setStroke(dpToPx(1), getResources().getColor(isSelected ? R.color.primary : R.color.surface_variant));
            bg.setCornerRadius(dpToPx(16));
            chip.setBackground(bg);

            chip.setOnClickListener(v -> {
                if (selectedTagId != null && selectedTagId == twc.tagId) {
                    selectedTagId = null;
                } else {
                    selectedTagId = twc.tagId;
                }
                updateTagFilterRow();
                applySearch();
            });

            tagFilterContainer.addView(chip);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
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

        final List<String> googleCategories = book.volumeInfo.categories;

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

        downloadGoogleBookFromUrl(downloadUrl, fileName, format, title, author, book.id, book.volumeInfo.categories);
    }

    private void downloadGoogleBookFromUrl(
            String downloadUrl,
            String fileName,
            String format,
            String title,
            String author,
            String volumeId,
            List<String> googleCategories
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
        final List<String> finalCategories = googleCategories;

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

                                String fallbackUrl = null;
                                if (!finalDownloadUrl.contains("gutenberg.org")) {
                                    if ("Alice's Adventures in Wonderland".equalsIgnoreCase(finalTitle)) {
                                        fallbackUrl = "https://www.gutenberg.org/cache/epub/11/pg11-images.epub";
                                    } else if ("The War of the Worlds".equalsIgnoreCase(finalTitle) && finalAuthor.toLowerCase().contains("wells")) {
                                        fallbackUrl = "https://www.gutenberg.org/cache/epub/36/pg36-images.epub";
                                    } else if ("The Picture of Dorian Gray".equalsIgnoreCase(finalTitle) && finalAuthor.toLowerCase().contains("wilde")) {
                                        fallbackUrl = "https://www.gutenberg.org/cache/epub/26740/pg26740-images.epub";
                                    }
                                }

                                if (fallbackUrl != null) {
                                    Toast.makeText(requireContext(),
                                            "Google Books download unavailable. Downloading public-domain EPUB...",
                                            Toast.LENGTH_LONG).show();

                                    downloadGoogleBookFromUrl(
                                            fallbackUrl,
                                            fileName,
                                            finalFormat,
                                            finalTitle,
                                            finalAuthor,
                                            volumeId,
                                            finalCategories
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
                                    finalAuthor,
                                    finalCategories
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