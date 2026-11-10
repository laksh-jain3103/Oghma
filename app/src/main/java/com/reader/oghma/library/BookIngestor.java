package com.reader.oghma.library;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import com.reader.oghma.api.metadata.FilenameParser;
import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.repository.BookRepository;
import com.reader.oghma.reader.EpubReaderBridge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BookIngestor {

    private final Context context;
    private final BookRepository repository;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final EpubReaderBridge epubReaderBridge;


    public BookIngestor(
            Context context,
            BookRepository repository
    ) {

        this.context =
                context.getApplicationContext();

        this.repository =
                repository;

        this.epubReaderBridge =
                new EpubReaderBridge(
                        this.context
                );
    }


    public void importBook(
            InputStream inputStream,
            String fileName,
            String format
    ) {

        executor.execute(() -> {

            try {

                // -----------------------------
                // 1. Create books directory
                // -----------------------------

                File booksDirectory =
                        new File(
                                context.getFilesDir(),
                                "books"
                        );

                if (!booksDirectory.exists()) {
                    booksDirectory.mkdirs();
                }


                // -----------------------------
                // 2. Copy the book
                // -----------------------------

                File destination =
                        new File(booksDirectory, new File(fileName).getName());

                copyFile(
                        inputStream,
                        destination
                );


                // -----------------------------
                // 3. Create Book
                // -----------------------------

                Book book =
                        new Book();

                book.title = FilenameParser.cleanTitle(fileName);

                book.author =
                        "Unknown author";

                book.filePath =
                        destination.getAbsolutePath();

                book.format =
                        format;

                book.addedAt =
                        System.currentTimeMillis();


                // -----------------------------
                // 4. Handle EPUB cover
                // -----------------------------

                if (
                        "epub".equalsIgnoreCase(
                                format
                        )
                ) {

                    extractEpubDetails(
                            destination,
                            book,
                            null
                    );

                } else if (
                        "pdf".equalsIgnoreCase(
                                format
                        )
                ) {

                    File generatedThumbnail =
                            generatePdfThumbnail(
                                    destination
                            );

                    if (
                            generatedThumbnail != null
                                    && generatedThumbnail.exists()
                    ) {

                        book.coverPath =
                                generatedThumbnail
                                        .getAbsolutePath();

                    } else {

                        book.coverPath =
                                "";
                    }

                    insertAndTag(book, null);

                } else {

                    // TXT files do not
                    // have a thumbnail.

                    book.coverPath = "";

                    insertAndTag(book, null);
                }


            } catch (IOException e) {

                e.printStackTrace();

            } catch (Exception e) {

                e.printStackTrace();
            }
        });
    }

    public void importDownloadedBook(
            InputStream inputStream,
            String fileName,
            String format,
            String title,
            String author
    ) {
        importDownloadedBook(inputStream, fileName, format, title, author, null);
    }

    public void importDownloadedBook(
            InputStream inputStream,
            String fileName,
            String format,
            String title,
            String author,
            List<String> categories
    ) {

        executor.execute(() -> {

            try {

                // -----------------------------
                // 1. Create books directory
                // -----------------------------

                File booksDirectory =
                        new File(
                                context.getFilesDir(),
                                "books"
                        );

                if (!booksDirectory.exists()) {
                    booksDirectory.mkdirs();
                }


                // -----------------------------
                // 2. Copy downloaded book
                // -----------------------------

                File destination =
                        new File(booksDirectory, new File(fileName).getName());

                copyFile(
                        inputStream,
                        destination
                );


                // -----------------------------
                // 3. Create Book
                // -----------------------------

                Book book =
                        new Book();

                book.title =
                        title != null
                                && !title.trim().isEmpty()
                                ? title
                                : FilenameParser.cleanTitle(fileName);

                book.author =
                        author != null
                                && !author.trim().isEmpty()
                                ? author
                                : "Unknown author";

                book.filePath =
                        destination.getAbsolutePath();

                book.format =
                        format;

                book.addedAt =
                        System.currentTimeMillis();


                // -----------------------------
                // 4. Handle cover
                // -----------------------------

                if (
                        "epub".equalsIgnoreCase(
                                format
                        )
                ) {

                    extractEpubDetails(
                            destination,
                            book,
                            categories
                    );

                } else if (
                        "pdf".equalsIgnoreCase(
                                format
                        )
                ) {

                    File generatedThumbnail =
                            generatePdfThumbnail(
                                    destination
                            );

                    if (
                            generatedThumbnail != null
                                    && generatedThumbnail.exists()
                    ) {

                        book.coverPath =
                                generatedThumbnail
                                        .getAbsolutePath();

                    } else {

                        book.coverPath =
                                "";
                    }

                    insertAndTag(book, categories);

                } else {

                    book.coverPath = "";

                    insertAndTag(book, categories);
                }


            } catch (IOException e) {

                e.printStackTrace();

            } catch (Exception e) {

                e.printStackTrace();
            }
        });
    }

    private File generatePdfThumbnail(
            File pdfFile
    ) {

        ParcelFileDescriptor fileDescriptor =
                null;

        PdfRenderer renderer =
                null;

        PdfRenderer.Page page =
                null;

        Bitmap bitmap =
                null;

        try {

            // -----------------------------------------
            // Create covers directory
            // -----------------------------------------

            File coversDirectory =
                    new File(
                            context.getFilesDir(),
                            "covers"
                    );

            if (!coversDirectory.exists()) {

                if (!coversDirectory.mkdirs()
                        && !coversDirectory.exists()) {

                    System.out.println(
                            "PDF THUMBNAIL: Could not create covers directory"
                    );

                    return null;
                }
            }


            // -----------------------------------------
            // Create thumbnail filename
            // -----------------------------------------

            String thumbnailFileName =
                    removeExtension(
                            pdfFile.getName()
                    ) + "_cover.jpg";


            File thumbnailFile =
                    new File(
                            coversDirectory,
                            thumbnailFileName
                    );


            System.out.println(
                    "PDF THUMBNAIL: Starting extraction"
            );


            // -----------------------------------------
            // Open PDF
            // -----------------------------------------

            fileDescriptor =
                    ParcelFileDescriptor.open(
                            pdfFile,
                            ParcelFileDescriptor.MODE_READ_ONLY
                    );

            renderer =
                    new PdfRenderer(
                            fileDescriptor
                    );


            if (renderer.getPageCount() == 0) {

                System.out.println(
                        "PDF THUMBNAIL: PDF has no pages"
                );

                return null;
            }


            // -----------------------------------------
            // Open first page
            // -----------------------------------------

            page =
                    renderer.openPage(0);


            // -----------------------------------------
            // Calculate thumbnail size
            //
            // Keep the page's aspect ratio.
            // -----------------------------------------

            int targetWidth =
                    600;

            int targetHeight =
                    Math.round(
                            page.getHeight()
                                    * (
                                    targetWidth
                                            / (float) page.getWidth()
                            )
                    );

            if (targetHeight > 900) {

                float scale =
                        900f
                                / targetHeight;

                targetWidth =
                        Math.round(
                                targetWidth
                                        * scale
                        );

                targetHeight =
                        900;
            }


            // -----------------------------------------
            // Create bitmap
            // -----------------------------------------

            bitmap =
                    Bitmap.createBitmap(
                            targetWidth,
                            targetHeight,
                            Bitmap.Config.ARGB_8888
                    );


            // -----------------------------------------
            // Give PDF page a white background
            // -----------------------------------------

            Canvas canvas =
                    new Canvas(
                            bitmap
                    );

            canvas.drawColor(
                    Color.WHITE
            );


            // -----------------------------------------
            // Render page into bitmap
            // -----------------------------------------

            page.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            );


            // -----------------------------------------
            // Save JPEG
            // -----------------------------------------

            try (
                    FileOutputStream output =
                            new FileOutputStream(
                                    thumbnailFile
                            )
            ) {

                bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        90,
                        output
                );
            }


            System.out.println(
                    "PDF THUMBNAIL CREATED: "
                            + thumbnailFile
                            .getAbsolutePath()
            );

            System.out.println(
                    "PDF THUMBNAIL EXISTS: "
                            + thumbnailFile.exists()
            );


            return thumbnailFile;


        } catch (Exception e) {

            e.printStackTrace();

            System.out.println(
                    "PDF THUMBNAIL ERROR: "
                            + e.getMessage()
            );

            return null;


        } finally {

            // -----------------------------------------
            // Close everything
            // -----------------------------------------

            if (page != null) {

                page.close();
            }

            if (renderer != null) {

                renderer.close();
            }

            if (fileDescriptor != null) {

                try {

                    fileDescriptor.close();

                } catch (IOException e) {

                    e.printStackTrace();
                }
            }

            if (bitmap != null) {

                bitmap.recycle();
            }
        }
    }

    /**
     * Inserts the book and starts tagging. Must run on [executor] (or another background
     * thread): Room refuses database access on the main thread.
     */
    private void insertAndTag(Book book, List<String> categories) {
        long id = repository.insertBookSync(book);
        int bookId = (int) id;

        if (categories != null && !categories.isEmpty()) {
            List<String> tags = com.reader.oghma.api.metadata.TagNormalizer.map(categories);
            if (!tags.isEmpty()) {
                repository.replaceAutoTagsSync(bookId, tags);
                repository.updateTagStatusSync(bookId, "DONE", 0);
                return;
            }
        }

        TagBookWorker.enqueue(context, bookId, false);
    }

    private void insertAndTagSafely(Book book, List<String> categories) {
        // Readium calls back on the main thread. Hop to the background executor first.
        executor.execute(() -> {
            try {
                insertAndTag(book, categories);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void extractEpubDetails(
            File epubFile,
            Book book,
            List<String> categories
    ) {
        File coversDirectory = new File(context.getFilesDir(), "covers");
        if (!coversDirectory.exists()) {
            coversDirectory.mkdirs();
        }

        String coverFileName = removeExtension(epubFile.getName()) + "_cover.jpg";
        File coverFile = new File(coversDirectory, coverFileName);

        epubReaderBridge.extractEpubDetails(
                epubFile,
                coverFile,
                new EpubReaderBridge.DetailsCallback() {
                    @Override
                    public void onSuccess(File generatedCover, String parsedTitle, String parsedAuthor, String parsedIdentifier) {
                        // Many downloaded EPUBs carry a junk title ("Unknown", "book1", the
                        // file name again). Only trust it when it is not obviously junk.
                        if (isUsableTitle(parsedTitle)) {
                            book.title = parsedTitle.trim();
                        }
                        if (isUsableAuthor(parsedAuthor)) {
                            book.author = parsedAuthor.trim();
                        }
                        if (generatedCover != null && generatedCover.exists()) {
                            book.coverPath = generatedCover.getAbsolutePath();
                        } else {
                            book.coverPath = "";
                        }
                        insertAndTagSafely(book, categories);
                    }

                    @Override
                    public void onError(String message) {
                        book.coverPath = "";
                        insertAndTagSafely(book, categories);
                    }
                }
        );
    }

    private static boolean isUsableTitle(String t) {
        if (t == null) return false;
        String s = t.trim().toLowerCase(java.util.Locale.ROOT);
        return !s.isEmpty() && !s.equals("unknown") && !s.equals("untitled") && !s.equals("title")
                && !s.matches("book\\s*\\d*") && !s.matches("[0-9a-f\\-]{20,}");
    }

    private static boolean isUsableAuthor(String a) {
        if (a == null) return false;
        String s = a.trim().toLowerCase(java.util.Locale.ROOT);
        return !s.isEmpty() && !s.equals("unknown") && !s.equals("unknown author")
                && !s.equals("author") && !s.equals("calibre") && !s.contains("z-lib")
                && !s.contains("libgen");
    }

    private void extractEpubCover(
            File epubFile,
            Book book
    ) {

        // -----------------------------
        // Create covers directory
        // -----------------------------

        File coversDirectory =
                new File(
                        context.getFilesDir(),
                        "covers"
                );

        if (!coversDirectory.exists()) {
            coversDirectory.mkdirs();
        }


        // -----------------------------
        // Create cover filename
        // -----------------------------

        String coverFileName =
                removeExtension(
                        epubFile.getName()
                ) + "_cover.jpg";


        File coverFile =
                new File(
                        coversDirectory,
                        coverFileName
                );


        System.out.println(
                "EPUB COVER: Starting extraction"
        );


        // -----------------------------
        // Ask Readium for the cover
        // -----------------------------

        epubReaderBridge.extractCover(
                epubFile,
                coverFile,
                new EpubReaderBridge.CoverCallback() {

                    @Override
                    public void onSuccess(
                            File generatedCover
                    ) {

                        System.out.println(
                                "EPUB COVER CREATED: "
                                        + generatedCover
                                        .getAbsolutePath()
                        );

                        System.out.println(
                                "EPUB COVER EXISTS: "
                                        + generatedCover.exists()
                        );


                        // Store the cover path
                        // in the Book.

                        book.coverPath =
                                generatedCover
                                        .getAbsolutePath();


                        System.out.println(
                                "BOOK COVER PATH: "
                                        + book.coverPath
                        );


                        // Only insert the book
                        // after the cover is ready.

                        repository.insertBook(
                                book
                        );
                    }


                    @Override
                    public void onError(
                            String message
                    ) {

                        System.out.println(
                                "EPUB COVER ERROR: "
                                        + message
                        );


                        // The EPUB is still valid,
                        // even if it has no cover.

                        book.coverPath = "";


                        repository.insertBook(
                                book
                        );
                    }
                }
        );
    }


    private void copyFile(
            InputStream inputStream,
            File destination
    ) throws IOException {

        try (
                InputStream input =
                        inputStream;

                FileOutputStream output =
                        new FileOutputStream(
                                destination
                        )
        ) {

            byte[] buffer =
                    new byte[8192];

            int bytesRead;


            while (
                    (bytesRead =
                            input.read(buffer))
                            != -1
            ) {

                output.write(
                        buffer,
                        0,
                        bytesRead
                );
            }
        }
    }


    private String removeExtension(
            String fileName
    ) {

        int dotIndex =
                fileName.lastIndexOf('.');


        if (dotIndex > 0) {

            return fileName.substring(
                    0,
                    dotIndex
            );
        }


        return fileName;
    }
}