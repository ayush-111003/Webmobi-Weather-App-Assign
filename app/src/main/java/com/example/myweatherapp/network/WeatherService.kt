package com.example.myweatherapp.network

import com.example.myweatherapp.models.CityResponse
import com.example.myweatherapp.models.WeatherResponse
import retrofit.Call
import retrofit.http.GET
import retrofit.http.Query

interface WeatherService {
    @GET("data/2.5/weather")
    fun getweather(
        @Query("lat") lat  :Double,
        @Query("lon") lon  :Double,
        @Query("units") units  :String?,
        @Query("appid") appid  :String?
    ):Call<WeatherResponse>

    @GET("geo/1.0/direct")
    fun getCityCoordinates(
        @Query("q") q  :String,
        @Query("appid") appid  :String?
    ):Call<List<CityResponse>>
}