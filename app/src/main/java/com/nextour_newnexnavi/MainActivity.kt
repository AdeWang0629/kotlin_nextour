package com.nextour_newnexnavi

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

class MainActivity : FragmentActivity() {
    var gpsService: GPSService? = null
    private var MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 100
    var isActive = false
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        Media.load(this)    //全画像＆音声ファイルの読み込み

        if (Build.VERSION.SDK_INT >= 23) {
            checkPermission()
            println(Build.VERSION.SDK_INT)
        } else {
            setGPS()
        }

        //a5.0:008:add:接続状態の処理

        Route.all.clear()  //a5.0:008
        Spot.all.clear()  //a5.0:008

        firebaseAnalytics = Firebase.analytics  //a5.1:002:add:アナリティクス初期化
        FALog.set(firebaseAnalytics)
    }

    override fun onStart() {
        super.onStart()
        isActive = true
    }

    override fun onStop() {
        super.onStop()
        isActive = false
    }

    //常時スクリーンONを採用する場合はこちら（Manifestも変更すること）
//    override fun onAttachedToWindow() {
//        super.onAttachedToWindow()
//        toBeShownOnLockScreen()
//    }
//
//    private fun toBeShownOnLockScreen() {
//        window.addFlags(
//            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
//                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
//        )
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
//            setTurnScreenOn(true)
//            setShowWhenLocked(true)
//        } else {
//            window.addFlags(
//                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
//                        or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
//            )
//        }
//    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION)

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            // Permission has already been granted
            setGPS()
        }
    }

    private val serviceConnection = object: ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            val name = p0?.className ?: ""
            if(name.endsWith("GPSService")) {
                gpsService = (p1 as GPSService.GPSServiceBinder).service
                this@MainActivity.gpsService?.startUpdatingLocation()
            }

        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            if (p0?.className == "GPSService") {
                this@MainActivity.gpsService?.stopUpdatingLocation()
                gpsService = null
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    setGPS()
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.

                }
                return
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }

    private fun setGPS() {
        //GPS設定
        val gpsservice = Intent(this.application, GPSService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.application.startForegroundService(gpsservice)
        } else {
            this.application.startService(gpsservice)
        }
        this.application.bindService(gpsservice, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, MainActivity::class.java)

    }
}
