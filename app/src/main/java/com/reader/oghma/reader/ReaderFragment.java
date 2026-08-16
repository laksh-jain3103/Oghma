package com.reader.oghma.reader;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;

import com.reader.oghma.R;
import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.repository.BookRepository;
import com.reader.oghma.data.repository.BookmarkRepository;
import com.reader.oghma.data.entity.Bookmark;
import org.readium.r2.navigator.epub.EpubNavigatorFragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class ReaderFragment extends Fragment {

    private TextView readerText;
    private TextView readerTitle;
    private ImageButton backButton;
    private FrameLayout readerContainer;

    private BookRepository repository;
    private BookmarkRepository bookmarkRepository;
    private EpubReaderBridge epubReaderBridge;

    private PdfReaderBridge pdfReaderBridge;

    private EpubNavigatorFragment epubNavigator;

    private int currentBookId = -1;

    private String bookmarkLocator;


    public ReaderFragment() {
        // Required empty constructor
    }


    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.fragment_reader,
                container,
                false
        );
    }


    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {

        super.onViewCreated(
                view,
                savedInstanceState
        );


        readerText =
                view.findViewById(
                        R.id.readerText
                );


        readerTitle =
                view.findViewById(
                        R.id.readerTitle
                );


        backButton =
                view.findViewById(
                        R.id.backButton
                );


        readerContainer =
                view.findViewById(
                        R.id.readerContainer
                );


        epubReaderBridge =
                new EpubReaderBridge(
                        requireContext()
                );

        pdfReaderBridge =
                new PdfReaderBridge(
                        requireContext()
                );

        repository =
                new BookRepository(
                        requireActivity()
                                .getApplication()
                );


        bookmarkRepository =
                new BookmarkRepository(
                        requireActivity()
                                .getApplication()
                );


        backButton.setOnClickListener(
                v ->
                        requireActivity()
                                .getSupportFragmentManager()
                                .popBackStack()
        );


        loadBook();
    }


    private void loadBook() {

        Bundle args =
                getArguments();


        if (args == null) {

            readerText.setText(
                    "No book selected."
            );

            return;
        }


        int bookId =
                args.getInt(
                        "bookId",
                        -1
                );

        bookmarkLocator =
                args.getString(
                        "bookmarkLocator",
                        null
                );

        if (bookId == -1) {

            readerText.setText(
                    "Invalid book."
            );

            return;
        }


        currentBookId =
                bookId;


        repository.getBook(
                bookId,
                book -> {

                    if (book == null) {

                        if (!isAdded()) {
                            return;
                        }

                        requireActivity().runOnUiThread(
                                () ->
                                        readerText.setText(
                                                "Book not found."
                                        )
                        );

                        return;
                    }


                    if (!isAdded()) {
                        return;
                    }


                    requireActivity().runOnUiThread(
                            () ->
                                    displayBook(book)
                    );
                }
        );
    }


    private void displayBook(
            Book book
    ) {

        readerTitle.setText(
                book.title
        );


        if (
                "txt".equalsIgnoreCase(
                        book.format
                )
        ) {

            displayTxtBook(book);

            return;
        }


        if (
                "epub".equalsIgnoreCase(
                        book.format
                )
        ) {

            displayEpubBook(book);

            return;
        }

        if (
                "pdf".equalsIgnoreCase(
                        book.format
                )
        ) {

            displayPdfBook(book);

            return;
        }

        readerText.setText(
                "This format is not supported yet."
        );
    }


    private void displayTxtBook(
            Book book
    ) {

        File file =
                new File(
                        book.filePath
                );


        if (!file.exists()) {

            readerText.setText(
                    "The book file could not be found."
            );

            return;
        }


        try {

            String content =
                    readTextFile(file);

            readerText.setText(
                    content
            );

        } catch (IOException e) {

            readerText.setText(
                    "Could not open the book."
            );

            e.printStackTrace();
        }
    }


    private void displayEpubBook(
            Book book
    ) {

        File epubFile =
                new File(
                        book.filePath
                );


        if (!epubFile.exists()) {

            readerText.setText(
                    "The EPUB file could not be found."
            );

            return;
        }


        readerText.setText(
                "EPUB reader is being loaded..."
        );


        epubReaderBridge.openEpub(
                epubFile,
                new EpubReaderBridge.Callback() {

                    @Override
                    public void onBookmarkRequested() {

                        handleBookmarkRequest();
                    }


                    @Override
                    public void onSuccess(
                            FragmentFactory factory
                    ) {

                        if (!isAdded()) {
                            return;
                        }


                        getChildFragmentManager()
                                .setFragmentFactory(
                                        factory
                                );


                        getChildFragmentManager()
                                .beginTransaction()
                                .replace(
                                        R.id.readerContainer,
                                        EpubNavigatorFragment.class,
                                        null,
                                        "epub_navigator"
                                )
                                .commit();


                        getChildFragmentManager()
                                .executePendingTransactions();


                        epubNavigator =
                                (EpubNavigatorFragment)
                                        getChildFragmentManager()
                                                .findFragmentByTag(
                                                        "epub_navigator"
                                                );
                    }


                    @Override
                    public void onError(
                            String message
                    ) {

                        if (!isAdded()) {
                            return;
                        }


                        readerText.setText(
                                "Could not open EPUB:\n\n"
                                        + message
                        );
                    }
                }
        );
    }


    private void handleBookmarkRequest() {

        if (epubNavigator == null) {

            Toast.makeText(
                    requireContext(),
                    "Reader is not ready.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        epubReaderBridge.getCurrentSelection(
                epubNavigator,
                new EpubReaderBridge.SelectionCallback() {

                    @Override
                    public void onSelection(
                            String selectedText,
                            String locatorJson,
                            String chapterTitle
                    ) {

                        if (
                                selectedText == null
                                        || selectedText.trim().isEmpty()
                        ) {

                            Toast.makeText(
                                    requireContext(),
                                    "No text selected.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }

                        if (
                                locatorJson == null
                                        || locatorJson.trim().isEmpty()
                        ) {

                            Toast.makeText(
                                    requireContext(),
                                    "Could not save the bookmark location.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }

                        Bookmark bookmark =
                                new Bookmark();

                        bookmark.bookId =
                                currentBookId;

                        bookmark.selectedText =
                                selectedText;

                        bookmark.locatorJson =
                                locatorJson;

                        bookmark.chapterTitle =
                                chapterTitle != null
                                        ? chapterTitle
                                        : "";

                        bookmark.createdAt =
                                System.currentTimeMillis();

                        bookmarkRepository.insertBookmark(
                                bookmark
                        );

                        Toast.makeText(
                                requireContext(),
                                "Bookmark saved.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    private void displayPdfBook(
            Book book
    ) {

        File pdfFile =
                new File(
                        book.filePath
                );

        if (!pdfFile.exists()) {

            readerText.setText(
                    "The PDF file could not be found."
            );

            return;
        }

        readerText.setText(
                "PDF reader is being loaded..."
        );

        pdfReaderBridge.openPdf(
                pdfFile,
                new PdfReaderBridge.Callback() {

                    @Override
                    public void onSuccess(
                            FragmentFactory factory
                    ) {

                        if (!isAdded()) {
                            return;
                        }

                        getChildFragmentManager()
                                .setFragmentFactory(
                                        factory
                                );

                        getChildFragmentManager()
                                .beginTransaction()
                                .replace(
                                        R.id.readerContainer,
                                        org.readium.r2.navigator.pdf.PdfNavigatorFragment.class,
                                        null,
                                        "pdf_navigator"
                                )
                                .commit();

                        readerText.setVisibility(
                                View.GONE
                        );
                    }

                    @Override
                    public void onError(
                            String message
                    ) {

                        if (!isAdded()) {
                            return;
                        }

                        readerText.setVisibility(
                                View.VISIBLE
                        );

                        readerText.setText(
                                "Could not open PDF:\n\n"
                                        + message
                        );
                    }
                }
        );
    }

    private String readTextFile(
            File file
    ) throws IOException {

        StringBuilder content =
                new StringBuilder();


        try (
                BufferedReader reader =
                        new BufferedReader(
                                new FileReader(file)
                        )
        ) {

            String line;


            while (
                    (line = reader.readLine())
                            != null
            ) {

                content.append(
                        line
                );

                content.append(
                        "\n"
                );
            }
        }


        return content.toString();
    }
}