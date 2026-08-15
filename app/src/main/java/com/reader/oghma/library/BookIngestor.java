package com.reader.oghma.library;

import android.content.Context;

import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.repository.BookRepository;
import com.reader.oghma.reader.EpubReaderBridge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

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
                        new File(
                                booksDirectory,
                                fileName
                        );

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
                        removeExtension(
                                fileName
                        );

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

                    extractEpubCover(
                            destination,
                            book
                    );

                } else {

                    // PDFs and TXT files don't
                    // have cover generation yet.

                    book.coverPath = "";

                    repository.insertBook(
                            book
                    );
                }


            } catch (IOException e) {

                e.printStackTrace();

            } catch (Exception e) {

                e.printStackTrace();
            }
        });
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