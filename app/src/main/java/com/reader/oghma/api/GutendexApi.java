package com.reader.oghma.api;

import com.reader.oghma.api.model.GutendexResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface GutendexApi {
    @GET("books")
    Call<GutendexResponse> searchBooks(@Query("search") String query);
}
