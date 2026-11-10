package com.reader.oghma.api.metadata;

import com.reader.oghma.api.GoogleBooksClient;
import com.reader.oghma.api.model.GoogleBookInfo;
import com.reader.oghma.api.model.GoogleBookItem;
import com.reader.oghma.api.model.GoogleBooksResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;

public class GoogleBooksMetadataSource {

    public BookMetadata search(String title, List<String> authors, String isbn, String apiKey, String packageName, String cert) throws IOException {
        String query;
        if (isbn != null && !isbn.trim().isEmpty()) {
            query = "isbn:" + isbn.trim();
        } else {
            StringBuilder sb = new StringBuilder();
            if (title != null && !title.trim().isEmpty()) {
                sb.append("intitle:").append(title.trim());
            }
            if (authors != null && !authors.isEmpty() && authors.get(0) != null) {
                if (sb.length() > 0) sb.append(" ");
                sb.append("inauthor:").append(authors.get(0).trim());
            }
            query = sb.toString();
        }

        if (query.trim().isEmpty()) {
            return null;
        }

        Response<GoogleBooksResponse> response = GoogleBooksClient.getApi()
                .searchBooks(query, apiKey, packageName, cert, null, null)
                .execute();

        if (!response.isSuccessful() || response.body() == null || response.body().items == null) {
            return null;
        }

        double bestScore = 0.0;
        GoogleBookItem bestItem = null;

        for (GoogleBookItem item : response.body().items) {
            if (item == null || item.volumeInfo == null) continue;
            GoogleBookInfo info = item.volumeInfo;
            List<String> candIsbns = extractIsbns(info);

            double score = BookMatcher.calculateScore(title, authors, isbn, info.title, info.authors, candIsbns);
            if (score > bestScore) {
                bestScore = score;
                bestItem = item;
            }
        }

        if (bestItem != null && bestScore >= BookMatcher.CONFIDENCE_THRESHOLD) {
            GoogleBookInfo info = bestItem.volumeInfo;
            List<String> rawTags = new ArrayList<>();
            if (info.categories != null) {
                for (String cat : info.categories) {
                    if (cat != null) {
                        String[] parts = cat.split("/");
                        for (String part : parts) {
                            String trimmed = part.trim();
                            if (!trimmed.isEmpty()) {
                                rawTags.add(trimmed);
                            }
                        }
                    }
                }
            }
            return new BookMetadata(info.title, info.authors, extractFirstIsbn(info), rawTags, "GoogleBooks");
        }

        return null;
    }

    private List<String> extractIsbns(GoogleBookInfo info) {
        List<String> list = new ArrayList<>();
        if (info.industryIdentifiers != null) {
            for (GoogleBookInfo.IndustryIdentifier id : info.industryIdentifiers) {
                if (id != null && id.identifier != null) {
                    list.add(id.identifier);
                }
            }
        }
        return list;
    }

    private String extractFirstIsbn(GoogleBookInfo info) {
        List<String> isbns = extractIsbns(info);
        return isbns.isEmpty() ? null : isbns.get(0);
    }
}
