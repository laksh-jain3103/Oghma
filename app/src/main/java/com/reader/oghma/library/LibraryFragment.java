package com.reader.oghma.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import java.io.InputStream;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.reader.oghma.R;
import com.reader.oghma.reader.ReaderFragment;


public class LibraryFragment extends Fragment {

    private LibraryViewModel viewModel;
    private BookAdapter adapter;

    private RecyclerView bookRecyclerView;
    private View emptyLibrary;
    private FloatingActionButton addBookButton;
    private ImageButton searchButton;

    private ActivityResultLauncher<String[]> filePickerLauncher;

    private BookIngestor bookIngestor;



    public LibraryFragment() {
        // Required empty constructor
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {

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


    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        return inflater.inflate(
                R.layout.fragment_library,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        bookRecyclerView = view.findViewById(R.id.bookRecyclerView);
        emptyLibrary = view.findViewById(R.id.emptyLibrary);
        addBookButton = view.findViewById(R.id.addBookButton);
        searchButton = view.findViewById(R.id.searchButton);

        setupRecyclerView();
        setupViewModel();

        bookIngestor = new BookIngestor(
                requireContext(),
                viewModel.getRepository()
        );

        setupButtons();
    }

    private void setupRecyclerView() {

        adapter = new BookAdapter();

        adapter.setOnBookClickListener(book -> {

            Bundle args = new Bundle();

            args.putInt(
                    "bookId",
                    book.bookId
            );

            ReaderFragment readerFragment =
                    new ReaderFragment();

            readerFragment.setArguments(args);

            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(
                            R.id.fragmentContainer,
                            readerFragment
                    )
                    .addToBackStack(null)
                    .commit();
        });

        bookRecyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext())
        );

        bookRecyclerView.setAdapter(adapter);
    }

    private void setupViewModel() {

        viewModel = new ViewModelProvider(this)
                .get(LibraryViewModel.class);

        viewModel.getBooks().observe(
                getViewLifecycleOwner(),
                books -> {

                    adapter.setBooks(books);

                    boolean isEmpty =
                            books == null || books.isEmpty();

                    emptyLibrary.setVisibility(
                            isEmpty ? View.VISIBLE : View.GONE
                    );

                    bookRecyclerView.setVisibility(
                            isEmpty ? View.GONE : View.VISIBLE
                    );
                }
        );
    }

    private void setupButtons() {

        addBookButton.setOnClickListener(v -> {

            filePickerLauncher.launch(
                    new String[]{
                            "application/epub+zip",
                            "application/pdf",
                            "text/plain"
                    }
            );

        });


        searchButton.setOnClickListener(v -> {

            // Search will be added later.

        });
    }

    private void importBook(Uri uri) {

        String fileName = getFileName(uri);

        if (fileName == null) {
            return;
        }

        String format = getFormat(fileName);

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

    private String getFileName(Uri uri) {

        String fileName = null;

        Cursor cursor = requireContext()
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
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } finally {
                cursor.close();
            }
        }

        return fileName;
    }

    private String getFormat(String fileName) {

        String lowerName = fileName.toLowerCase();

        if (lowerName.endsWith(".epub")) {
            return "epub";
        }

        if (lowerName.endsWith(".pdf")) {
            return "pdf";
        }

        if (lowerName.endsWith(".txt")) {
            return "txt";
        }

        return null;
    }
}