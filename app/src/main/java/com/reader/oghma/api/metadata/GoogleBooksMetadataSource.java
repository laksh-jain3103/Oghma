package com.reader.oghma.api.metadata;

import com.reader.oghma.api.GoogleBooksClient;
import com.reader.oghma.api.model.GoogleBookInfo;
import com.reader.oghma.api.model.GoogleBookItem;
import com.reader.oghma.api.model.GoogleBooksResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Response;

public class GoogleBooksMetadataSource {

    public BookMetadata search(String title, List<String> authors, String isbn, String apiKey, String packageName, String cert) throws IOException {
        String query;
        if (isbn != null && !isbn.trim().isEmpty()) {
            query = "isbn:" + isbn.trim();
        } else {
            StringBuilder sb = new StringBuilder();
            if (title != null && !title.trim().isEmpty()) {
                String primaryTitle = title.trim();
                int colonIdx = primaryTitle.indexOf(':');
                if (colonIdx != -1) primaryTitle = primaryTitle.substring(0, colonIdx).trim();
                int semiIdx = primaryTitle.indexOf(';');
                if (semiIdx != -1) primaryTitle = primaryTitle.substring(0, semiIdx).trim();
                int orIdx = primaryTitle.toLowerCase(Locale.ROOT).indexOf(" or, ");
                if (orIdx != -1) primaryTitle = primaryTitle.substring(0, orIdx).trim();

                sb.append("intitle:\"").append(primaryTitle).append("\"");
            }
            if (authors != null && !authors.isEmpty() && authors.get(0) != null) {
                String firstAuthor = authors.get(0).trim();
                String surname = BookMatcher.extractSurname(firstAuthor);
                if (!surname.isEmpty()) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append("inauthor:\"").append(surname).append("\"");
                }
            }
            query = sb.toString();
        }

        if (query.trim().isEmpty()) {
            return null;
        }

        Response<GoogleBooksResponse> response = GoogleBooksClient.getApi()
                .searchBooks(query, apiKey, packageName, cert, 10, 0)
                .execute();

        int code = response.code();
        if (code == 403 || code == 429 || code >= 500) {
            // Rate limit, quota or server trouble: not "no such book". Let the worker retry.
            throw new IOException("Metadata service returned HTTP " + code);
        }

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
