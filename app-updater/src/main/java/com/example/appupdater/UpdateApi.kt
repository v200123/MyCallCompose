package com.example.appupdater

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

internal interface UpdateApi {
    @GET
    fun checkForUpdate(
        @Url url: String,
        @Query("packageName") packageName: String,
        @Query("versionCode") versionCode: Long
    ): Call<UpdateCheckResult>

    @Streaming
    @GET
    fun downloadApk(@Url url: String): Call<ResponseBody>
}
