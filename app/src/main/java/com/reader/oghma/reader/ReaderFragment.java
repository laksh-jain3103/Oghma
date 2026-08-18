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
import com.reader.oghma.data.entity.Bookmark;
import com.reader.oghma.data.entity.Progress;
import com.reader.oghma.data.repository.BookRepository;
import com.reader.oghma.data.repository.BookmarkRepository;
import com.reader.oghma.data.repository.ProgressRepository;

import org.readium.r2.navigator.epub.EpubNavigatorFragment;
import org.readium.r2.navigator.pdf.PdfNavigatorFragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;


public class ReaderFragment extends Fragment {

    // =========================================================
    // Views
    // =========================================================

    private TextView readerText;
    private TextView readerTitle;

    private ImageButton backButton;

    private FrameLayout readerContainer;


    // =========================================================
    // Repositories
    // =========================================================

    private BookRepository repository;

    private BookmarkRepository bookmarkRepository;

    private ProgressRepository progressRepository;


    // =========================================================
    // Reader bridges
    // =========================================================

    private EpubReaderBridge epubReaderBridge;

    private PdfReaderBridge pdfReaderBridge;


    // =========================================================
    // Active navigators
    // =========================================================

    private EpubNavigatorFragment epubNavigator;

    private PdfNavigatorFragment pdfNavigator;


    // =========================================================
    // Reading mode
    //
    // true  = horizontal / paginated
    // false = vertical / scrolling
    // =========================================================

    private boolean horizontalReadingMode =
            true;


    // =========================================================
    // Current book
    // =========================================================

    private int currentBookId =
            -1;


    /*
     * If the reader was opened from a bookmark,
     * this contains the saved bookmark locator.
     *
     * Bookmark location takes priority over normal
     * reading progress when opening the book.
     */
    private String bookmarkLocator;


    // =========================================================
    // Constructor
    // =========================================================

    public ReaderFragment() {
        // Required empty constructor
    }


    // =========================================================
    // Create view
    // =========================================================

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


        ImageButton readingModeButton =
                view.findViewById(
                        R.id.readingModeButton
                );


        readingModeButton.setOnClickListener(
                v ->
                        switchReadingMode()
        );


        // -----------------------------------------------------
        // Create reader bridges
        // -----------------------------------------------------

        epubReaderBridge =
                new EpubReaderBridge(
                        requireContext()
                );


        pdfReaderBridge =
                new PdfReaderBridge(
                        requireContext()
                );


        // -----------------------------------------------------
        // Create repositories
        // -----------------------------------------------------

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


        progressRepository =
                new ProgressRepository(
                        requireActivity()
                                .getApplication()
                );


        // -----------------------------------------------------
        // Back button
        // -----------------------------------------------------

        backButton.setOnClickListener(
                v -> {

                    requireActivity()
                            .getSupportFragmentManager()
                            .popBackStack();
                }
        );


        // -----------------------------------------------------
        // Load selected book
        // -----------------------------------------------------

