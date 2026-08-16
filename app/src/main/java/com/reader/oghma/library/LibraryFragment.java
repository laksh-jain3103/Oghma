package com.reader.oghma.library;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.reader.oghma.R;
import com.reader.oghma.data.entity.Book;
import com.reader.oghma.reader.ReaderFragment;
import java.io.File;
import java.io.InputStream;


public class LibraryFragment extends Fragment {

    private LibraryViewModel viewModel;

    private BookAdapter adapter;

    private RecyclerView bookRecyclerView;

    private View emptyLibrary;

    private FloatingActionButton addBookButton;

    private ImageButton searchButton;

    private ImageButton viewModeButton;

    private ImageButton sortButton;

    private boolean isGridView = false;

    private GridSpacingItemDecoration gridSpacing;

    private ActivityResultLauncher<String[]> filePickerLauncher;

    private BookIngestor bookIngestor;

    private BottomNavigationView bottomNavigation;

    private ImageButton settingsButton;


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


        // Grid spacing

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


        setupButtons();
    }


    // =========================================================
    // RecyclerView
    // =========================================================

    private void setupRecyclerView() {

        adapter =
                new BookAdapter();


        // Normal card click
        // Opens the book.

        adapter.setOnBookClickListener(
                book -> openBook(book)
        );


        // Three-dot button

        adapter.setOnBookMoreClickListener(
                book -> showBookMenu(book)
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
                .setTitle(book.title)
                .setItems(
                        options,
                        (dialog, which) -> {

                            if (which == 0) {

                                showBookDetails(book);

                            } else if (which == 1) {

                                confirmRemoveBook(book);
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
                .setTitle("Book details")
                .setMessage(details)
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

    private void confirmRemoveBook(Book book) {

        new AlertDialog.Builder(
                requireContext()
        )
                .setTitle("Remove book?")
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

        // --------------------------------
        // Delete database record
        // --------------------------------

        viewModel
                .getRepository()
                .deleteBook(book);


        // --------------------------------
        // Delete Oghma's copied book file
        // --------------------------------

        if (
                book.filePath != null
                        && !book.filePath.isEmpty()
        ) {

            File bookFile =
                    new File(book.filePath);

            if (bookFile.exists()) {

                boolean deleted =
                        bookFile.delete();

                if (!deleted) {

                    bookFile.deleteOnExit();
                }
            }
        }


        // --------------------------------
        // Delete Oghma's generated cover
        // --------------------------------

        if (
                book.coverPath != null
                        && !book.coverPath.isEmpty()
        ) {

            File coverFile =
                    new File(book.coverPath);

            if (coverFile.exists()) {

                boolean deleted =
                        coverFile.delete();

                if (!deleted) {

                    coverFile.deleteOnExit();
                }
            }
        }


        // --------------------------------
        // Tell user
        // --------------------------------

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

                    adapter.setBooks(
                            books
                    );


                    boolean isEmpty =
                            books == null
                                    || books.isEmpty();


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
        );
    }


    // =========================================================
    // Buttons
    // =========================================================

    private void setupButtons() {

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


        searchButton.setOnClickListener(
                v -> {

                    // Search will be added later.
                }
        );


        viewModeButton.setOnClickListener(
                v ->
                        toggleViewMode()
        );


        sortButton.setOnClickListener(
                v -> {

                    // Sorting will be added later.
                }
        );

        settingsButton.setOnClickListener(
                v -> {

                    Toast.makeText(
                            requireContext(),
                            "Settings coming later",
                            Toast.LENGTH_SHORT
                    ).show();
                }
        );

        bottomNavigation.setOnItemSelectedListener(
                item -> {

                    int itemId = item.getItemId();

                    if (itemId == R.id.nav_library) {

                        return true;

                    } else if (itemId == R.id.nav_bookmarks) {

                        Toast.makeText(
                                requireContext(),
                                "Bookmarks coming later",
                                Toast.LENGTH_SHORT
                        ).show();

                        return false;

                    } else if (itemId == R.id.nav_find_more) {

                        Toast.makeText(
                                requireContext(),
                                "Find More coming later",
                                Toast.LENGTH_SHORT
                        ).show();

                        return false;
                    }

                    return false;
                }
        );

    }




    // =========================================================
    // Import book
    // =========================================================

    private void importBook(Uri uri) {

        String fileName =
                getFileName(uri);


        if (fileName == null) {
            return;
        }


        String format =
                getFormat(fileName);


        if (format == null) {
            return;
        }


        try {

            InputStream inputStream =
                    requireContext()
                            .getContentResolver()
                            .openInputStream(uri);


            if (inputStream == null) {
                return;
            }


            bookIngestor.importBook(
                    inputStream,
                    fileName,
                    format
            );


        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    // =========================================================
    // Get filename
    // =========================================================

    private String getFileName(Uri uri) {

        String fileName = null;


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
    // Get format
    // =========================================================

    private String getFormat(
            String fileName
    ) {

        String lowerName =
                fileName.toLowerCase();


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