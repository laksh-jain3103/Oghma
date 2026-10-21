package com.reader.oghma.reader;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.view.Gravity;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
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
import com.reader.oghma.data.repository.ProgressRepository;
import com.reader.oghma.data.entity.Bookmark;
import com.reader.oghma.data.entity.Progress;
import com.reader.oghma.settings.SettingsFragment;
import org.readium.r2.navigator.epub.EpubNavigatorFragment;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class ReaderFragment extends Fragment {

    private TextView readerText;
    private TextView readerTitle;
    private ImageButton backButton;

    // Read aloud (text-to-speech)
    private TextView ttsButton;

    private View ttsBar;

    private ImageButton ttsPlayPause;

    private TextView ttsSpeedLabel;

    private EpubTtsController epubTts;

    // Dictionary popup (one at a time)
    private DictionaryPopup dictionaryPopup;

    private TranslatePopup translatePopup;

    // True while one popup replaces another, so the new word's mark is not wiped.
    private boolean dictionaryReplacing = false;

    // Book progress bar (follows reading, and the voice while reading aloud)
    private View progressFooter;

    private SeekBar progressSeek;

    private TextView progressLabel;

    private TextView progressPercent;

    private boolean progressDragging = false;

    private boolean ttsActive = false;

    private boolean ttsPlaying = false;

    private double ttsSpeed = 1.0;
    private FrameLayout readerContainer;

    private BookRepository repository;
    private BookmarkRepository bookmarkRepository;
    private EpubReaderBridge epubReaderBridge;

    private ProgressRepository progressRepository;

    private PdfReaderView pdfReaderView;

    private EpubNavigatorFragment epubNavigator;

    // EPUB text scale, 1.0 = 100%. Remembered between books and sessions.
    private double epubFontSize = 1.0;

    private TextView epubZoomLabel;

    // Reading progress is written at most once per second.
    private final Handler progressHandler =
            new Handler(Looper.getMainLooper());

    private final Runnable progressSaver =
            this::flushProgress;

    private String pendingProgressLocator;

    private float pendingProgressPercentage;

    private boolean hasPendingProgress = false;

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

        ttsButton =
                view.findViewById(
                        R.id.ttsButton
                );

        ttsBar =
                view.findViewById(
                        R.id.ttsBar
                );

        ttsPlayPause =
                view.findViewById(
                        R.id.ttsPlayPause
                );

        ttsSpeedLabel =
                view.findViewById(
                        R.id.ttsSpeed
                );

        progressFooter =
                view.findViewById(
                        R.id.progressFooter
                );

        progressSeek =
                view.findViewById(
                        R.id.progressSeek
                );

        progressLabel =
                view.findViewById(
                        R.id.progressLabel
                );

        progressPercent =
                view.findViewById(
                        R.id.progressPercent
                );

        setupProgressBar();

        setupTtsControls(
                view
        );


        readerContainer =
                view.findViewById(
                        R.id.readerContainer
                );


        epubReaderBridge =
                new EpubReaderBridge(
                        requireContext()
                );

        progressRepository =
                new ProgressRepository(
                        requireActivity()
                                .getApplication()
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

            // Nothing to follow along with in a plain text box.
            ttsButton.setVisibility(
                    View.GONE
            );

            progressFooter.setVisibility(
                    View.GONE
            );

            displayTxtBook(book);

            return;
        }


        if (
                "epub".equalsIgnoreCase(
                        book.format
                )
        ) {

            ttsButton.setVisibility(
                    View.VISIBLE
            );

            displayEpubBook(book);

            return;
        }

        if (
                "pdf".equalsIgnoreCase(
                        book.format
                )
        ) {

            ttsButton.setVisibility(
                    View.VISIBLE
            );

            displayPdfBook(book);

            return;
        }

        ttsButton.setVisibility(
                View.GONE
        );

        progressFooter.setVisibility(
                View.GONE
        );

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


        // Where to open: the tapped highlight wins, otherwise the last reading position.
        progressRepository.get(
                currentBookId,
                progress -> {

                    String initialLocator =
                            bookmarkLocator != null
                                    ? bookmarkLocator
                                    : (progress != null
                                    ? progress.locator
                                    : null);

                    runOnUi(() ->
                            openEpub(
                                    epubFile,
                                    initialLocator
                            )
                    );
                }
        );
    }


    private void openEpub(
            File epubFile,
            String initialLocatorJson
    ) {

        epubFontSize =
                ReaderPrefs.getEpubFontSize(
                        requireContext()
                );

        epubReaderBridge.openEpub(
                epubFile,
                initialLocatorJson,
                epubFontSize,
                new EpubReaderBridge.Callback() {

                    @Override
                    public void onBookmarkRequested() {

                        handleBookmarkRequest();
                    }


                    @Override
                    public void onCopyRequested() {

                        handleCopyRequest();
                    }


                    @Override
                    public void onPlayRequested() {

                        handlePlayRequest();
                    }


                    @Override
                    public void onDictionaryRequested() {

                        handleDictionaryRequest();
                    }


                    @Override
                    public void onTranslateRequested() {

                        handleTranslateRequest();
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


                        readerText.setVisibility(
                                View.GONE
                        );


                        if (epubNavigator != null) {

                            epubReaderBridge.observeProgress(
                                    epubNavigator,
                                    (locatorJson, percentage) -> {

                                        scheduleProgressSave(
                                                locatorJson,
                                                percentage
                                        );

                                        // While the voice is speaking, it drives the bar.
                                        if (!ttsPlaying) {

                                            updateProgress(
                                                    percentage / 100.0,
                                                    chapterOf(locatorJson)
                                            );
                                        }
                                    }
                            );

                            refreshEpubHighlights();

                            setupEpubZoom();
                        }
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
    // EPUB text size (A- / A+ buttons and pinch)
    // =========================================================

    private void setupEpubZoom() {

        if (!(readerContainer instanceof PinchZoomLayout)) {
            return;
        }

        PinchZoomLayout pinchLayout =
                (PinchZoomLayout) readerContainer;

        // Small pill in the bottom corner: A-  100%  A+
        LinearLayout bar =
                new LinearLayout(
                        requireContext()
                );

        bar.setOrientation(
                LinearLayout.HORIZONTAL
        );

        bar.setGravity(
                Gravity.CENTER_VERTICAL
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(0xE6313244);

        background.setCornerRadius(dp(24));

        bar.setBackground(background);

        bar.setPadding(
                dp(4),
                dp(2),
                dp(4),
                dp(2)
        );

        bar.setElevation(dp(6));

        TextView minus =
                zoomButton("A\u2212");

        TextView plus =
                zoomButton("A+");

        epubZoomLabel =
                new TextView(
                        requireContext()
                );

        epubZoomLabel.setTextColor(0xFFCDD6F4);

        epubZoomLabel.setTextSize(13);

        epubZoomLabel.setGravity(Gravity.CENTER);

        epubZoomLabel.setMinWidth(dp(48));

        updateEpubZoomLabel(epubFontSize);

        minus.setOnClickListener(v ->
                applyEpubFontSize(epubFontSize - 0.1)
        );

        plus.setOnClickListener(v ->
                applyEpubFontSize(epubFontSize + 0.1)
        );

        // Tap the percentage to go back to 100%.
        epubZoomLabel.setOnClickListener(v ->
                applyEpubFontSize(1.0)
        );

        bar.addView(minus);
        bar.addView(epubZoomLabel);
        bar.addView(plus);

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM | Gravity.END
                );

        params.setMargins(
                0,
                0,
                dp(16),
                dp(16)
        );

        readerContainer.addView(
                bar,
                params
        );

        // Pinch: preview the percentage while pinching, resize when the fingers lift.
        pinchLayout.setListener(
                new PinchZoomLayout.Listener() {

                    @Override
                    public void onScale(
                            float factor
                    ) {

                        updateEpubZoomLabel(
                                clampFontSize(
                                        epubFontSize * factor
                                )
                        );
                    }

                    @Override
                    public void onScaleEnd(
                            float factor
                    ) {

                        applyEpubFontSize(
                                epubFontSize * factor
                        );
                    }
                }
        );
    }


    private TextView zoomButton(
            String label
    ) {

        TextView button =
                new TextView(
                        requireContext()
                );

        button.setText(label);

        button.setTextColor(0xFFCBA6F7);

        button.setTextSize(17);

        button.setTypeface(
                button.getTypeface(),
                android.graphics.Typeface.BOLD
        );

        button.setGravity(Gravity.CENTER);

        button.setMinWidth(dp(48));

        button.setMinHeight(dp(44));

        button.setContentDescription(
                label.startsWith("A+")
                        ? "Larger text"
                        : "Smaller text"
        );

        return button;
    }


    private double clampFontSize(
            double size
    ) {

        // Steps of 5%.
        double snapped =
                Math.round(size * 20.0) / 20.0;

        return Math.max(
                ReaderPrefs.MIN_FONT_SIZE,
                Math.min(
                        ReaderPrefs.MAX_FONT_SIZE,
                        snapped
                )
        );
    }


    private void applyEpubFontSize(
            double requested
    ) {

        double size =
                clampFontSize(
                        requested
                );

        updateEpubZoomLabel(size);

        if (epubNavigator == null || size == epubFontSize) {
            return;
        }

        epubFontSize = size;

        ReaderPrefs.setEpubFontSize(
                requireContext(),
                size
        );

        epubReaderBridge.setFontSize(
                epubNavigator,
                size
        );
    }


    private void updateEpubZoomLabel(
            double size
    ) {

        if (epubZoomLabel != null) {

            epubZoomLabel.setText(
                    Math.round(size * 100) + "%"
            );
        }
    }


    private int dp(
            int value
    ) {

        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }


    /** Loads this book's saved highlights and draws them in the EPUB. */
    private void refreshEpubHighlights() {

        bookmarkRepository.getBookmarksForBook(
                currentBookId,
                bookmarks ->
                        runOnUi(() -> {

                            if (epubNavigator != null) {

                                epubReaderBridge.applyHighlights(
                                        epubNavigator,
                                        bookmarks
                                );
                            }
                        })
        );
    }


    /** "Highlight" from the EPUB selection menu: ask for colour + note, then save. */
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

                        if (!isAdded()) {
                            return;
                        }

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
                                    "Could not save the highlight location.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }

                        HighlightDialog.show(
                                requireContext(),
                                selectedText,
                                HighlightColors.getLast(
                                        requireContext()
                                ),
                                null,
                                false,
                                new HighlightDialog.Listener() {

                                    @Override
                                    public void onSave(
                                            int color,
                                            String note
                                    ) {

                                        saveHighlight(
                                                selectedText,
                                                locatorJson,
                                                chapterTitle != null
                                                        ? chapterTitle
                                                        : "",
                                                color,
                                                note
                                        );

                                        clearEpubSelection();

                                        refreshEpubHighlights();
                                    }

                                    @Override
                                    public void onCopy() {

                                        HighlightDialog.copyToClipboard(
                                                requireContext(),
                                                selectedText
                                        );

                                        clearEpubSelection();
                                    }

                                    @Override
                                    public void onCancel() {

                                        clearEpubSelection();
                                    }
                                }
                        );
                    }
                }
        );
    }


    /** "Copy" from the EPUB selection menu. */
    private void handleCopyRequest() {

        if (epubNavigator == null) {
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

                        if (!isAdded()) {
                            return;
                        }

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

                        HighlightDialog.copyToClipboard(
                                requireContext(),
                                selectedText
                        );

                        clearEpubSelection();
                    }
                }
        );
    }


    private void clearEpubSelection() {

        if (epubNavigator != null) {

            epubReaderBridge.clearSelection(
                    epubNavigator
            );
        }
    }


    /** Saves a highlight (EPUB or PDF) with its colour and optional note. */
    private void saveHighlight(
            String selectedText,
            String locatorJson,
            String chapterTitle,
            int color,
            String note
    ) {

        Bookmark bookmark =
                new Bookmark();

        bookmark.bookId =
                currentBookId;

        bookmark.selectedText =
                selectedText;

        bookmark.locatorJson =
                locatorJson;

        bookmark.chapterTitle =
                chapterTitle;

        bookmark.createdAt =
                System.currentTimeMillis();

        bookmark.color =
                color;

        bookmark.note =
                note != null && !note.trim().isEmpty()
                        ? note.trim()
                        : null;

        bookmarkRepository.insertBookmark(
                bookmark
        );

        HighlightColors.setLast(
                requireContext(),
                color
        );

        Toast.makeText(
                requireContext(),
                bookmark.note != null
                        ? "Note saved."
                        : "Highlight saved.",
                Toast.LENGTH_SHORT
        ).show();
    }


    // =========================================================
    // PDF (PDF.js in a WebView, so the text is selectable)
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

        readerText.setText(
                "PDF reader is being loaded..."
        );

        progressRepository.get(
                currentBookId,
                progress ->
                        bookmarkRepository.getBookmarksForBook(
                                currentBookId,
                                bookmarks ->
                                        runOnUi(() ->
                                                openPdf(
                                                        pdfFile,
                                                        progress,
                                                        bookmarks
                                                )
                                        )
                        )
        );
    }


    private void openPdf(
            File pdfFile,
            Progress progress,
            List<Bookmark> bookmarks
    ) {

        // Start position: tapped highlight, else last reading position, else page 1.
        JSONObject start =
                parsePdfLocator(bookmarkLocator);

        if (start == null && progress != null) {

            start =
                    parsePdfLocator(progress.locator);
        }

        int startPage =
                start != null
                        ? start.optInt("page", 1)
                        : 1;

        float startFraction =
                start != null
                        ? (float) start.optDouble("y", 0.0)
                        : 0f;

        // Only PDF locators can be drawn by the PDF viewer.
        List<String> highlights =
                new ArrayList<>();

        for (Bookmark bookmark : bookmarks) {

            JSONObject locator =
                    parsePdfLocator(bookmark.locatorJson);

            if (locator != null) {

                try {

                    // The colour lives in its own column, so the viewer gets it alongside.
                    locator.put(
                            "color",
                            HighlightColors.toHex(
                                    bookmark.color
                            )
                    );

                    highlights.add(
                            locator.toString()
                    );

                } catch (Exception ignored) {
                }
            }
        }

        pdfReaderView =
                new PdfReaderView(
                        requireContext()
                );

        readerContainer.addView(
                pdfReaderView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        pdfReaderView.open(
                pdfFile,
                startPage,
                startFraction,
                highlights,
                new PdfReaderView.Listener() {

                    @Override
                    public void onReady(
                            int pageCount
                    ) {

                        if (isAdded()) {

                            readerText.setVisibility(
                                    View.GONE
                            );
                        }
                    }


                    @Override
                    public void onProgress(
                            int page,
                            float pageFraction,
                            int pageCount
                    ) {

                        if (pageCount <= 0) {
                            return;
                        }

                        String locator =
                                "{\"type\":\"pdf\",\"page\":"
                                        + page
                                        + ",\"y\":"
                                        + pageFraction
                                        + "}";

                        float percentage =
                                ((page - 1) + pageFraction)
                                        / pageCount
                                        * 100f;

                        scheduleProgressSave(
                                locator,
                                percentage
                        );

                        updateProgress(
                                percentage / 100.0,
                                "Page " + page + " of " + pageCount
                        );
                    }


                    @Override
                    public void onHighlight(
                            String locatorJson,
                            String text,
                            int page,
                            String colorHex,
                            boolean withNote
                    ) {

                        if (!isAdded()) {
                            return;
                        }

                        // Quick colour dot: the viewer already drew it, just save.
                        if (!withNote) {

                            saveHighlight(
                                    text,
                                    locatorJson,
                                    "Page " + page,
                                    parseColor(colorHex),
                                    null
                            );

                            return;
                        }

                        // "Note" button: ask for colour + note first.
                        HighlightDialog.show(
                                requireContext(),
                                text,
                                HighlightColors.getLast(
                                        requireContext()
                                ),
                                null,
                                false,
                                new HighlightDialog.Listener() {

                                    @Override
                                    public void onSave(
                                            int color,
                                            String note
                                    ) {

                                        saveHighlight(
                                                text,
                                                locatorJson,
                                                "Page " + page,
                                                color,
                                                note
                                        );

                                        if (pdfReaderView != null) {

                                            pdfReaderView.addHighlight(
                                                    locatorJson,
                                                    HighlightColors.toHex(
                                                            color
                                                    )
                                            );
                                        }
                                    }

                                    @Override
                                    public void onCopy() {

                                        HighlightDialog.copyToClipboard(
                                                requireContext(),
                                                text
                                        );
                                    }
                                }
                        );
                    }


                    @Override
                    public void onTtsState(
                            boolean active,
                            boolean playing
                    ) {

                        if (!isAdded()) {
                            return;
                        }

                        ttsActive =
                                active;

                        updateTtsUi(
                                active,
                                playing
                        );
                    }


                    @Override
                    public void onTtsError(
                            String message
                    ) {

                        if (!isAdded()) {
                            return;
                        }

                        Toast.makeText(
                                requireContext(),
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }


                    @Override
                    public void onTtsRequested() {

                        if (isAdded() && !ttsActive) {

                            startTts(null);
                        }
                    }


                    @Override
                    public void onTranslateRequested(
                            String text,
                            double topFraction,
                            double bottomFraction
                    ) {

                        if (!isAdded()) {
                            return;
                        }

                        float height =
                                readerContainer.getHeight();

                        showTranslation(
                                text,
                                topFraction < 0
                                        ? Float.NaN
                                        : (float) (topFraction * height),
                                bottomFraction < 0
                                        ? Float.NaN
                                        : (float) (bottomFraction * height)
                        );
                    }


                    @Override
                    public void onDefineRequested(
                            String text,
                            double topFraction,
                            double bottomFraction
                    ) {

                        if (!isAdded()) {
                            return;
                        }

                        float height =
                                readerContainer.getHeight();

                        // Negative = the viewer could not tell where the word is.
                        showDictionary(
                                text,
                                topFraction < 0
                                        ? Float.NaN
                                        : (float) (topFraction * height),
                                bottomFraction < 0
                                        ? Float.NaN
                                        : (float) (bottomFraction * height)
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

                        readerText.bringToFront();

                        readerText.setText(
                                "Could not open PDF:\n\n"
                                        + message
                        );
                    }
                }
        );
    }


    private int parseColor(
            String hex
    ) {

        try {

            return android.graphics.Color.parseColor(
                    hex
            );

        } catch (Exception e) {

            return HighlightColors.YELLOW;
        }
    }


    /** Returns the parsed locator if [json] is a PDF locator, otherwise null. */
    private JSONObject parsePdfLocator(
            String json
    ) {

        if (json == null || json.trim().isEmpty()) {
            return null;
        }

        try {

            JSONObject object =
                    new JSONObject(json);

            return "pdf".equals(
                    object.optString("type")
            )
                    ? object
                    : null;

        } catch (Exception e) {

            return null;
        }
    }


    // =========================================================
    // Book progress bar
    // =========================================================

    private void setupProgressBar() {

        progressSeek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {

                        if (fromUser) {

                            // Preview the percentage while dragging.
                            progressPercent.setText(
                                    Math.round(progress / 10f) + "%"
                            );
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(
                            SeekBar seekBar
                    ) {

                        progressDragging = true;
                    }

                    @Override
                    public void onStopTrackingTouch(
                            SeekBar seekBar
                    ) {

                        progressDragging = false;

                        seekToFraction(
                                seekBar.getProgress() / 1000.0
                        );
                    }
                }
        );
    }


    /** [fraction] is 0.0 (start of the book) to 1.0 (end). */
    private void updateProgress(
            double fraction,
            String label
    ) {

        if (progressDragging || progressSeek == null) {
            return;
        }

        double clamped =
                Math.max(0.0, Math.min(1.0, fraction));

        progressSeek.setProgress(
                (int) Math.round(clamped * 1000)
        );

        progressPercent.setText(
                Math.round(clamped * 100) + "%"
        );

        if (label != null) {

            progressLabel.setText(label);
        }
    }


    /** Jumps to a point in the book. If the voice is on, it carries on from there. */
    private void seekToFraction(
            double fraction
    ) {

        if (epubNavigator != null) {

            epubReaderBridge.seekToProgression(
                    epubNavigator,
                    fraction,
                    locatorJson -> {

                        if (epubTts != null) {

                            // Keep it playing if it was playing, paused if it was paused.
                            epubTts.seekTo(
                                    locatorJson,
                                    epubTts.isPlaying()
                            );
                        }
                    }
            );

        } else if (pdfReaderView != null) {

            pdfReaderView.seekTo(fraction);
        }
    }


    /** The chapter title stored in an EPUB locator, or "" if there is none. */
    private String chapterOf(
            String locatorJson
    ) {

        try {

            return new JSONObject(locatorJson)
                    .optString("title", "");

        } catch (Exception e) {

            return "";
        }
    }


    // =========================================================
    // Dictionary
    // =========================================================

    /** "Dictionary" from the EPUB selection menu. */
    private void handleDictionaryRequest() {

        if (epubNavigator == null) {
            return;
        }

        epubReaderBridge.getSelectionForLookup(
                epubNavigator,
                new EpubReaderBridge.LookupListener() {

                    @Override
                    public void onLookup(
                            String text,
                            String locatorJson,
                            float top,
                            float bottom
                    ) {

                        if (!isAdded()) {
                            return;
                        }

                        if (text == null || text.trim().isEmpty()) {

                            Toast.makeText(
                                    requireContext(),
                                    "No text selected.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }

                        // Underline the word so it is easy to spot behind the popup.
                        epubReaderBridge.showLookupMark(
                                epubNavigator,
                                locatorJson
                        );

                        showDictionary(
                                text,
                                top,
                                bottom
                        );
                    }
                }
        );
    }


    /**
     * Shows the dictionary card in the half of the reader away from the word
     * (top / bottom are the word's position in pixels, or NaN if unknown).
     */
    private void showDictionary(
            String text,
            float top,
            float bottom
    ) {

        dismissLookupPopups();

        final DictionaryPopup popup =
                new DictionaryPopup(
                        requireContext()
                );

        dictionaryPopup =
                popup;

        popup.show(
                readerContainer,
                text,
                top,
                bottom,
                new DictionaryPopup.Listener() {

                    @Override
                    public void onDismissed() {

                        if (dictionaryPopup == popup) {

                            dictionaryPopup =
                                    null;
                        }

                        if (!dictionaryReplacing) {

                            clearLookupMarks();
                        }
                    }
                }
        );
    }


    /** "Translate" from the EPUB selection menu. */
    private void handleTranslateRequest() {

        if (epubNavigator == null) {
            return;
        }

        epubReaderBridge.getSelectionForLookup(
                epubNavigator,
                new EpubReaderBridge.LookupListener() {

                    @Override
                    public void onLookup(
                            String text,
                            String locatorJson,
                            float top,
                            float bottom
                    ) {

                        if (!isAdded()) {
                            return;
                        }

                        if (text == null || text.trim().isEmpty()) {

                            Toast.makeText(
                                    requireContext(),
                                    "No text selected.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }

                        epubReaderBridge.showLookupMark(
                                epubNavigator,
                                locatorJson
                        );

                        showTranslation(
                                text,
                                top,
                                bottom
                        );
                    }
                }
        );
    }


    /** Shows the translation card. First time with no languages chosen, it points to Settings. */
    private void showTranslation(
            String text,
            float top,
            float bottom
    ) {

        if (!TranslationPrefs.hasTargets(requireContext())) {

            askToChooseLanguages();

            return;
        }

        dismissLookupPopups();

        final TranslatePopup popup =
                new TranslatePopup(
                        requireContext()
                );

        translatePopup =
                popup;

        popup.show(
                readerContainer,
                text,
                top,
                bottom,
                new MlKitTranslationEngine(),
                TranslationPrefs.getTargets(requireContext()),
                new TranslatePopup.Listener() {

                    @Override
                    public void onDismissed() {

                        if (translatePopup == popup) {

                            translatePopup =
                                    null;
                        }

                        if (!dictionaryReplacing) {

                            clearLookupMarks();
                        }
                    }

                    @Override
                    public void onOpenSettings() {

                        popup.dismiss();

                        openSettings();
                    }
                }
        );
    }


    /** Nothing chosen yet: say so, and take the reader to Settings to pick languages. */
    private void askToChooseLanguages() {

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Choose your languages")
                .setMessage(
                        "Hey! To translate, first pick the language(s) you want text "
                                + "translated into in Settings.\n\n"
                                + "You can also download language packs there, so "
                                + "translating works offline."
                )
                .setPositiveButton(
                        "Open Settings",
                        (dialog, which) -> openSettings()
                )
                .setNegativeButton(
                        "Not now",
                        null
                )
                .setOnDismissListener(dialog -> clearLookupMarks())
                .show();
    }


    private void openSettings() {

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.fragmentContainer,
                        new SettingsFragment()
                )
                .addToBackStack(null)
                .commit();
    }


    /** Closes the dictionary and translation cards, keeping the mark on the next word. */
    private void dismissLookupPopups() {

        // Replacing a card: keep the mark that was just placed on the new text.
        dictionaryReplacing = true;

        if (dictionaryPopup != null) {

            dictionaryPopup.dismiss();
        }

        if (translatePopup != null) {

            translatePopup.dismiss();
        }

        if (translatePopup != null) {

            translatePopup.dismiss();
        }

        dictionaryReplacing = false;
    }


    /** Removes the underline and the selection left on the word that was looked up. */
    private void clearLookupMarks() {

        if (epubNavigator != null) {

            epubReaderBridge.clearLookupMark(
                    epubNavigator
            );

            clearEpubSelection();
        }

        if (pdfReaderView != null) {

            pdfReaderView.clearLookup();
        }
    }


    /** "Play" from the EPUB selection menu: read aloud starting at the selected text. */
    private void handlePlayRequest() {

        if (epubNavigator == null) {
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

                        if (!isAdded()) {
                            return;
                        }

                        if (
                                locatorJson == null
                                        || locatorJson.trim().isEmpty()
                        ) {

                            Toast.makeText(
                                    requireContext(),
                                    "No text selected.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }

                        if (epubTts != null) {

                            // Already reading (or paused): jump to the selection and play.
                            epubTts.seekTo(locatorJson, true);

                        } else {

                            startTts(locatorJson);
                        }

                        clearEpubSelection();
                    }
                }
        );
    }


    // =========================================================
    // Read aloud
    // =========================================================

    private void setupTtsControls(
            View root
    ) {

        ttsSpeed =
                ReaderPrefs.getTtsSpeed(
                        requireContext()
                );

        updateSpeedLabel();

        ttsButton.setOnClickListener(v -> {

            if (ttsActive) {

                stopTts();

            } else {

                startTts(null);
            }
        });

        ttsPlayPause.setOnClickListener(v -> {

            if (epubTts != null) {

                epubTts.togglePlayPause();

            } else if (pdfReaderView != null) {

                if (ttsPlaying) {
                    pdfReaderView.pauseTts();
                } else {
                    pdfReaderView.resumeTts();
                }
            }
        });

        root.findViewById(R.id.ttsNext).setOnClickListener(v -> {

            if (epubTts != null) {
                epubTts.next();
            } else if (pdfReaderView != null) {
                pdfReaderView.nextTts();
            }
        });

        root.findViewById(R.id.ttsPrevious).setOnClickListener(v -> {

            if (epubTts != null) {
                epubTts.previous();
            } else if (pdfReaderView != null) {
                pdfReaderView.previousTts();
            }
        });

        root.findViewById(R.id.ttsStop).setOnClickListener(v ->
                stopTts()
        );

        // Choose a different voice (applies right away while reading).
        root.findViewById(R.id.ttsVoice).setOnClickListener(v ->
                VoicePickerDialog.show(
                        requireContext(),
                        ReaderPrefs.getTtsVoice(requireContext()),
                        new VoicePickerDialog.Listener() {

                            @Override
                            public void onVoiceChosen(
                                    String voiceName,
                                    String languageTag
                            ) {

                                if (!isAdded()) {
                                    return;
                                }

                                ReaderPrefs.setTtsVoice(
                                        requireContext(),
                                        voiceName,
                                        languageTag
                                );

                                if (epubTts != null) {

                                    epubTts.setVoice(
                                            voiceName,
                                            languageTag
                                    );

                                } else if (pdfReaderView != null) {

                                    pdfReaderView.setTtsVoice(
                                            voiceName
                                    );
                                }
                            }
                        }
                )
        );

        // Tap the speed to step through 0.75x, 1x, 1.25x, 1.5x, 2x.
        ttsSpeedLabel.setOnClickListener(v -> {

            double[] speeds =
                    ReaderPrefs.TTS_SPEEDS;

            int next = 0;

            for (int i = 0; i < speeds.length; i++) {

                if (Math.abs(speeds[i] - ttsSpeed) < 0.01) {

                    next = (i + 1) % speeds.length;

                    break;
                }
            }

            ttsSpeed =
                    speeds[next];

            ReaderPrefs.setTtsSpeed(
                    requireContext(),
                    ttsSpeed
            );

            updateSpeedLabel();

            if (epubTts != null) {

                epubTts.setSpeed(
                        ttsSpeed
                );

            } else if (pdfReaderView != null) {

                pdfReaderView.setTtsSpeed(
                        ttsSpeed
                );
            }
        });
    }


    /** @param startLocatorJson EPUB only: where to start (selected text), or null for "here". */
    private void startTts(String startLocatorJson) {

        // ---------- EPUB ----------
        if (epubNavigator != null) {

            if (epubReaderBridge.getCurrentPublication() == null) {

                Toast.makeText(
                        requireContext(),
                        "The book is still loading.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            epubTts =
                    new EpubTtsController(
                            requireActivity().getApplication(),
                            epubReaderBridge.getCurrentPublication(),
                            epubNavigator
                    );

            ttsActive =
                    true;

            updateTtsUi(
                    true,
                    true
            );

            epubTts.start(
                    ttsSpeed,
                    ReaderPrefs.getTtsVoice(requireContext()),
                    ReaderPrefs.getTtsVoiceLanguage(requireContext()),
                    startLocatorJson,
                    new EpubTtsController.Listener() {

                        @Override
                        public void onPlayingChanged(
                                boolean playing
                        ) {

                            if (isAdded()) {

                                updateTtsUi(
                                        true,
                                        playing
                                );
                            }
                        }

                        @Override
                        public void onSpokenProgress(
                                double fraction
                        ) {

                            if (isAdded()) {

                                updateProgress(
                                        fraction,
                                        "Reading aloud"
                                );
                            }
                        }

                        @Override
                        public void onStopped() {

                            epubTts =
                                    null;

                            ttsActive =
                                    false;

                            if (isAdded()) {

                                updateTtsUi(
                                        false,
                                        false
                                );
                            }
                        }

                        @Override
                        public void onError(
                                String message
                        ) {

                            if (isAdded()) {

                                Toast.makeText(
                                        requireContext(),
                                        message,
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }
                    }
            );

            return;
        }

        // ---------- PDF ----------
        if (pdfReaderView != null) {

            ttsActive =
                    true;

            updateTtsUi(
                    true,
                    true
            );

            pdfReaderView.startTts(
                    ttsSpeed,
                    ReaderPrefs.getTtsVoice(requireContext())
            );

            return;
        }

        Toast.makeText(
                requireContext(),
                "The book is still loading.",
                Toast.LENGTH_SHORT
        ).show();
    }


    private void stopTts() {

        ttsActive =
                false;

        if (epubTts != null) {

            epubTts.stop();

            epubTts =
                    null;
        }

        if (pdfReaderView != null) {

            pdfReaderView.stopTts();
        }

        updateTtsUi(
                false,
                false
        );
    }


    private void updateTtsUi(
            boolean active,
            boolean playing
    ) {

        ttsPlaying =
                playing;

        ttsBar.setVisibility(
                active
                        ? View.VISIBLE
                        : View.GONE
        );

        ttsPlayPause.setImageResource(
                playing
                        ? android.R.drawable.ic_media_pause
                        : android.R.drawable.ic_media_play
        );

        ttsPlayPause.setContentDescription(
                playing
                        ? "Pause"
                        : "Play"
        );

        // The header button becomes "Stop" while reading aloud.
        ttsButton.setText(
                active
                        ? "Stop"
                        : "Listen"
        );

        ttsButton.setContentDescription(
                active
                        ? "Stop reading aloud"
                        : "Read aloud"
        );
    }


    private void updateSpeedLabel() {

        String label =
                (ttsSpeed == Math.rint(ttsSpeed)
                        ? String.valueOf((int) ttsSpeed)
                        : String.valueOf(ttsSpeed))
                        + "x";

        // 1.0 -> "1x", 1.25 -> "1.25x"
        if (ttsSpeed == 1.0) {
            label = "1x";
        }

        ttsSpeedLabel.setText(
                label
        );
    }


    // =========================================================
    // Progress + lifecycle
    // =========================================================

    private void scheduleProgressSave(
            String locator,
            float percentage
    ) {

        pendingProgressLocator =
                locator;

        pendingProgressPercentage =
                percentage;

        hasPendingProgress =
                true;

        progressHandler.removeCallbacks(
                progressSaver
        );

        progressHandler.postDelayed(
                progressSaver,
                1000
        );
    }


    private void flushProgress() {

        progressHandler.removeCallbacks(
                progressSaver
        );

        if (
                hasPendingProgress
                        && currentBookId != -1
                        && progressRepository != null
        ) {

            progressRepository.save(
                    currentBookId,
                    pendingProgressLocator,
                    pendingProgressPercentage
            );
        }

        hasPendingProgress =
                false;
    }


    private void runOnUi(
            Runnable action
    ) {

        if (!isAdded() || getActivity() == null) {
            return;
        }

        getActivity().runOnUiThread(
                action
        );
    }


    @Override
    public void onPause() {

        super.onPause();

        if (pdfReaderView != null) {

            pdfReaderView.onPause();
        }

        flushProgress();
    }


    @Override
    public void onResume() {

        super.onResume();

        if (pdfReaderView != null) {

            pdfReaderView.onResume();
        }
    }


    @Override
    public void onDestroyView() {

        if (dictionaryPopup != null) {

            dictionaryPopup.dismiss();
        }

        if (epubTts != null) {

            epubTts.stop();

            epubTts =
                    null;
        }

        flushProgress();

        if (pdfReaderView != null) {

            pdfReaderView.destroy();

            pdfReaderView = null;
        }

        epubNavigator = null;

        if (epubReaderBridge != null) {

            epubReaderBridge.close();
        }

        super.onDestroyView();
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