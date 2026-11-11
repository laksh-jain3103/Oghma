package com.reader.oghma.library;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
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
import com.reader.oghma.api.model.GoogleBookItem;
import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.entity.Tag;
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
import com.reader.oghma.api.ProjectGutenbergClient;
import com.reader.oghma.api.model.ProjectGutenbergBook;
import com.reader.oghma.api.model.ProjectGutenbergParser;
import android.content.Intent;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import com.reader.oghma.data.entity.Bookmark;
import com.reader.oghma.data.repository.BookmarkRepository;
import com.reader.oghma.util.AuthorUtils;
import com.reader.oghma.util.TagChipHelper;

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

    private Integer selectedTagId = null;
    private final Map<Integer, List<Tag>> currentBookTagsMap = new HashMap<>();
    private List<com.reader.oghma.data.dao.TagWithCount> currentTagCounts = new ArrayList<>();
    private HorizontalScrollView tagFilterScrollView;
    private LinearLayout tagFilterContainer;
    private TextView findMoreTagPrompt;
    private String searchQueryLibrary = "";
    private String searchQueryBookmarks = "";
    private String searchQueryNotes = "";
    private final List<Book> allBookmarkedBooks = new ArrayList<>();
    private final List<Bookmark> allNotes = new ArrayList<>();


    // =========================================================
    // Import
    // =========================================================

    private ActivityResultLauncher<String[]> filePickerLauncher;

    private BookIngestor bookIngestor;

    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor();

    private final ExecutorService searchExecutor = Executors.newFixedThreadPool(2);

    private static final int MAX_GUTENDEX_RETRIES = 3;
    private static final int MAX_OPDS_SEARCH_RETRIES = 2;
    private static final int MAX_OPDS_DETAIL_RETRIES = 2;
    private static final int MAX_OPDS_DETAIL_BOOKS = 20;


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
        searchExecutor.shutdown();
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
                    allBookmarkedBooks.clear();
                    if (bookmarkedBooks != null) {
                        allBookmarkedBooks.addAll(bookmarkedBooks);
                    }
                    applyBookmarksSearch();
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

        adapter.setOnTagClickListener(tag -> {
            libraryPager.setCurrentItem(pageIndexForNav(R.id.nav_library), true);
            selectedTagId = tag.tagId;
            updateTagFilterRow();
            applySearch();
            if (tagFilterScrollView != null) tagFilterScrollView.smoothScrollTo(0, 0);
        });

        adapter.setOnTagLongClickListener(tag -> {
            libraryPager.setCurrentItem(pageIndexForNav(R.id.nav_find_more), true);
            googleBooksSearchInput.setText("subject:" + tag.name);
            searchGoogleBooks("subject:" + tag.name);
        });

        bookBookmarksAdapter.setOnTagClickListener(tag -> {
            libraryPager.setCurrentItem(pageIndexForNav(R.id.nav_library), true);
            selectedTagId = tag.tagId;
            updateTagFilterRow();
            applySearch();
            if (tagFilterScrollView != null) tagFilterScrollView.smoothScrollTo(0, 0);
        });

        bookBookmarksAdapter.setOnTagLongClickListener(tag -> {
            libraryPager.setCurrentItem(pageIndexForNav(R.id.nav_find_more), true);
            googleBooksSearchInput.setText("subject:" + tag.name);
            searchGoogleBooks("subject:" + tag.name);
        });

        googleBooksAdapter.setOnTagClickListener(tag -> {
            libraryPager.setCurrentItem(pageIndexForNav(R.id.nav_find_more), true);
            googleBooksSearchInput.setText("subject:" + tag);
            searchGoogleBooks("subject:" + tag);
        });


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
        android.content.Context context = requireContext();
        AlertDialog dialog = new AlertDialog.Builder(context).create();

        ScrollView scrollView = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));
        scrollView.addView(root);

        // 1. Title heading
        TextView titleView = new TextView(context);
        titleView.setText(book.title != null ? book.title : "Untitled");
        titleView.setTextSize(20);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setTextColor(getResources().getColor(R.color.text_primary));
        titleView.setMaxLines(2);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(titleView);

        // 2. Compact details line under title (Author · Format · Progress)
        TextView detailsView = new TextView(context);
        detailsView.setText("Loading details...");
        detailsView.setTextColor(getResources().getColor(R.color.text_secondary));
        detailsView.setTextSize(13);
        detailsView.setPadding(0, dpToPx(4), 0, dpToPx(12));
        root.addView(detailsView);

        // 3. Tags section
        LinearLayout tagsSection = new LinearLayout(context);
        tagsSection.setOrientation(LinearLayout.VERTICAL);
        tagsSection.setPadding(0, dpToPx(4), 0, dpToPx(12));

        LinearLayout tagsHeaderRow = new LinearLayout(context);
        tagsHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        tagsHeaderRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        tagsHeaderRow.setBackgroundResource(getSelectableItemBackground(context));
        tagsHeaderRow.setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6));
        tagsHeaderRow.setOnClickListener(v -> {
            dialog.dismiss();
            showTagEditorDialog(book);
        });

        TextView tagsLabel = new TextView(context);
        tagsLabel.setText("Tags");
        tagsLabel.setTextSize(14);
        tagsLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tagsLabel.setTextColor(getResources().getColor(R.color.text_primary));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tagsHeaderRow.addView(tagsLabel, labelParams);

        TextView editTagsAction = new TextView(context);
        editTagsAction.setText("Edit");
        editTagsAction.setTextSize(14);
        editTagsAction.setTextColor(getResources().getColor(R.color.primary));
        tagsHeaderRow.addView(editTagsAction);

        tagsSection.addView(tagsHeaderRow);

        com.google.android.material.chip.ChipGroup chipGroup = new com.google.android.material.chip.ChipGroup(context);
        chipGroup.setChipSpacing(TagChipHelper.dpToPx(context, 4));
        chipGroup.setPadding(0, TagChipHelper.dpToPx(context, 4), 0, 0);
        chipGroup.setMinimumHeight(TagChipHelper.dpToPx(context, 32));
        tagsSection.addView(chipGroup);

        root.addView(tagsSection);
        root.addView(createThinDivider(context));

        // 4. Action rows (Open book, Add/Remove Bookmark, Edit tags, Remove from library)
        TextView openBookRow = createActionRow(context, "Open book");
        openBookRow.setOnClickListener(v -> {
            dialog.dismiss();
            openBook(book);
        });
        root.addView(openBookRow);
        root.addView(createThinDivider(context));

        String bookmarkText = book.isBookmarked ? "Remove Bookmark" : "Add Bookmark";
        TextView bookmarkRow = createActionRow(context, bookmarkText);
        bookmarkRow.setOnClickListener(v -> {
            boolean newStatus = !book.isBookmarked;
            viewModel.getRepository().updateBookmarkStatus(book.bookId, newStatus);
            book.isBookmarked = newStatus;
            Toast.makeText(context, newStatus ? "Added to bookmarks" : "Removed from bookmarks", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        root.addView(bookmarkRow);
        root.addView(createThinDivider(context));

        TextView editTagsRow = createActionRow(context, "Edit tags");
        editTagsRow.setOnClickListener(v -> {
            dialog.dismiss();
            showTagEditorDialog(book);
        });
        root.addView(editTagsRow);
        root.addView(createThinDivider(context));

        TextView removeRow = createActionRow(context, "Remove from library");
        int errorColor = 0;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            errorColor = getThemeColor(context, android.R.attr.colorError);
        }
        if (errorColor == 0) {
            errorColor = getThemeColor(context, com.google.android.material.R.attr.colorError);
        }
        removeRow.setTextColor(errorColor != 0 ? errorColor : getResources().getColor(android.R.color.holo_red_dark));
        removeRow.setOnClickListener(v -> {
            dialog.dismiss();
            confirmRemoveBook(book);
        });
        root.addView(removeRow);

        dialog.setView(scrollView);
        dialog.show();

        // Load progress and tags off main thread
        new ProgressRepository(requireActivity().getApplication()).get(book.bookId, progress -> {
            viewModel.getRepository().getTagsForBook(book.bookId, tags -> {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    String cleanAuthor = AuthorUtils.cleanAuthor(book.author);
                    boolean hasAuthor = !AuthorUtils.isUnknownOrEmpty(cleanAuthor);
                    boolean hasFormat = book.format != null && !book.format.trim().isEmpty();
                    String progressText = progress == null ? "Not started" : Math.round(progress.percentage) + "% read";

                    List<String> parts = new ArrayList<>();
                    if (hasAuthor) parts.add(cleanAuthor);
                    if (hasFormat) parts.add(book.format.toUpperCase());
                    parts.add(progressText);

                    detailsView.setText(String.join(" · ", parts));

                    chipGroup.removeAllViews();
                    if (tags == null || tags.isEmpty()) {
                        android.widget.TextView addChip = TagChipHelper.createChip(context, "Add tags", 12f);
                        addChip.setOnClickListener(v -> {
                            dialog.dismiss();
                            showTagEditorDialog(book);
                        });
                        chipGroup.addView(addChip);
                    } else {
                        int maxShow = 8;
                        int total = tags.size();
                        int showCount = Math.min(total, maxShow);
                        for (int i = 0; i < showCount; i++) {
                            Tag t = tags.get(i);
                            android.widget.TextView chip = TagChipHelper.createChip(context, t.name, 12f);
                            chip.setMaxWidth(TagChipHelper.dpToPx(context, 140));
                            chip.setOnClickListener(v -> {
                                dialog.dismiss();
                                showTagEditorDialog(book);
                            });
                            chipGroup.addView(chip);
                        }
                        if (total > maxShow) {
                            int extra = total - maxShow;
                            android.widget.TextView plusChip = TagChipHelper.createChip(context, "+" + extra, 12f);
                            plusChip.setOnClickListener(v -> {
                                dialog.dismiss();
                                showTagEditorDialog(book);
                            });
                            chipGroup.addView(plusChip);
                        }
                    }
                });
            });
        });
    }

    private View createThinDivider(android.content.Context context) {
        View divider = new View(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
        divider.setLayoutParams(params);
        divider.setBackgroundColor(getResources().getColor(R.color.surface_variant));
        return divider;
    }

    private int getThemeColor(android.content.Context context, int attrResId) {
        android.util.TypedValue tv = new android.util.TypedValue();
        context.getTheme().resolveAttribute(attrResId, tv, true);
        return tv.data;
    }

    private int getSelectableItemBackground(android.content.Context context) {
        android.util.TypedValue out = new android.util.TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true);
        return out.resourceId;
    }

    private TextView createActionRow(android.content.Context context, String text) {
        TextView row = new TextView(context);
        row.setText(text);
        row.setTextSize(16);
        row.setTextColor(getResources().getColor(R.color.text_primary));
        row.setMinimumHeight(dpToPx(48));
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(12), 0, dpToPx(12), 0);
        row.setBackgroundResource(getSelectableItemBackground(context));
        return row;
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
                }
        );
    }


    // =========================================================
    // Search
    // =========================================================

    private void showSearchDialogForCurrentPage(int pageIndex) {
        String title = "Search library";
        String hint = "Search library...";
        String currentQuery = searchQueryLibrary;
        if (pageIndex == PAGE_BOOKMARKS) {
            title = "Search bookmarks";
            hint = "Search bookmarks...";
            currentQuery = searchQueryBookmarks;
        } else if (pageIndex == PAGE_NOTES) {
            title = "Search notes";
            hint = "Search notes...";
            currentQuery = searchQueryNotes;
        }

        final EditText searchInput = new EditText(requireContext());
        searchInput.setSingleLine(true);
        searchInput.setHint(hint);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        if (!currentQuery.isEmpty()) {
            searchInput.setText(currentQuery);
            searchInput.setSelection(currentQuery.length());
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(searchInput)
                .setPositiveButton("Search", (d, w) -> {
                    String q = searchInput.getText().toString().trim();
                    if (pageIndex == PAGE_LIBRARY) {
                        searchQueryLibrary = q;
                        applySearch();
                    } else if (pageIndex == PAGE_BOOKMARKS) {
                        searchQueryBookmarks = q;
                        applyBookmarksSearch();
                    } else if (pageIndex == PAGE_NOTES) {
                        searchQueryNotes = q;
                        applyNotesSearch();
                    }
                    updateSearchButtonTint();
                })
                .setNeutralButton("Clear", (d, w) -> {
                    if (pageIndex == PAGE_LIBRARY) {
                        searchQueryLibrary = "";
                        applySearch();
                    } else if (pageIndex == PAGE_BOOKMARKS) {
                        searchQueryBookmarks = "";
                        applyBookmarksSearch();
                    } else if (pageIndex == PAGE_NOTES) {
                        searchQueryNotes = "";
                        applyNotesSearch();
                    }
                    updateSearchButtonTint();
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            searchInput.requestFocus();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        });
        dialog.show();
    }

    private void updateSearchButtonTint() {
        if (searchButton == null || libraryPager == null) return;
        int currentItem = libraryPager.getCurrentItem();
        String query = "";
        if (currentItem == PAGE_LIBRARY) query = searchQueryLibrary;
        else if (currentItem == PAGE_BOOKMARKS) query = searchQueryBookmarks;
        else if (currentItem == PAGE_NOTES) query = searchQueryNotes;

        if (query != null && !query.trim().isEmpty()) {
            searchButton.setColorFilter(getResources().getColor(R.color.primary));
        } else {
            searchButton.setColorFilter(null);
        }
    }

    private void applySearch() {

        if (adapter == null) {
            return;
        }

        List<Book> filteredBooks = new ArrayList<>();
        String query = searchQueryLibrary != null ? searchQueryLibrary : "";

        for (Book book : allBooks) {
            if (book == null) {
                continue;
            }
            List<String> tags = new ArrayList<>();
            List<Tag> tList = currentBookTagsMap.get(book.bookId);
            if (tList != null) {
                for (Tag t : tList) {
                    if (t.name != null) tags.add(t.name);
                }
            }
            if (com.reader.oghma.util.BookSearchMatcher.matches(book.title, book.author, book.format, tags, query)) {
                filteredBooks.add(book);
            }
        }

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

        if (selectedTagId != null && filteredBooks.isEmpty()) {
            selectedTagId = null;
            updateTagFilterRow();
            applySearch();
            Toast.makeText(requireContext(), "Tag filter cleared because no books matched", Toast.LENGTH_SHORT).show();
            return;
        }

        sortBooks(filteredBooks);
        adapter.setBooks(filteredBooks);

        boolean isEmpty = filteredBooks.isEmpty();
        bookRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        if (emptyLibrary != null) {
            emptyLibrary.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
    }

    private void applyBookmarksSearch() {
        if (bookBookmarksAdapter == null) return;
        List<Book> filtered = new ArrayList<>();
        String query = searchQueryBookmarks != null ? searchQueryBookmarks : "";

        for (Book book : allBookmarkedBooks) {
            if (book == null) continue;
            List<String> tags = new ArrayList<>();
            List<Tag> tList = currentBookTagsMap.get(book.bookId);
            if (tList != null) {
                for (Tag t : tList) {
                    if (t.name != null) tags.add(t.name);
                }
            }
            if (com.reader.oghma.util.BookSearchMatcher.matches(book.title, book.author, book.format, tags, query)) {
                filtered.add(book);
            }
        }
        bookBookmarksAdapter.setBooks(filtered);
        boolean isEmpty = filtered.isEmpty();
        bookBookmarksRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        if (bookBookmarksEmptyText != null) {
            bookBookmarksEmptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            if (isEmpty && !query.isEmpty()) {
                if (bookBookmarksEmptyText instanceof TextView) {
                    ((TextView) bookBookmarksEmptyText).setText("No matches for \"" + searchQueryBookmarks + "\"");
                }
            } else if (isEmpty) {
                if (bookBookmarksEmptyText instanceof TextView) {
                    ((TextView) bookBookmarksEmptyText).setText("No bookmarked books yet.\nAdd a bookmark from the book options menu.");
                }
            }
        }
    }

    private void applyNotesSearch() {
        if (bookmarkAdapter == null) return;
        List<Bookmark> filtered = new ArrayList<>();
        String query = searchQueryNotes != null ? searchQueryNotes : "";

        for (Bookmark b : allNotes) {
            if (b == null) continue;
            String noteText = b.note != null ? b.note : "";
            String chTitle = b.chapterTitle != null ? b.chapterTitle : "";
            String selText = b.selectedText != null ? b.selectedText : "";
            if (com.reader.oghma.util.BookSearchMatcher.matches(chTitle, noteText, selText, null, query)) {
                filtered.add(b);
            }
        }
        bookmarkAdapter.setBookmarks(filtered);
        boolean isEmpty = filtered.isEmpty();
        bookmarksRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        if (bookmarksEmptyText != null) {
            bookmarksEmptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            if (isEmpty && !query.isEmpty()) {
                if (bookmarksEmptyText instanceof TextView) {
                    ((TextView) bookmarksEmptyText).setText("No matches for \"" + searchQueryNotes + "\"");
                }
            } else if (isEmpty) {
                if (bookmarksEmptyText instanceof TextView) {
                    ((TextView) bookmarksEmptyText).setText("No notes yet.\nSelect text while reading to highlight it.");
                }
            }
        }
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
                    int currentItem = libraryPager != null ? libraryPager.getCurrentItem() : PAGE_LIBRARY;
                    if (currentItem == PAGE_FIND_MORE) {
                        if (googleBooksSearchInput != null) {
                            googleBooksSearchInput.requestFocus();
                            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                            if (imm != null) imm.showSoftInput(googleBooksSearchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                        }
                    } else {
                        showSearchDialogForCurrentPage(currentItem);
                    }
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
                        updateSearchButtonTint();
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
                                allNotes.clear();
                                if (bookmarks != null) {
                                    allNotes.addAll(bookmarks);
                                }
                                applyNotesSearch();
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
        if (book == null || book.source != UnifiedBookItem.Source.GUTENBERG) {
            if (book != null && book.previewUrl != null && !book.previewUrl.isEmpty()) {
                openGoogleBooksPreview(book.previewUrl);
            } else {
                Toast.makeText(requireContext(), "This book is not available for EPUB download.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String downloadUrl = book.downloadUrl;
        if ((downloadUrl == null || downloadUrl.isEmpty())
                && book.gutendexBook != null) {
            downloadUrl = book.gutendexBook.getEpubDownloadUrl();
        }
        if ((downloadUrl == null || downloadUrl.isEmpty())
                && book.projectGutenbergBook != null) {
            downloadUrl = book.projectGutenbergBook.epubUrl;
        }
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            Toast.makeText(requireContext(), "No EPUB download is listed for this book.", Toast.LENGTH_SHORT).show();
            return;
        }

        int gutenbergId = book.gutenbergId;
        if (gutenbergId <= 0 && book.gutendexBook != null) {
            gutenbergId = book.gutendexBook.id;
        }
        if (gutenbergId <= 0 && book.projectGutenbergBook != null) {
            gutenbergId = book.projectGutenbergBook.id;
        }
        if (gutenbergId <= 0) {
            Toast.makeText(requireContext(), "The Gutenberg book ID is missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (downloadUrl.startsWith("http://")) {
            downloadUrl = downloadUrl.replaceFirst("http://", "https://");
        }

        String title = book.title != null && !book.title.trim().isEmpty()
                ? book.title : "Unknown book";
        String author = book.author != null && !book.author.trim().isEmpty()
                ? book.author : "Unknown author";
        String uniqueTitle = title + " - PG" + gutenbergId
                + "-" + System.currentTimeMillis();
        String fileName = createSafeBookFileName(uniqueTitle, ".epub");

        downloadGutenbergEpub(downloadUrl, fileName, title, author, gutenbergId);
    }

    private void searchGoogleBooks(String query) {
        if (getView() == null || googleBooksProgress == null || googleBooksEmptyText == null) {
            return;
        }

        final int requestId = ++searchRequestId;

        googleBooksProgress.setVisibility(View.VISIBLE);
        googleBooksEmptyText.setVisibility(View.GONE);

        List<GoogleBookItem> googleBooksList = new ArrayList<>();
        List<GutendexBook> gutendexBooksList = new ArrayList<>();
        List<ProjectGutenbergBook> projectGutenbergBooksList = new ArrayList<>();

        final boolean[] googleFinished = {false};
        final boolean[] gutendexFinished = {false};
        final boolean[] projectGutenbergFinished = {false};
        final boolean[] gutendexAvailable = {false};
        final boolean[] projectGutenbergAvailable = {false};
        final boolean[] outageToastShown = {false};

        // Render as soon as Google finishes, then refresh the same list whenever
        // either Gutenberg catalogue responds. A slow catalogue must not block previews.
        Runnable renderResults = () -> {
            if (requestId != searchRequestId || !googleFinished[0] || !isAdded()) {
                return;
            }

            if (getView() == null || googleBooksProgress == null) {
                return;
            }

            googleBooksProgress.setVisibility(View.GONE);
            processCombinedResults(
                    googleBooksList,
                    gutendexBooksList,
                    projectGutenbergBooksList
            );

            if (gutendexFinished[0]
                    && projectGutenbergFinished[0]
                    && !gutendexAvailable[0]
                    && !projectGutenbergAvailable[0]
                    && !outageToastShown[0]) {
                outageToastShown[0] = true;
                Toast.makeText(
                        requireContext(),
                        "The public-domain catalogue is temporarily unavailable. Google previews may still appear.",
                        Toast.LENGTH_LONG
                ).show();
            }
        };

        // Google Books supplies broad discovery results and preview links.
        fetchGoogleBooksPages(query, 0, 40, googleBooksList, requestId, () -> {
            googleFinished[0] = true;
            renderResults.run();
        });

        // Gutendex is retained as one catalogue provider.
        fetchGutendexPages(
                query, 1, 3, gutendexBooksList, requestId, gutendexAvailable,
                () -> {
                    gutendexFinished[0] = true;
                    renderResults.run();
                }
        );

        // The official OPDS feed is an independent fallback. Request one page per search.
        fetchProjectGutenbergBooks(
                query, projectGutenbergBooksList, requestId, projectGutenbergAvailable,
                () -> {
                    projectGutenbergFinished[0] = true;
                    renderResults.run();
                }
        );
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

        downloadGoogleBookFromUrl(downloadUrl, fileName, format, title, author, book.id, googleCategories);
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
        Context appContext = requireContext().getApplicationContext();
        BookIngestor ingestor = bookIngestor;
        if (ingestor == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "The library importer is not ready.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (getView() != null && googleBooksProgress != null) {
            googleBooksProgress.setVisibility(View.VISIBLE);
        }

        final String finalFormat = format != null ? format : "epub";
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
                        if (getView() != null && googleBooksProgress != null) {
                            googleBooksProgress.setVisibility(View.GONE);
                        }

                        if (!response.isSuccessful() || response.body() == null) {
                            int code = response.code();
                            if (response.errorBody() != null) {
                                try {
                                    String err = response.errorBody().string();
                                    Log.e("GoogleBooks", "Download error body: " + err);
                                } catch (Exception ignored) {
                                } finally {
                                    response.errorBody().close();
                                }
                            }
                            postDownloadMessage("Could not download this book (HTTP " + code + ")");
                            return;
                        }

                        ResponseBody body = response.body();
                        try {
                            downloadExecutor.execute(() -> saveAndImportGoogleBook(
                                    appContext,
                                    body,
                                    fileName,
                                    finalFormat,
                                    finalTitle,
                                    finalAuthor,
                                    volumeId,
                                    finalDownloadUrl,
                                    finalCategories,
                                    ingestor
                            ));
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
                        if (getView() != null && googleBooksProgress != null) {
                            googleBooksProgress.setVisibility(View.GONE);
                        }
                        Log.e("GoogleBooksDownload", "Request failed", t);
                        postDownloadMessage("Download failed. Check your connection and try again.");
                    }
                });
    }

    private void saveAndImportGoogleBook(
            Context appContext,
            ResponseBody responseBody,
            String fileName,
            String format,
            String title,
            String author,
            String volumeId,
            String downloadUrl,
            List<String> googleCategories,
            BookIngestor ingestor
    ) {
        File tempFile = new File(appContext.getCacheDir(), "google_books_temp_" + System.currentTimeMillis());
        long downloadedBytes = 0;

        try (ResponseBody body = responseBody;
             InputStream inputStream = body.byteStream();
             FileOutputStream outputStream = new FileOutputStream(tempFile)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                downloadedBytes += bytesRead;
            }
        } catch (Exception e) {
            Log.e("GoogleBooks", "Error saving to temp file", e);
            if (tempFile.exists()) tempFile.delete();
            postDownloadMessage("Download failed while saving file");
            return;
        }

        Log.d("GoogleBooksDownload", "Actual Bytes: " + downloadedBytes);
        Log.d("GoogleBooksDownload", "Volume ID: " + (volumeId != null ? volumeId : "null"));
        Log.d("GoogleBooksDownload", "Title: " + title);
        Log.d("GoogleBooksDownload", "Format: " + format);
        Log.d("GoogleBooksDownload", "URL: " + downloadUrl);

        byte[] header = new byte[4];
        try (FileInputStream fis = new FileInputStream(tempFile)) {
            int read = fis.read(header);
            if (read < 4) {
                Log.e("GoogleBooksDownload", "File too small to read magic bytes");
            }
        } catch (java.io.IOException e) {
            Log.e("GoogleBooksDownload", "Error reading magic bytes", e);
        }

        boolean isZip = (header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04);
        boolean isPdf = (header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46);

        boolean isValid = false;
        if ("pdf".equalsIgnoreCase(format)) {
            isValid = isPdf;
            if (!isValid) {
                Log.e("GoogleBooksDownload", "Downloaded file is not a valid PDF");
            }
        } else {
            isValid = isZip && isValidEpub(tempFile);
            if (!isValid) {
                Log.e("GoogleBooksDownload", "Downloaded file is not a valid EPUB/ZIP");
            }
        }

        if (!isValid) {
            String previewText = "";
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
                Log.e("GoogleBooksDownload", "The server returned an HTML page instead of a book file.");

                String fallbackUrl = null;
                if (!downloadUrl.contains("gutenberg.org")) {
                    if ("Alice's Adventures in Wonderland".equalsIgnoreCase(title)) {
                        fallbackUrl = "https://www.gutenberg.org/cache/epub/11/pg11-images.epub";
                    } else if ("The War of the Worlds".equalsIgnoreCase(title) && author != null && author.toLowerCase().contains("wells")) {
                        fallbackUrl = "https://www.gutenberg.org/cache/epub/36/pg36-images.epub";
                    } else if ("The Picture of Dorian Gray".equalsIgnoreCase(title) && author != null && author.toLowerCase().contains("wilde")) {
                        fallbackUrl = "https://www.gutenberg.org/cache/epub/26740/pg26740-images.epub";
                    }
                }

                if (fallbackUrl != null) {
                    final String targetFallbackUrl = fallbackUrl;
                    final String fallbackFileName = createSafeBookFileName(title, ".epub");
                    postDownloadMessage("Google Books download unavailable. Downloading public-domain EPUB...");
                    if (tempFile.exists()) tempFile.delete();
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        downloadGoogleBookFromUrl(
                                targetFallbackUrl,
                                fallbackFileName,
                                "epub",
                                title,
                                author,
                                volumeId,
                                googleCategories
                        );
                    });
                    return;
                }

                postDownloadMessage("Google Books returned a web page instead of a book file. Authorization required.");
                if (tempFile.exists()) tempFile.delete();
                return;
            }

            if (tempFile.exists()) tempFile.delete();
            postDownloadMessage("Downloaded file format validation failed.");
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
                    format,
                    title,
                    author,
                    googleCategories
            );

            postDownloadMessage("Book downloaded. Adding it to your library.");
        } catch (java.io.IOException e) {
            Log.e("GoogleBooksDownload", "Could not pass book to the library importer", e);
            if (tempFile.exists()) {
                tempFile.delete();
            }
            postDownloadMessage("The book could not be imported.");
        }
    }

    private void fetchGutendexPages(
            String query,
            int page,
            int maxPages,
            List<GutendexBook> accumulated,
            int requestId,
            boolean[] sourceAvailable,
            Runnable onComplete
    ) {
        fetchGutendexPageAttempt(
                query, page, maxPages, accumulated, requestId,
                sourceAvailable, onComplete, 0
        );
    }

    private void fetchGutendexPageAttempt(
            String query,
            int page,
            int maxPages,
            List<GutendexBook> accumulated,
            int requestId,
            boolean[] sourceAvailable,
            Runnable onComplete,
            int retryAttempt
    ) {
        if (!isAdded() || requestId != searchRequestId) {
            onComplete.run();
            return;
        }

        GutendexClient.getApi()
                .searchBooks(normalizeGutendexQuery(query), "false", "application/epub", page)
                .enqueue(new Callback<GutendexResponse>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<GutendexResponse> call,
                            @NonNull Response<GutendexResponse> response
                    ) {
                        if (!isAdded() || requestId != searchRequestId) {
                            if (response.errorBody() != null) response.errorBody().close();
                            onComplete.run();
                            return;
                        }

                        if (!response.isSuccessful() || response.body() == null) {
                            if (response.errorBody() != null) {
                                response.errorBody().close();
                            }

                            Log.w(
                                    "GutendexSearch",
                                    "HTTP " + response.code()
                                            + " for query '" + query + "', page " + page
                            );

                            if (isRetryableGutendexStatus(response.code())
                                    && retryAttempt < MAX_GUTENDEX_RETRIES) {
                                scheduleGutendexRetry(
                                        query, page, maxPages, accumulated, requestId,
                                        sourceAvailable, onComplete, retryAttempt + 1,
                                        "HTTP " + response.code()
                                );
                            } else {
                                onComplete.run();
                            }
                            return;
                        }

                        sourceAvailable[0] = true;
                        GutendexResponse body = response.body();
                        if (body.results != null) {
                            accumulated.addAll(body.results);
                        }

                        if (body.next != null
                                && page < maxPages
                                && body.results != null
                                && !body.results.isEmpty()) {
                            fetchGutendexPages(
                                    query, page + 1, maxPages, accumulated, requestId,
                                    sourceAvailable, onComplete
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
                        if (requestId == searchRequestId
                                && isAdded()
                                && retryAttempt < MAX_GUTENDEX_RETRIES) {
                            scheduleGutendexRetry(
                                    query, page, maxPages, accumulated, requestId,
                                    sourceAvailable, onComplete, retryAttempt + 1,
                                    t.getClass().getSimpleName()
                            );
                        } else {
                            onComplete.run();
                        }
                    }
                });
    }

    private void scheduleGutendexRetry(
            String query,
            int page,
            int maxPages,
            List<GutendexBook> accumulated,
            int requestId,
            boolean[] sourceAvailable,
            Runnable onComplete,
            int nextAttempt,
            String reason
    ) {
        long delayMs = 1500L * (1L << (nextAttempt - 1));
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                () -> {
                    if (!isAdded() || requestId != searchRequestId) {
                        onComplete.run();
                        return;
                    }

                    fetchGutendexPageAttempt(
                            query, page, maxPages, accumulated, requestId,
                            sourceAvailable, onComplete, nextAttempt
                    );
                },
                delayMs
        );
    }

    private boolean isRetryableGutendexStatus(int statusCode) {
        return statusCode == 408
                || statusCode == 429
                || statusCode >= 500;
    }

    private void fetchProjectGutenbergBooks(
            String query,
            List<ProjectGutenbergBook> accumulated,
            int requestId,
            boolean[] sourceAvailable,
            Runnable onComplete
    ) {
        fetchProjectGutenbergSearchAttempt(
                query, accumulated, requestId, sourceAvailable, onComplete, 0
        );
    }

    private void fetchProjectGutenbergSearchAttempt(
            String query,
            List<ProjectGutenbergBook> accumulated,
            int requestId,
            boolean[] sourceAvailable,
            Runnable onComplete,
            int retryAttempt
    ) {
        if (!isAdded() || requestId != searchRequestId) {
            onComplete.run();
            return;
        }

        String opdsQuery = normalizeProjectGutenbergQuery(query);
        ProjectGutenbergClient.getApi()
                .searchBooks(opdsQuery)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<ResponseBody> call,
                            @NonNull Response<ResponseBody> response
                    ) {
                        if (!isAdded() || requestId != searchRequestId) {
                            if (response.body() != null) response.body().close();
                            if (response.errorBody() != null) response.errorBody().close();
                            onComplete.run();
                            return;
                        }

                        if (!response.isSuccessful() || response.body() == null) {
                            if (response.errorBody() != null) response.errorBody().close();
                            if (isRetryableCatalogueStatus(response.code())
                                    && retryAttempt < MAX_OPDS_SEARCH_RETRIES) {
                                scheduleProjectGutenbergSearchRetry(
                                        query, accumulated, requestId, sourceAvailable,
                                        onComplete, retryAttempt + 1
                                );
                            } else {
                                Log.w(
                                        "ProjectGutenbergSearch",
                                        "OPDS search returned HTTP " + response.code()
                                                + " for query '" + query + "'"
                                );
                                onComplete.run();
                            }
                            return;
                        }

                        ResponseBody responseBody = response.body();
                        try {
                            searchExecutor.execute(() -> {
                                List<ProjectGutenbergBook> candidates = new ArrayList<>();
                                boolean parsedSuccessfully = false;
                                try (ResponseBody closeableBody = responseBody;
                                     InputStream inputStream = closeableBody.byteStream()) {
                                    candidates.addAll(
                                            ProjectGutenbergParser.parseSearchListing(inputStream)
                                    );
                                    parsedSuccessfully = true;
                                } catch (Exception e) {
                                    Log.e(
                                            "ProjectGutenbergSearch",
                                            "Could not parse OPDS search listing for query '"
                                                    + query + "'",
                                            e
                                    );
                                }

                                final boolean parsed = parsedSuccessfully;
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                    if (!isAdded() || requestId != searchRequestId) {
                                        onComplete.run();
                                        return;
                                    }
                                    if (!parsed) {
                                        onComplete.run();
                                        return;
                                    }

                                    sourceAvailable[0] = true;
                                    Log.d(
                                            "ProjectGutenbergSearch",
                                            "OPDS listing returned " + candidates.size()
                                                    + " candidates for query '" + query + "'"
                                    );
                                    fetchProjectGutenbergBookDetails(
                                            candidates, 0, accumulated, requestId,
                                            sourceAvailable, onComplete, 0
                                    );
                                });
                            });
                        } catch (java.util.concurrent.RejectedExecutionException e) {
                            responseBody.close();
                            Log.w("ProjectGutenbergSearch", "Search executor is shutting down", e);
                            onComplete.run();
                        }
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<ResponseBody> call,
                            @NonNull Throwable t
                    ) {
                        if (requestId == searchRequestId
                                && isAdded()
                                && retryAttempt < MAX_OPDS_SEARCH_RETRIES) {
                            scheduleProjectGutenbergSearchRetry(
                                    query, accumulated, requestId, sourceAvailable,
                                    onComplete, retryAttempt + 1
                            );
                        } else {
                            Log.w(
                                    "ProjectGutenbergSearch",
                                    "OPDS search request failed for query '" + query + "'",
                                    t
                            );
                            onComplete.run();
                        }
                    }
                });
    }

    private void scheduleProjectGutenbergSearchRetry(
            String query,
            List<ProjectGutenbergBook> accumulated,
            int requestId,
            boolean[] sourceAvailable,
            Runnable onComplete,
            int nextAttempt
    ) {
        long delayMs = 1500L * (1L << (nextAttempt - 1));
        Log.w(
                "ProjectGutenbergSearch",
                "Retrying OPDS search in " + delayMs + "ms. Attempt "
                        + nextAttempt + " of " + MAX_OPDS_SEARCH_RETRIES
        );
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                () -> fetchProjectGutenbergSearchAttempt(
                        query, accumulated, requestId, sourceAvailable,
                        onComplete, nextAttempt
                ),
                delayMs
        );
    }

    /**
     * Resolve a bounded number of search hits sequentially. Sequential detail
     * requests avoid hammering Gutenberg and ensure each result has its own
     * rights statement and EPUB acquisition link before it is marked downloadable.
     */
    private void fetchProjectGutenbergBookDetails(
            List<ProjectGutenbergBook> candidates,
            int index,
            List<ProjectGutenbergBook> accumulated,
            int requestId,
            boolean[] sourceAvailable,
            Runnable onComplete,
            int retryAttempt
    ) {
        int limit = Math.min(
                candidates == null ? 0 : candidates.size(),
                MAX_OPDS_DETAIL_BOOKS
        );
        if (!isAdded() || requestId != searchRequestId || index >= limit) {
            onComplete.run();
            return;
        }

        ProjectGutenbergBook candidate = candidates.get(index);
        if (candidate == null || candidate.id <= 0
                || candidate.bookPageUrl == null || candidate.bookPageUrl.trim().isEmpty()) {
            fetchProjectGutenbergBookDetails(
                    candidates, index + 1, accumulated, requestId,
                    sourceAvailable, onComplete, 0
            );
            return;
        }

        String detailUrl = candidate.bookPageUrl + ".opds";
        ProjectGutenbergClient.getApi()
                .getBookDetail(detailUrl)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<ResponseBody> call,
                            @NonNull Response<ResponseBody> response
                    ) {
                        if (!isAdded() || requestId != searchRequestId) {
                            if (response.body() != null) response.body().close();
                            if (response.errorBody() != null) response.errorBody().close();
                            onComplete.run();
                            return;
                        }

                        if (!response.isSuccessful() || response.body() == null) {
                            if (response.errorBody() != null) response.errorBody().close();
                            if (isRetryableCatalogueStatus(response.code())
                                    && retryAttempt < MAX_OPDS_DETAIL_RETRIES) {
                                scheduleProjectGutenbergDetailRetry(
                                        candidates, index, accumulated, requestId,
                                        sourceAvailable, onComplete, retryAttempt + 1
                                );
                            } else {
                                Log.w(
                                        "ProjectGutenbergSearch",
                                        "OPDS detail returned HTTP " + response.code()
                                                + " for Gutenberg ID " + candidate.id
                                );
                                fetchProjectGutenbergBookDetails(
                                        candidates, index + 1, accumulated, requestId,
                                        sourceAvailable, onComplete, 0
                                );
                            }
                            return;
                        }

                        ResponseBody responseBody = response.body();
                        try {
                            searchExecutor.execute(() -> {
                                ProjectGutenbergBook detail = null;
                                try (ResponseBody closeableBody = responseBody;
                                     InputStream inputStream = closeableBody.byteStream()) {
                                    detail = ProjectGutenbergParser.parseDetail(inputStream);
                                } catch (Exception e) {
                                    Log.w(
                                            "ProjectGutenbergSearch",
                                            "Could not parse OPDS detail for Gutenberg ID "
                                                    + candidate.id,
                                            e
                                    );
                                }

                                ProjectGutenbergBook parsedDetail = detail;
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                    if (!isAdded() || requestId != searchRequestId) {
                                        onComplete.run();
                                        return;
                                    }

                                    if (parsedDetail != null
                                            && parsedDetail.id > 0
                                            && parsedDetail.publicDomainInUs
                                            && parsedDetail.epubUrl != null
                                            && !parsedDetail.epubUrl.trim().isEmpty()) {
                                        accumulated.add(parsedDetail);
                                        Log.d(
                                                "ProjectGutenbergSearch",
                                                "Verified EPUB metadata for Gutenberg ID "
                                                        + parsedDetail.id
                                        );
                                    }

                                    fetchProjectGutenbergBookDetails(
                                            candidates, index + 1, accumulated, requestId,
                                            sourceAvailable, onComplete, 0
                                    );
                                });
                            });
                        } catch (java.util.concurrent.RejectedExecutionException e) {
                            responseBody.close();
                            Log.w("ProjectGutenbergSearch", "Search executor is shutting down", e);
                            fetchProjectGutenbergBookDetails(
                                    candidates, index + 1, accumulated, requestId,
                                    sourceAvailable, onComplete, 0
                            );
                        }
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<ResponseBody> call,
                            @NonNull Throwable t
                    ) {
                        if (requestId == searchRequestId && isAdded()
                                && retryAttempt < MAX_OPDS_DETAIL_RETRIES) {
                            scheduleProjectGutenbergDetailRetry(
                                    candidates, index, accumulated, requestId,
                                    sourceAvailable, onComplete, retryAttempt + 1
                            );
                        } else {
                            Log.w(
                                    "ProjectGutenbergSearch",
                                    "OPDS detail request failed for Gutenberg ID " + candidate.id,
                                    t
                            );
                            fetchProjectGutenbergBookDetails(
                                    candidates, index + 1, accumulated, requestId,
                                    sourceAvailable, onComplete, 0
                            );
                        }
                    }
                });
    }

    private void scheduleProjectGutenbergDetailRetry(
            List<ProjectGutenbergBook> candidates,
            int index,
            List<ProjectGutenbergBook> accumulated,
            int requestId,
            boolean[] sourceAvailable,
            Runnable onComplete,
            int nextAttempt
    ) {
        long delayMs = 1500L * (1L << (nextAttempt - 1));
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                () -> fetchProjectGutenbergBookDetails(
                        candidates, index, accumulated, requestId,
                        sourceAvailable, onComplete, nextAttempt
                ),
                delayMs
        );
    }

    private boolean isRetryableCatalogueStatus(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private String normalizeGutendexQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.replaceAll("(?i)['’]s\\b", "")
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeProjectGutenbergQuery(String query) {
        if (query == null) {
            return "";
        }

        // Gutenberg's search guidance recommends avoiding punctuation.
        // Remove common possessive apostrophes before stripping remaining punctuation.
        String normalized = query.replaceAll("(?i)['’]s\\b", "")
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return normalized.isEmpty() ? query.trim() : normalized;
    }

    private void processCombinedResults(
            List<GoogleBookItem> googleItems,
            List<GutendexBook> gutendexItems,
            List<ProjectGutenbergBook> projectGutenbergItems
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
                item.gutenbergId = gBook.id;
                unifiedList.add(item);
            }
        }

        // Merge the official OPDS catalogue into the same Gutenberg result set.
        // Only entries with explicit US public-domain rights and an EPUB link are accepted.
        if (projectGutenbergItems != null) {
            for (ProjectGutenbergBook pgBook : projectGutenbergItems) {
                if (pgBook == null || !pgBook.publicDomainInUs
                        || pgBook.id <= 0
                        || pgBook.epubUrl == null || pgBook.epubUrl.trim().isEmpty()) {
                    continue;
                }

                String author = pgBook.authors == null || pgBook.authors.isEmpty()
                        ? "Unknown author" : String.join(", ", pgBook.authors);

                UnifiedBookItem existing = findGutenbergBookById(unifiedList, pgBook.id);
                if (existing != null) {
                    if (existing.coverUrl == null || existing.coverUrl.isEmpty()) {
                        existing.coverUrl = pgBook.coverUrl;
                    }
                    // Prefer the official OPDS acquisition URL over a cached catalogue URL.
                    existing.downloadUrl = pgBook.epubUrl;
                    existing.gutenbergId = pgBook.id;
                    existing.projectGutenbergBook = pgBook;
                    existing.isDownloadable = true;
                    continue;
                }

                UnifiedBookItem item = new UnifiedBookItem();
                item.source = UnifiedBookItem.Source.GUTENBERG;
                item.title = pgBook.title;
                item.author = author;
                item.publishedDate = pgBook.publishedDate != null
                        ? pgBook.publishedDate : "";
                item.coverUrl = pgBook.coverUrl;
                item.isDownloadable = true;
                item.downloadUrl = pgBook.epubUrl;
                item.projectGutenbergBook = pgBook;
                item.gutenbergId = pgBook.id;
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

                UnifiedBookItem matchingGutenberg = findMatchingGutenbergBook(
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

        if (getView() != null && googleBooksAdapter != null) {
            googleBooksAdapter.setBooks(unifiedList);
        }

        if (getView() != null && googleBooksEmptyText != null) {
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
    }

    private UnifiedBookItem findGutenbergBookById(
            List<UnifiedBookItem> books,
            int gutenbergId
    ) {
        for (UnifiedBookItem candidate : books) {
            if (candidate != null
                    && candidate.source == UnifiedBookItem.Source.GUTENBERG
                    && candidate.gutenbergId == gutenbergId) {
                return candidate;
            }
        }
        return null;
    }

    private UnifiedBookItem findMatchingGutenbergBook(
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

    private void downloadGutenbergEpub(
            String downloadUrl,
            String fileName,
            String title,
            String author,
            int gutenbergId
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

        if (getView() != null && googleBooksProgress != null) {
            googleBooksProgress.setVisibility(View.VISIBLE);
        }

        ProjectGutenbergClient.getApi()
                .downloadEpub(
                        downloadUrl,
                        "https://www.gutenberg.org/ebooks/" + gutenbergId
                )
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<ResponseBody> call,
                            @NonNull Response<ResponseBody> response
                    ) {
                        if (getView() != null && googleBooksProgress != null) {
                            googleBooksProgress.setVisibility(View.GONE);
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
                        if (getView() != null && googleBooksProgress != null) {
                            googleBooksProgress.setVisibility(View.GONE);
                        }
                        Log.e("GutenbergDownload", "EPUB download request failed", t);
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
                "gutenberg_epub_" + System.currentTimeMillis() + ".epub"
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
            Log.e("GutenbergDownload", "Could not save downloaded EPUB", e);
            if (tempFile.exists()) {
                tempFile.delete();
            }
            postDownloadMessage("The EPUB could not be saved.");
            return;
        }

        if (!isValidEpub(tempFile)) {
            Log.e("GutenbergDownload", "The server response is not a valid EPUB file.");
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
            Log.e("GutenbergDownload", "Could not pass EPUB to the library importer", e);
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
            Log.e("GutenbergDownload", "EPUB ZIP validation failed", e);
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