package com.reader.oghma.api.metadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;

public class OpenLibraryMetadataSource {

    public BookMetadata search(String title, List<String> authors, String isbn) throws IOException {
        String query = null;
        String queryTitle = null;
        String queryAuthor = null;
        String queryIsbn = null;

        if (isbn != null && !isbn.trim().isEmpty()) {
            queryIsbn = isbn.trim();
        } else {
            if (title != null && !title.trim().isEmpty()) {
                queryTitle = title.trim();
            }
            if (authors != null && !authors.isEmpty() && authors.get(0) != null) {
                queryAuthor = authors.get(0).trim();
            }
        }

        if (queryIsbn == null && queryTitle == null && queryAuthor == null) {
            return null;
        }

        Response<OpenLibraryResponse> response = OpenLibraryClient.getApi()
                .search(query, queryTitle, queryAuthor, queryIsbn, "key,title,author_name,subject,isbn", 5)
                .execute();

        int code = response.code();
        if (code == 403 || code == 429 || code >= 500) {
            // Rate limit, quota or server trouble: not "no such book". Let the worker retry.
            throw new IOException("Metadata service returned HTTP " + code);
        }

        if (!response.isSuccessful() || response.body() == null || response.body().docs == null) {
            return null;
        }

        double bestScore = 0.0;
        OpenLibraryDoc bestDoc = null;

        for (OpenLibraryDoc doc : response.body().docs) {
            if (doc == null) continue;
            double score = BookMatcher.calculateScore(title, authors, isbn, doc.title, doc.authorName, doc.isbn);
            if (score > bestScore) {
                bestScore = score;
                bestDoc = doc;
            }
        }

        if (bestDoc != null && bestScore >= BookMatcher.CONFIDENCE_THRESHOLD) {
            List<String> rawTags = bestDoc.subject != null ? new ArrayList<>(bestDoc.subject) : new ArrayList<>();
            String matchedIsbn = (bestDoc.isbn != null && !bestDoc.isbn.isEmpty()) ? bestDoc.isbn.get(0) : isbn;
            return new BookMetadata(bestDoc.title, bestDoc.authorName, matchedIsbn, rawTags, "OpenLibrary");
        }

        return null;
    }
}
