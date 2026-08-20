package com.reader.oghma.api;

import com.reader.oghma.api.model.GoogleBooksResponse;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;
import retrofit2.http.Streaming;
import retrofit2.http.Url;

public interface GoogleBooksApi {

    @GET("volumes")
    Call<GoogleBooksResponse> searchBooks(
            @Query("q") String query,
            @Query("key") String apiKey,
            @Header("X-Android-Package") String packageName,
            @Header("X-Android-Cert") String certificate,
            @Query("filter") String filter,
            @Query("download") String download
    );

    @Streaming
    @GET
    Call<ResponseBody> downloadBook(@Url String url);
}