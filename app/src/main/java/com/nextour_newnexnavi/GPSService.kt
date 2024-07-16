package com.nextour_newnexnavi

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.location.*
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_MIN
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class GPSService : Service(), LocationListener {
    companion object {
        var lastLocation: Location? = null
    }
    val LOG_TAG = GPSService::class.java.simpleName

    private val binder = GPSServiceBinder()
    private var isLocationManagerUpdatingLocation: Boolean = false

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(i: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(i, flags, startId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground()
        }

        return Service.START_STICKY
    }


    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onRebind(intent: Intent) {

    }

    override fun onUnbind(intent: Intent): Boolean {

        return true
    }

    override fun onDestroy() {

    }

    fun startUpdatingLocation(){
        if (isLocationManagerUpdatingLocation) return
        isLocationManagerUpdatingLocation = true
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        //Exception thrown when GPS or Network provider were not available on the user's device.
        try {
            val criteria = Criteria()
            criteria.accuracy = Criteria.ACCURACY_FINE
            criteria.powerRequirement = Criteria.POWER_HIGH
            criteria.isAltitudeRequired = false
            criteria.isSpeedRequired = true
            criteria.isCostAllowed = true
            criteria.isBearingRequired = false

            //API level 9 and up
            criteria.horizontalAccuracy = Criteria.ACCURACY_HIGH
            criteria.verticalAccuracy = Criteria.ACCURACY_HIGH
            //criteria.setBearingAccuracy(Criteria.ACCURACY_HIGH);
            //criteria.setSpeedAccuracy(Criteria.ACCURACY_HIGH);

            val gpsFreqInMillis = 1000
            val gpsFreqInDistance = 0.1  // in meters


            locationManager.requestLocationUpdates(
                gpsFreqInMillis.toLong(),
                gpsFreqInDistance.toFloat(),
                criteria,
                this,
                null
            )
            /* Battery Consumption Measurement */
//            gpsCount = 0
//            batteryLevelArray.clear()
//            batteryLevelScaledArray.clear()


        } catch (e: IllegalArgumentException) {
            Log.e(LOG_TAG, e.localizedMessage)
        } catch (e: SecurityException) {
            Log.e(LOG_TAG, e.localizedMessage)
        } catch (e: RuntimeException) {
            Log.e(LOG_TAG, e.localizedMessage)
        }

    }

    fun stopUpdatingLocation() {
        if (this.isLocationManagerUpdatingLocation == true) {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager.removeUpdates(this)
            isLocationManagerUpdatingLocation = false
        }
    }

    inner class GPSServiceBinder : Binder() {
        val service: GPSService
            get() = this@GPSService
    }

    override fun onLocationChanged(location: Location) {
        location.let{
            lastLocation = location
            val intent = Intent("LocationUpdated")
            intent.putExtra("location", it)
            LocalBroadcastManager.getInstance(this.application).sendBroadcast(intent)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        if (provider == LocationManager.GPS_PROVIDER) {
            if (status == LocationProvider.OUT_OF_SERVICE) {
                notifyLocationProviderStatusUpdated(false)
            } else {
                notifyLocationProviderStatusUpdated(true)
            }
        }
    }

//    fun onProviderEnabled(provider: String?) {
//        if (provider == LocationManager.GPS_PROVIDER) {
//            notifyLocationProviderStatusUpdated(true)
//        }
//    }
//
//    fun onProviderDisabled(provider: String?) {
//        if (provider == LocationManager.GPS_PROVIDER) {
//            notifyLocationProviderStatusUpdated(false)
//        }
//
//    }

    private fun notifyLocationProviderStatusUpdated(isLocationProviderAvailable: Boolean) {
        //Broadcast location provider status change here
    }

    @SuppressLint("ForegroundServiceType")
    private fun startForeground() {
        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel("my_service", "Location Tracking Service")
            } else {
                // If earlier version channel ID is not used
                // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                ""
            }

        val notificationBuilder = NotificationCompat.Builder(this, channelId )
        val notification = notificationBuilder.setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        startForeground(101, notification)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String): String{
        val chan = NotificationChannel(channelId,
            channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }
}
