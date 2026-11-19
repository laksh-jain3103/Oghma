package com.reader.oghma.api;

import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class GutendexClient {
    private static final String BASE_URL = "https://gutendex.com/";

    private static final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private static final Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build();

    private static final GutendexApi api = retrofit.create(GutendexApi.class);

    public static GutendexApi getApi() {
        return api;
    }
}
