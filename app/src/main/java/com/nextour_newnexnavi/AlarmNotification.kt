package com.nextour_newnexnavi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock

import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*


class AlarmNotification : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val reqcode = intent.getIntExtra("reqcode",1)
        val title = intent.getStringExtra("title") ?: ""
        val msg = intent.getStringExtra("message") ?:""

//        val outintents = arrayOf<Intent>(intent)
//        val pending = PendingIntent.getActivities(context,reqcode,outintents,PendingIntent.FLAG_UPDATE_CURRENT)

        //タップ時はアプリに戻す
        val mainintent = Intent(Intent.ACTION_MAIN)
        mainintent.addCategory(Intent.CATEGORY_LAUNCHER)
        mainintent.setClassName(MyApp.getContext().packageName, MainActivity::class.java.name)
        mainintent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_NEW_TASK)

        val pending = PendingIntent.getActivity(context,reqcode,mainintent,PendingIntent.FLAG_CANCEL_CURRENT)
        try {
            pending.send()
        } catch (e: Throwable)  {
            println(e.localizedMessage)
        }
        val channelid = "default"

        val dateformat = SimpleDateFormat("HH:mm:ss")
        dateformat.timeZone = TimeZone.getTimeZone("Asia/Tokyo")


        val notifyman = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sounduri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
//        val notifyman = NotificationManagerCompat.from(context)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            //Oreo以降はChannel設定
            val channel = NotificationChannel(channelid, title, NotificationManager.IMPORTANCE_HIGH)
            channel.description = msg
            channel.enableVibration(true)
            channel.canShowBadge()
            channel.enableLights(true)
            channel.lightColor = Color.BLUE
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.setSound(sounduri, null)
            channel.setShowBadge(true)

            notifyman.createNotificationChannel(channel)
        }

        val bmp = BitmapFactory.decodeResource(context.resources,R.drawable.icon120)
        val notification = NotificationCompat.Builder(context,channelid)
            .setContentTitle(title)
            .setContentText(msg)
            .setContentIntent(pending)
            .setSmallIcon(R.drawable.icon20)
            .setLargeIcon(bmp)
            .setDefaults(Notification.DEFAULT_ALL)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()

        wakeupscreen(context)
        notifyman.notify(SystemClock.uptimeMillis().toInt(),notification)


    }

    //画面ON機能
    private fun wakeupscreen(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn = pm.isInteractive()

        if (isScreenOn == false) {
            val wl = pm.newWakeLock((PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE), "MyLock")

            wl.acquire(10000);
            val wl_cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyCpuLock")

            wl_cpu.acquire(10000);
        }
    }
}