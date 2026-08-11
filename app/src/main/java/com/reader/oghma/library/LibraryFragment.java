package com.reader.oghma.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.reader.oghma.R;

public class LibraryFragment extends Fragment {

    private LibraryViewModel viewModel;
    private BookAdapter adapter;

    private RecyclerView bookRecyclerView;
    private View emptyLibrary;
    private FloatingActionButton addBookButton;
    private ImageButton searchButton;

    public LibraryFragment() {
        // Required empty constructor
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
        setupButtons();
    }

    private void setupRecyclerView() {

        adapter = new BookAdapter();

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
                viewModel.addTestBook();
        });

        searchButton.setOnClickListener(v -> {

            // Search will be added later.

        });
    }
}