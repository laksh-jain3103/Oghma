package com.reader.oghma.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;
import retrofit2.http.Streaming;
import retrofit2.http.Url;

public interface ProjectGutenbergApi {

    @GET("ebooks/search.opds/")
    Call<ResponseBody> searchBooks(
            @Query("query") String query
    );

    @Streaming
    @GET
    Call<ResponseBody> downloadEpub(
            @Url String url,
            @Header("Referer") String referer
    );
}
