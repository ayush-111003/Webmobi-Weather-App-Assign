package com.example.myweatherapp


import android.annotation.SuppressLint
import android.app.PendingIntent.getActivity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.Log.i
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.myweatherapp.Constants.APP_ID
import com.example.myweatherapp.Constants.BASE_URL
import com.example.myweatherapp.Constants.METRIC_UNIT
import com.example.myweatherapp.databinding.ActivityMainBinding
import com.example.myweatherapp.models.CityResponse
import com.example.myweatherapp.models.WeatherResponse
import com.example.myweatherapp.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import customProgressDialog
import retrofit.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {
    var binding: ActivityMainBinding? = null
    private var progressDialog: customProgressDialog? = null
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var mSharedPreferences :SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding?.root)

        binding?.clickMe?.setOnClickListener {
            var cSharedPreferences = getSharedPreferences("My Location", Context.MODE_PRIVATE)

            if (Constants.isNetworkAvailable(this@MainActivity)) {

                val retrofit: Retrofit = Retrofit.Builder().baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                val service: WeatherService =
                    retrofit.create<WeatherService>(WeatherService::class.java)

                val listCall: Call<List<CityResponse>> = service.getCityCoordinates(
                   binding?.cityName?.text.toString(), APP_ID
                )
                showProgressDialog()

                // Callback methods are executed using the Retrofit callback executor.
                listCall.enqueue(object : Callback<List<CityResponse>> {
                    override fun onResponse(
                        response: Response<List<CityResponse>>?,
                        retrofit: Retrofit
                    ) {

                        hideProgressDialog()
                        // Check weather the response is success or not.
                        if (response!!.isSuccess) {

                            /** The de-serialized response body of a successful response. */
                            val weatherList: List<CityResponse> = response.body()
                            val weatherResponseJsonObject = Gson().toJson(weatherList)
                            getLocationWeatherDetails(weatherList[0].lat, weatherList[0].lon)

                            val editor = cSharedPreferences.edit()
                            editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonObject)
                            editor.apply()

                        } else {
                            Log.v("MainActivity", "Error")

                            // If the response is not success then we check the response code.
                            val sc = response.code()
                            when (sc) {
                                400 -> {
                                    Log.e("Error 400", "Bad Request")
                                }

                                404 -> {
                                    Log.e("Error 404", "Not Found")
                                }

                                else -> {
                                    Log.e("Error", "Generic Error")
                                }
                            }
                        }
                    }

                    override fun onFailure(t: Throwable) {
                        hideProgressDialog()

                        Log.e("MainActivity", t.message.toString())
                    }
                })

            } else {
                Toast.makeText(
                    this@MainActivity,
                    "No internet connection available.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()

        progressDialog = customProgressDialog(this)
        if (!isLocationEnabled()) {
        } else {
            Dexter.withActivity(this)
                .withPermissions(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }

                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission. Please allow it is mandatory.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread()
                .check()
        }
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage(
                "It Looks like you have turned off permissions required for this feature." +
                        " It can be enabled under Application Settings"
            )
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog,
                                           _ ->
                dialog.dismiss()
            }.show()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            i("Current Latitude", "$latitude")

            val longitude = mLastLocation.longitude
            i("Current Longitude", "$longitude")


            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun isLocationEnabled(): Boolean {

        // This provides access to the system location services.
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }
    // TODO (STEP 5: Create a function to make an api call using Retrofit Network Library.)
    // START
    /**
     * Function is used to get the weather details of the current location based on the latitude longitude
     */
    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {

        // TODO (STEP 6: Here we will check whether the internet
        //  connection is available or not using the method which
        //  we have created in the Constants object.)
        // START
        if (Constants.isNetworkAvailable(this@MainActivity)) {
            Log.d("MainActivity", "inside getlocationweatherdetails")

            val retrofit: Retrofit = Retrofit.Builder().baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService =
                retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getweather(
                latitude, longitude,
                METRIC_UNIT, APP_ID
            )
            showProgressDialog()

            // Callback methods are executed using the Retrofit callback executor.
            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(
                    response: Response<WeatherResponse>?,
                    retrofit: Retrofit
                ) {

                    hideProgressDialog()
                    // Check weather the response is success or not.
                    if (response!!.isSuccess) {

                        /** The de-serialized response body of a successful response. */
                        val weatherList: WeatherResponse = response.body()
                        val weatherResponseJsonObject = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonObject)
                        editor.apply()
                        setupUI()
                        Log.v("MainActivity", "$weatherList")
                    } else {
                        // If the response is not success then we check the response code.
                        val sc = response.code()
                        when (sc) {
                            400 -> {
                                Log.e("Error 400", "Bad Request")
                            }

                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }

                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(t: Throwable) {
                    hideProgressDialog()

                    Log.e("MainActivity", t.message.toString())
                }
            })

        } else {
            Toast.makeText(
                this@MainActivity,
                "No internet connection available.",
                Toast.LENGTH_SHORT
            ).show()
        }
        // END
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.refresh_menu -> {
                requestLocationData()
                true
            }

            else -> return super.onOptionsItemSelected(item)
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun showProgressDialog() {
        // Show the custom progress dialog
        progressDialog?.show()
    }

    private fun hideProgressDialog() {
        // Dismiss the custom progress dialog
        progressDialog?.dismiss()
    }

    private fun setupUI() {


        val weatherResponseJsonString =
            mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")
        // For loop to get the required data. And all are populated in the UI.
        if (!weatherResponseJsonString.isNullOrEmpty()) {
            val weatherList =
                Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
            for (z in weatherList.weather.indices) {
                Log.i("NAMEEEEEEEE", weatherList.weather[z].main)

                binding?.tvMain?.text = weatherList.weather[z].main
                binding?.tvMainDescription?.text = weatherList.weather[z].description
                binding?.tvTemp?.text =
                    weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                binding?.tvSunriseTime?.text = unixTime(weatherList.sys.sunrise.toLong())
                binding?.tvSunsetTime?.text = unixTime(weatherList.sys.sunset.toLong())
                binding?.tvHumidity?.text = weatherList.main.humidity.toString() + " per cent"
//                binding?.tvMin?.text = weatherList.main.tempMin.toString() + " min"
//                binding?.tvMax?.text = weatherList.main.tempMax.toString() + " max"
                binding?.tvSpeed?.text = weatherList.wind.speed.toString()
                binding?.tvName?.text = weatherList.name
                binding?.tvCountry?.text = weatherList.sys.country

                // Here we update the main icon
                when (weatherList.weather[z].icon) {
                    "01d" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
                    "02d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "03d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "04d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "04n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "10d" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                    "11d" -> binding?.ivMain?.setImageResource(R.drawable.storm)
                    "13d" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                    "01n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "02n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "03n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "10n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "11n" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                    "13n" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                }
            }
        }

    }
    private fun getUnit(value: String): String? {
        Log.i("unitttttt", value)
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}
