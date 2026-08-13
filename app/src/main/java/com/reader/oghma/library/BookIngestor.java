package com.reader.oghma.library;

import android.content.Context;

import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.repository.BookRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BookIngestor {

    private final Context context;
    private final BookRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public BookIngestor(Context context, BookRepository repository) {
        this.context = context.getApplicationContext();
        this.repository = repository;
    }

    public void importBook(
            InputStream inputStream,
            String fileName,
            String format) {

        executor.execute(() -> {
            try {

                File booksDirectory =
                        new File(context.getFilesDir(), "books");

                if (!booksDirectory.exists()) {
                    booksDirectory.mkdirs();
                }

                File destination =
                        new File(booksDirectory, fileName);

                copyFile(inputStream, destination);

                Book book = new Book();

                book.title = removeExtension(fileName);
                book.author = "Unknown author";
                book.filePath = destination.getAbsolutePath();
                book.coverPath = "";
                book.format = format;
                book.addedAt = System.currentTimeMillis();

                repository.insertBook(book);

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void copyFile(
            InputStream inputStream,
            File destination)
            throws IOException {

        try (
                InputStream input = inputStream;
                FileOutputStream output =
                        new FileOutputStream(destination)
        ) {

            byte[] buffer = new byte[8192];

            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }
    }

    private String removeExtension(String fileName) {

        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }

        return fileName;
    }
}