        loadBook();
    }


    // =========================================================
    // Save progress when leaving the reader
    // =========================================================

    @Override
    public void onPause() {

        saveProgress();

        super.onPause();
    }


    // =========================================================
    // SWITCH READING MODE
    //
    // Works for both EPUB and PDF.
    //
    // The current locator is captured first so changing
    // the presentation does not throw the reader back
    // to the beginning.
    // =========================================================

    private void switchReadingMode() {

        // -----------------------------------------------------
        // EPUB
        // -----------------------------------------------------

        if (epubNavigator != null) {

            epubReaderBridge.getCurrentLocator(
                    epubNavigator,
                    locatorJson -> {

                        if (
                                locatorJson == null
                                        || locatorJson.trim().isEmpty()
                        ) {

                            Toast.makeText(
                                    requireContext(),
                                    "Could not save the current reading position.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }


                        horizontalReadingMode =
                                !horizontalReadingMode;


                        readerText.setVisibility(
                                View.VISIBLE
                        );


                        readerText.setText(
                                horizontalReadingMode
                                        ? "Switching to horizontal mode..."
                                        : "Switching to vertical mode..."
                        );


                        reopenEpubAtLocator(
                                locatorJson
                        );
                    }
            );

            return;
        }


        // -----------------------------------------------------
        // PDF
        // -----------------------------------------------------

        if (pdfNavigator != null) {

            pdfReaderBridge.getCurrentLocator(
                    pdfNavigator,
                    locatorJson -> {

                        if (
                                locatorJson == null
                                        || locatorJson.trim().isEmpty()
                        ) {

                            Toast.makeText(
                                    requireContext(),
                                    "Could not save the current reading position.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }


                        horizontalReadingMode =
                                !horizontalReadingMode;


                        readerText.setVisibility(
                                View.VISIBLE
                        );


                        readerText.setText(
                                horizontalReadingMode
                                        ? "Switching to horizontal mode..."
                                        : "Switching to vertical mode..."
                        );


                        reopenPdfAtLocator(
                                locatorJson
                        );
                    }
            );

            return;
        }


        Toast.makeText(
                requireContext(),
                "Reader is not ready.",
                Toast.LENGTH_SHORT
        ).show();
    }


    // =========================================================
    // REOPEN EPUB AT A SAVED LOCATOR
    // =========================================================

    private void reopenEpubAtLocator(
            String locatorJson
    ) {

        if (currentBookId == -1) {
            return;
        }


        repository.getBook(
                currentBookId,
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


                    File epubFile =
                            new File(
                                    book.filePath
                            );


                    if (!epubFile.exists()) {

                        if (!isAdded()) {
                            return;
                        }


                        requireActivity().runOnUiThread(
                                () ->
                                        readerText.setText(
                                                "The EPUB file could not be found."
                                        )
                        );

                        return;
                    }


                    requireActivity().runOnUiThread(
                            () ->
                                    openEpub(
                                            epubFile,
                                            locatorJson
                                    )
                    );
                }
        );
    }


    // =========================================================
    // REOPEN PDF AT A SAVED LOCATOR
    // =========================================================

    private void reopenPdfAtLocator(
            String locatorJson
    ) {

        if (currentBookId == -1) {
            return;
        }


        repository.getBook(
                currentBookId,
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


                    File pdfFile =
                            new File(
                                    book.filePath
                            );


                    if (!pdfFile.exists()) {

                        if (!isAdded()) {
                            return;
                        }


                        requireActivity().runOnUiThread(
                                () ->
                                        readerText.setText(
                                                "The PDF file could not be found."
                                        )
                        );

                        return;
                    }


                    requireActivity().runOnUiThread(
                            () ->
                                    openPdf(
                                            pdfFile,
                                            locatorJson
                                    )
                    );
                }
        );
    }


    // =========================================================
    // LOAD BOOK
    // =========================================================

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


        /*
         * If the Library opened this reader from a bookmark,
         * this contains the bookmark's locator.
         */
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


    // =========================================================
    // DISPLAY BOOK
    // =========================================================

    private void displayBook(
            Book book
    ) {

        readerTitle.setText(
                book.title
        );


        // -----------------------------------------------------
        // TXT
        // -----------------------------------------------------

        if (
                "txt".equalsIgnoreCase(
                        book.format
                )
        ) {

            displayTxtBook(
                    book
            );

            return;
        }


        // -----------------------------------------------------
        // EPUB
        // -----------------------------------------------------

        if (
                "epub".equalsIgnoreCase(
                        book.format
                )
        ) {

            displayEpubBook(
                    book
            );

            return;
        }


        // -----------------------------------------------------
        // PDF
        // -----------------------------------------------------

        if (
                "pdf".equalsIgnoreCase(
                        book.format
                )
        ) {

            displayPdfBook(
                    book
            );

            return;
        }


        readerText.setText(
                "This format is not supported yet."
        );
    }


    // =========================================================
    // TXT
    // =========================================================

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
                    readTextFile(
                            file
                    );


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


    // =========================================================
    // EPUB
    // =========================================================

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


        loadProgressAndOpenEpub(
                epubFile
        );
    }


    // =========================================================
    // LOAD EPUB PROGRESS
    //
    // Bookmark location takes priority.
    // Otherwise use the last saved reading position.
    // =========================================================

    private void loadProgressAndOpenEpub(
            File epubFile
    ) {

        if (
                bookmarkLocator != null
                        && !bookmarkLocator.trim().isEmpty()
        ) {

            openEpub(
                    epubFile,
                    bookmarkLocator
            );

            return;
        }


        progressRepository.getProgress(
                currentBookId,
                progress -> {

                    if (!isAdded()) {
                        return;
                    }


                    String progressLocator =
                            null;


                    if (progress != null) {

                        progressLocator =
                                progress.locator;
                    }


                    String finalLocator =
                            progressLocator;


                    requireActivity().runOnUiThread(
                            () ->
                                    openEpub(
                                            epubFile,
                                            finalLocator
                                    )
                    );
                }
        );
    }


    // =========================================================
    // OPEN EPUB
    // =========================================================

    private void openEpub(
            File epubFile,
            String initialLocatorJson
    ) {

        epubReaderBridge.openEpub(
                epubFile,
                initialLocatorJson,
                horizontalReadingMode,
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


                        /*
                         * Clear the PDF reference because
                         * EPUB is now the active navigator.
                         */
                        pdfNavigator =
                                null;


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
                                "Could not open EPUB:\n\n"
                                        + message
                        );
                    }
                }
        );
    }


    // =========================================================
    // BOOKMARK
    // =========================================================

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


    // =========================================================
    // SAVE READING PROGRESS
    //
    // EPUB progress is currently saved here.
    // PDF progress will be added separately once the PDF
    // navigator is confirmed working with its locator.
    // =========================================================

    private void saveProgress() {

        if (
                epubNavigator == null
                        || currentBookId == -1
        ) {

            return;
        }


        epubReaderBridge.getCurrentLocator(
                epubNavigator,
                locatorJson -> {

                    if (
                            locatorJson == null
                                    || locatorJson.trim().isEmpty()
                    ) {

                        return;
                    }


                    Progress progress =
                            new Progress();


                    progress.bookId =
                            currentBookId;


                    progress.locator =
                            locatorJson;


                    progress.percentage =
                            0f;


                    progressRepository.saveProgress(
                            progress
                    );
                }
        );
    }


    // =========================================================
    // PDF
    // =========================================================

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


        readerText.setVisibility(
                View.VISIBLE
        );


        readerText.setText(
                "PDF reader is being loaded..."
        );


        openPdf(
                pdfFile,
                null
        );
    }


    // =========================================================
    // OPEN PDF
    //
    // initialLocatorJson can be null when opening normally,
    // or contain a saved locator when switching modes.
    // =========================================================

    private void openPdf(
            File pdfFile,
            String initialLocatorJson
    ) {

        pdfReaderBridge.openPdf(
                pdfFile,
                initialLocatorJson,
                horizontalReadingMode,
                new PdfReaderBridge.Callback() {

                    @Override
                    public void onSuccess(
                            FragmentFactory factory
                    ) {

                        if (!isAdded()) {
                            return;
                        }


                        /*
                         * Clear the EPUB reference because
                         * PDF is now the active navigator.
                         */
                        epubNavigator =
                                null;


                        getChildFragmentManager()
                                .setFragmentFactory(
                                        factory
                                );


                        getChildFragmentManager()
                                .beginTransaction()
                                .replace(
                                        R.id.readerContainer,
                                        PdfNavigatorFragment.class,
                                        null,
                                        "pdf_navigator"
                                )
                                .commit();


                        getChildFragmentManager()
                                .executePendingTransactions();


                        pdfNavigator =
                                (PdfNavigatorFragment)
                                        getChildFragmentManager()
                                                .findFragmentByTag(
                                                        "pdf_navigator"
                                                );


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


    // =========================================================
    // READ TXT FILE
    // =========================================================

    private String readTextFile(
            File file
    ) throws IOException {

        StringBuilder content =
                new StringBuilder();


        try (
                BufferedReader reader =
                        new BufferedReader(
                                new FileReader(
                                        file
                                )
                        )
        ) {

            String line;


            while (
                    (line =
                            reader.readLine())
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