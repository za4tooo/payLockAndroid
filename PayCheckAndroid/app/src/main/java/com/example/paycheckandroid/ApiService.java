package com.example.paycheckandroid;

import retrofit2.Call;
import retrofit2.http.GET;

public interface ApiService {
    @GET("api/TestLink/google-link")
    Call<LinkResponse> getLink();
}