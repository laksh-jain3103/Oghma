package com.reader.oghma.api;

import com.reader.oghma.api.model.GoogleBooksResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public interface GoogleBooksApi {

    @GET("volumes")
    Call<GoogleBooksResponse> searchBooks(
            @Query("q") String query,
            @Query("key") String apiKey,
            @Header("X-Android-Package") String packageName,
            @Header("X-Android-Cert") String certificate
    );
}