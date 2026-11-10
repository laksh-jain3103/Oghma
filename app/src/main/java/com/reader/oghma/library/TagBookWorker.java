package com.reader.oghma.library;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.reader.oghma.BuildConfig;
import com.reader.oghma.api.metadata.BookMetadataService;
import com.reader.oghma.api.metadata.FilenameParser;
import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.repository.BookRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TagBookWorker extends Worker {

    public static final String WORK_NAME_PREFIX = "tag-book-";
    public static final String KEY_BOOK_ID = "book_id";
    public static final String KEY_FORCE = "force";

    public TagBookWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static void enqueue(Context context, int bookId, boolean force) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        Data inputData = new Data.Builder()
                .putInt(KEY_BOOK_ID, bookId)
                .putBoolean(KEY_FORCE, force)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(TagBookWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_PREFIX + bookId,
                ExistingWorkPolicy.KEEP,
                request
        );
    }

    @NonNull
    @Override
    public Result doWork() {
        int bookId = getInputData().getInt(KEY_BOOK_ID, -1);
        boolean force = getInputData().getBoolean(KEY_FORCE, false);

        if (bookId <= 0) {
            return Result.success();
        }

        BookRepository repository = new BookRepository((Application) getApplicationContext());
        Book book = repository.getBookSync(bookId);

        if (book == null) {
            return Result.success();
        }

        if (!force && ("DONE".equalsIgnoreCase(book.tagStatus) || "NOT_FOUND".equalsIgnoreCase(book.tagStatus))) {
            return Result.success();
        }

        // Build the searches to try, best first.
        //  - EPUBs with real metadata: title + author as stored.
        //  - Files named like downloads ("Author - Title (z-lib.org)"): dig the title and
        //    author out of the name, trying both orders, then ISBN if the name holds one.
        boolean authorKnown = book.author != null
                && !"Unknown author".equalsIgnoreCase(book.author.trim());

        String isbn = FilenameParser.extractIsbn(book.title);
        if (isbn == null && book.filePath != null) {
            isbn = FilenameParser.extractIsbn(new java.io.File(book.filePath).getName());
        }

        List<FilenameParser.Candidate> candidates = new ArrayList<>();
        if (authorKnown) {
            List<String> authors = new ArrayList<>();
            for (String a : book.author.split("[,&]")) {
                if (!a.trim().isEmpty()) authors.add(a.trim());
            }
            candidates.add(new FilenameParser.Candidate(book.title, authors));
        } else {
            candidates.addAll(FilenameParser.candidates(book.title));
        }

        BookMetadataService service = new BookMetadataService();
        String apiKey = BuildConfig.GOOGLE_BOOKS_API_KEY;
        String packageName = getApplicationContext().getPackageName();
        String cert = BuildConfig.GOOGLE_BOOKS_CERT;

        BookMetadataService.Result found = null;
        boolean networkProblem = false;

        try {
            if (isbn != null) {
                BookMetadataService.Result r = service.fetchAndNormalizeTags(
                        null, null, isbn, apiKey, packageName, cert);
                if (r.status == BookMetadataService.Status.SUCCESS) found = r;
                else if (r.status == BookMetadataService.Status.NETWORK_ERROR) networkProblem = true;
            }

            for (FilenameParser.Candidate c : candidates) {
                if (found != null) break;
                BookMetadataService.Result r = service.fetchAndNormalizeTags(
                        c.title, c.authors, null, apiKey, packageName, cert);
                if (r.status == BookMetadataService.Status.SUCCESS) found = r;
                else if (r.status == BookMetadataService.Status.NETWORK_ERROR) networkProblem = true;
            }
        } catch (Exception e) {
            // Bad JSON, unexpected response, anything else: treat as a failed attempt.
            networkProblem = true;
        }

        if (found != null) {
            repository.replaceAutoTagsSync(bookId, found.tags);

            // The file name was all we had. Replace it with the catalogue title/author.
            if (!authorKnown
                    && found.matchedTitle != null && !found.matchedTitle.trim().isEmpty()
                    && !found.matchedAuthors.isEmpty()) {
                repository.updateTitleAuthorSync(
                        bookId,
                        found.matchedTitle.trim(),
                        android.text.TextUtils.join(", ", found.matchedAuthors));
            }

            repository.updateTagStatusSync(bookId, "DONE", book.tagAttempts);
            return Result.success();
        }

        if (!networkProblem) {
            repository.updateTagStatusSync(bookId, "NOT_FOUND", book.tagAttempts);
            return Result.success();
        }

        int newAttempts = book.tagAttempts + 1;
        repository.updateTagStatusSync(bookId, "FAILED", newAttempts);
        return newAttempts < 3 ? Result.retry() : Result.failure();
    }
}
