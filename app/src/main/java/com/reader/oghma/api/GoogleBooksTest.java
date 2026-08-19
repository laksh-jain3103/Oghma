package com.reader.oghma.api;

import android.util.Log;

import com.reader.oghma.BuildConfig;
import com.reader.oghma.api.model.GoogleBookItem;
import com.reader.oghma.api.model.GoogleBooksResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GoogleBooksTest {

    public static void testSearch() {

        GoogleBooksClient.getApi()
                .searchBooks(
                        "Harry Potter",
                        BuildConfig.GOOGLE_BOOKS_API_KEY,
                        BuildConfig.APPLICATION_ID,
                        BuildConfig.GOOGLE_BOOKS_CERT
                )
                .enqueue(new Callback<GoogleBooksResponse>() {

                    @Override
                    public void onResponse(
                            Call<GoogleBooksResponse> call,
                            Response<GoogleBooksResponse> response
                    ) {

                        if (!response.isSuccessful()) {
                            Log.e(
                                    "GoogleBooksTest",
                                    "HTTP error: " + response.code()
                            );
                            return;
                        }

                        GoogleBooksResponse result =
                                response.body();

                        if (result == null) {
                            Log.e(
                                    "GoogleBooksTest",
                                    "Response body is null"
                            );
                            return;
                        }

                        Log.d(
                                "GoogleBooksTest",
                                "Total results: "
                                        + result.totalItems
                        );

                        if (result.items != null
                                && !result.items.isEmpty()) {

                            GoogleBookItem firstBook =
                                    result.items.get(0);

                            Log.d(
                                    "GoogleBooksTest",
                                    "First result ID: "
                                            + firstBook.id
                            );

                            if (firstBook.volumeInfo != null) {

                                Log.d(
                                        "GoogleBooksTest",
                                        "Title: "
                                                + firstBook.volumeInfo.title
                                );

                                Log.d(
                                        "GoogleBooksTest",
                                        "Authors: "
                                                + firstBook.volumeInfo.authors
                                );
                            }
                        }
                    }

                    @Override
                    public void onFailure(
                            Call<GoogleBooksResponse> call,
                            Throwable t
                    ) {

                        Log.e(
                                "GoogleBooksTest",
                                "Request failed",
                                t
                        );
                    }
                });
    }
}