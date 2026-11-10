package com.reader.oghma.api.metadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BookMetadataService {

    public enum Status {
        SUCCESS,
        NOT_FOUND,
        NETWORK_ERROR
    }

    public static class Result {
        public final Status status;
        public final List<String> tags;

        public Result(Status status, List<String> tags) {
            this.status = status;
            this.tags = tags != null ? tags : new ArrayList<>();
        }
    }

    private final GoogleBooksMetadataSource googleSource = new GoogleBooksMetadataSource();
    private final OpenLibraryMetadataSource openLibrarySource = new OpenLibraryMetadataSource();

    public Result fetchAndNormalizeTags(String title, List<String> authors, String isbn, String apiKey, String packageName, String cert) {
        List<String> combinedRawTags = new ArrayList<>();
        boolean networkError = false;
        boolean foundAny = false;

        // 1. Google Books
        try {
            BookMetadata gMeta = googleSource.search(title, authors, isbn, apiKey, packageName, cert);
            if (gMeta != null && gMeta.rawTags != null && !gMeta.rawTags.isEmpty()) {
                combinedRawTags.addAll(gMeta.rawTags);
                foundAny = true;
            }
        } catch (IOException e) {
            networkError = true;
        }

        // 2. Open Library
        try {
            BookMetadata olMeta = openLibrarySource.search(title, authors, isbn);
            if (olMeta != null && olMeta.rawTags != null && !olMeta.rawTags.isEmpty()) {
                combinedRawTags.addAll(olMeta.rawTags);
                foundAny = true;
            }
        } catch (IOException e) {
            networkError = true;
        }

        if (combinedRawTags.isEmpty()) {
            if (networkError && !foundAny) {
                return new Result(Status.NETWORK_ERROR, null);
            }
            return new Result(Status.NOT_FOUND, null);
        }

        List<String> normalizedTags = TagNormalizer.map(combinedRawTags);
        if (normalizedTags.isEmpty()) {
            return new Result(Status.NOT_FOUND, null);
        }

        return new Result(Status.SUCCESS, normalizedTags);
    }
}
