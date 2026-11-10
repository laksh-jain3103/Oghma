package com.reader.oghma.api;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class GutendexClient {
    private static final String BASE_URL = "https://gutendex.com/";

    private static final Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build();

    private static final GutendexApi api = retrofit.create(GutendexApi.class);

    public static GutendexApi getApi() {
        return api;
    }
}
