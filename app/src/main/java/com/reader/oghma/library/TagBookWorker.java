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
import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.repository.BookRepository;

import java.util.Arrays;
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

        List<String> authors = null;
        if (book.author != null && !"Unknown author".equalsIgnoreCase(book.author.trim())) {
            authors = Arrays.asList(book.author.split("[,&]"));
        }

        String apiKey = BuildConfig.GOOGLE_BOOKS_API_KEY;
        String packageName = getApplicationContext().getPackageName();
        String cert = BuildConfig.GOOGLE_BOOKS_CERT;

        BookMetadataService service = new BookMetadataService();
        BookMetadataService.Result metadataResult = service.fetchAndNormalizeTags(
                book.title, authors, null, apiKey, packageName, cert
        );

        if (metadataResult.status == BookMetadataService.Status.SUCCESS) {
            repository.replaceAutoTags(bookId, metadataResult.tags);
            repository.updateTagStatusSync(bookId, "DONE", book.tagAttempts);
            return Result.success();
        } else if (metadataResult.status == BookMetadataService.Status.NOT_FOUND) {
            repository.updateTagStatusSync(bookId, "NOT_FOUND", book.tagAttempts);
            return Result.success();
        } else {
            int newAttempts = book.tagAttempts + 1;
            repository.updateTagStatusSync(bookId, "FAILED", newAttempts);
            if (newAttempts < 3) {
                return Result.retry();
            } else {
                return Result.failure();
            }
        }
    }
}
