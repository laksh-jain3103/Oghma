package com.reader.oghma.api;

import com.reader.oghma.api.model.GutendexResponse;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import retrofit2.http.Streaming;
import retrofit2.http.Url;

public interface GutendexApi {

    @GET("books/")
    Call<GutendexResponse> searchBooks(
            @Query("search") String query,
            @Query("copyright") String copyright,
            @Query("mime_type") String mimeType,
            @Query("page") int page
    );

    @GET("books/")
    Call<GutendexResponse> byIds(
            @Query("ids") String ids
    );

    @Streaming
    @GET
    Call<ResponseBody> downloadEpub(@Url String url);
}
