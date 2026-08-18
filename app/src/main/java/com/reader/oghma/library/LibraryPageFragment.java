package com.reader.oghma.library;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
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

import com.reader.oghma.R;
import com.reader.oghma.data.entity.Book;
import com.reader.oghma.reader.ReaderFragment;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;


public class LibraryPageFragment extends Fragment {


    // =========================================================
    // Views
    // =========================================================

    private RecyclerView bookRecyclerView;

    private View emptyLibrary;

    private ImageButton viewModeButton;


    // =========================================================
    // Data
    // =========================================================

    private LibraryViewModel viewModel;

    private BookAdapter adapter;

    private BookIngestor bookIngestor;

    private final List<Book> allBooks =
            new ArrayList<>();


    // =========================================================
    // Search / sort
    // =========================================================

    private String searchQuery = "";

    private int sortMode = 0;

    private boolean sortDescending = false;

    private boolean isGridView = false;


    // =========================================================
    // Grid
    // =========================================================

    private GridSpacingItemDecoration gridSpacing;


    // =========================================================
    // File picker
    // =========================================================

    private ActivityResultLauncher<String[]> filePickerLauncher;


    // =========================================================
    // Constructor
    // =========================================================

    public LibraryPageFragment() {
        // Required empty constructor
    }


    // =========================================================
    // Create
    // =========================================================

    @Override
    public void onCreate(
            @Nullable Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );


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
    // Layout
    // =========================================================

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.fragment_library_page,
                container,
                false
        );
    }


    // =========================================================
    // View created
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


        bookRecyclerView =
                view.findViewById(
                        R.id.bookRecyclerView
                );


        emptyLibrary =
                view.findViewById(
                        R.id.emptyLibrary
                );


        viewModeButton =
                view.findViewById(
                        R.id.viewModeButton
                );


        gridSpacing =
                new GridSpacingItemDecoration(
                        16
                );


        setupRecyclerView();

        setupViewModel();


        bookIngestor =
                new BookIngestor(
                        requireContext(),
                        viewModel.getRepository()
                );


        viewModeButton.setOnClickListener(
                v -> toggleViewMode()
        );
    }


    // =========================================================
    // RecyclerView
    // =========================================================

    private void setupRecyclerView() {

        adapter =
                new BookAdapter();


        adapter.setOnBookClickListener(
                this::openBook
        );


        adapter.setOnBookMoreClickListener(
                this::showBookMenu
        );


        bookRecyclerView.setLayoutManager(
                new LinearLayoutManager(
                        requireContext()
                )
        );


        bookRecyclerView.setAdapter(
                adapter
        );
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

                    allBooks.clear();


                    if (books != null) {

                        allBooks.addAll(
                                books
                        );
                    }


                    applySearch();


                    boolean empty =
                            allBooks.isEmpty();


                    emptyLibrary.setVisibility(
                            empty
                                    ? View.VISIBLE
                                    : View.GONE
                    );


                    bookRecyclerView.setVisibility(
                            empty
                                    ? View.GONE
                                    : View.VISIBLE
                    );
                }
        );
    }


    // =========================================================
    // Open book
    // =========================================================

    private void openBook(
            Book book
    ) {

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
    // Grid / list
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


            try {

                bookRecyclerView.removeItemDecoration(
                        gridSpacing
                );

            } catch (Exception ignored) {
            }


            viewModeButton.setImageResource(
                    R.drawable.ic_view_list
            );


            viewModeButton.setContentDescription(
                    "List view"
            );
        }
    }


    // =========================================================
    // Search
    // =========================================================

    public void showSearchDialog() {

        if (!isAdded()) {
            return;
        }


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


                    if (
                            dialog.getWindow()
                                    != null
                    ) {

                        dialog.getWindow()
                                .setSoftInputMode(
                                        WindowManager
                                                .LayoutParams
                                                .SOFT_INPUT_STATE_ALWAYS_VISIBLE
                                );
                    }
                }
        );


        dialog.show();
    }


    // =========================================================
    // Apply search
    // =========================================================

    private void applySearch() {

        if (adapter == null) {
            return;
        }


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
                    searchQuery.toLowerCase(
                            Locale.ROOT
                    );


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


                if (
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
                                ).contains(query)
                ) {

                    filteredBooks.add(
                            book
                    );
                }
            }
        }


        sortBooks(
                filteredBooks
        );


        adapter.setBooks(
                filteredBooks
        );
    }


    // =========================================================
    // Sort
    // =========================================================

    public void showSortDialog() {

        if (!isAdded()) {
            return;
        }


        String[] sortOptions = {
                "Title",
                "Author",
                "Date added",
                "Format"
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

        String[] directions = {
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
                        directions,
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


    private void sortBooks(
            List<Book> books
    ) {

        Comparator<Book> comparator;


        switch (sortMode) {

            case 1:

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

                comparator =
                        Comparator.comparingLong(
                                book ->
                                        book.addedAt
                        );

                break;


            case 3:

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


    // =========================================================
    // Import
    // =========================================================

    public void startImport() {

        filePickerLauncher.launch(
                new String[]{
                        "application/epub+zip",
                        "application/pdf",
                        "text/plain"
                }
        );
    }


    private void importBook(
            Uri uri
    ) {

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
    // File name
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
                                    OpenableColumns.DISPLAY_NAME
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
    // Format
    // =========================================================

    private String getFormat(
            String fileName
    ) {

        String lower =
                fileName.toLowerCase(
                        Locale.ROOT
                );


        if (lower.endsWith(".epub")) {
            return "epub";
        }


        if (lower.endsWith(".pdf")) {
            return "pdf";
        }


        if (lower.endsWith(".txt")) {
            return "txt";
        }


        return null;
    }


    // =========================================================
    // Book menu
    // =========================================================

    private void showBookMenu(
            Book book
    ) {

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

                            } else {

                                confirmRemoveBook(
                                        book
                                );
                            }
                        }
                )
                .show();
    }


    private void showBookDetails(
            Book book
    ) {

        String author =
                book.author != null
                        ? book.author
                        : "Unknown author";


        String format =
                book.format != null
                        ? book.format.toUpperCase()
                        : "Unknown";


        new AlertDialog.Builder(
                requireContext()
        )
                .setTitle(
                        "Book details"
                )
                .setMessage(
                        "Title: "
                                + book.title
                                + "\n\nAuthor: "
                                + author
                                + "\n\nFormat: "
                                + format
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


    private void removeBook(
            Book book
    ) {

        viewModel
                .getRepository()
                .deleteBook(book);


        if (
                book.filePath != null
                        && !book.filePath.isEmpty()
        ) {

            File bookFile =
                    new File(
                            book.filePath
                    );


            if (bookFile.exists()) {

                bookFile.delete();
            }
        }


        if (
                book.coverPath != null
                        && !book.coverPath.isEmpty()
        ) {

            File coverFile =
                    new File(
                            book.coverPath
                    );


            if (coverFile.exists()) {

                coverFile.delete();
            }
        }


        Toast.makeText(
                requireContext(),
                "Removed from library",
                Toast.LENGTH_SHORT
        ).show();
    }
}