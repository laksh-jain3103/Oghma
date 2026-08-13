package com.reader.oghma.reader;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.reader.oghma.R;
import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.repository.BookRepository;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class ReaderFragment extends Fragment {

    private TextView readerText;
    private TextView readerTitle;
    private ImageButton backButton;

    private BookRepository repository;

    public ReaderFragment() {
        // Required empty constructor
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        return inflater.inflate(
                R.layout.fragment_reader,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        readerText = view.findViewById(
                R.id.readerText
        );

        readerTitle = view.findViewById(
                R.id.readerTitle
        );

        backButton = view.findViewById(
                R.id.backButton
        );

        repository = new BookRepository(
                requireActivity().getApplication()
        );

        backButton.setOnClickListener(v ->
                requireActivity()
                        .getSupportFragmentManager()
                        .popBackStack()
        );

        loadBook();
    }

    private void loadBook() {

        Bundle args = getArguments();

        if (args == null) {
            readerText.setText("No book selected.");
            return;
        }

        int bookId = args.getInt("bookId", -1);

        if (bookId == -1) {
            readerText.setText("Invalid book.");
            return;
        }

        repository.getBook(
                bookId,
                book -> {

                    if (book == null) {

                        requireActivity().runOnUiThread(() ->
                                readerText.setText(
                                        "Book not found."
                                )
                        );

                        return;
                    }

                    requireActivity().runOnUiThread(() ->
                            displayBook(book)
                    );
                }
        );
    }

    private void displayBook(Book book) {

        readerTitle.setText(book.title);

        if ("txt".equalsIgnoreCase(book.format)) {

            displayTxtBook(book);
            return;
        }

        if ("epub".equalsIgnoreCase(book.format)) {

            displayEpubBook(book);
            return;
        }

        readerText.setText(
                "This format is not supported yet."
        );
    }

    private void displayTxtBook(Book book) {

        File file = new File(book.filePath);

        if (!file.exists()) {

            readerText.setText(
                    "The book file could not be found."
            );

            return;
        }

        try {

            String content = readTextFile(file);

            readerText.setText(content);

        } catch (IOException e) {

            readerText.setText(
                    "Could not open the book."
            );

            e.printStackTrace();
        }
    }

    private void displayEpubBook(Book book) {

        readerText.setText(
                "EPUB reader is being loaded..."
        );

        // Readium EPUB implementation will go here.
    }

    private String readTextFile(File file)
            throws IOException {

        StringBuilder content =
                new StringBuilder();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new FileReader(file)
                        )
        ) {

            String line;

            while ((line = reader.readLine()) != null) {

                content.append(line);
                content.append("\n");
            }
        }

        return content.toString();
    }
}