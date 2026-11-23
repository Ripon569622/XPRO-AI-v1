package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OpenAiChatMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiChatMessage>,
    val temperature: Double = 0.7
)

@JsonClass(generateAdapter = true)
data class OpenAiChatChoice(
    val index: Int = 0,
    val message: OpenAiChatMessage? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiChatResponse(
    val id: String? = null,
    val choices: List<OpenAiChatChoice> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OpenAiImageRequest(
    val model: String = "dall-e-3",
    val prompt: String,
    val n: Int = 1,
    val size: String = "1024x1024",
    @Json(name = "response_format") val responseFormat: String = "b64_json"
)

@JsonClass(generateAdapter = true)
data class OpenAiImageData(
    @Json(name = "b64_json") val b64Json: String? = null,
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiImageResponse(
    val data: List<OpenAiImageData> = emptyList()
)

interface OpenAiApi {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: OpenAiChatRequest
    ): OpenAiChatResponse

    @POST("v1/images/generations")
    suspend fun generateImage(
        @Header("Authorization") authorization: String,
        @Body request: OpenAiImageRequest
    ): OpenAiImageResponse
}

object OpenAiClient {
    private const val BASE_URL = "https://api.openai.com/"
    private const val PLACEHOLDER_KEY = "MY_OPENAI_API_KEY"

    fun isConfigured(): Boolean {
        val key = BuildConfig.OPENAI_API_KEY
        return key.isNotBlank() && key != PLACEHOLDER_KEY
    }

    fun bearerHeader(): String = "Bearer ${BuildConfig.OPENAI_API_KEY.trim()}"

    val api: OpenAiApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenAiApi::class.java)
    }
}
