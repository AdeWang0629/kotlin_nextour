package com.nextour_newnexnavi

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.Application
import android.app.Dialog
import android.content.*
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent
import java.text.SimpleDateFormat
import java.util.*


//変数
var mylanguage = myLanguage.ENGLISH
val firstGuideInterval: Long = 1000    //スタート地点ガイドの自動遷移待機秒数

//共通関数
class Common {
    companion object {
        //選択タップ時の背景色エフェクト（iOS風）
        fun changeColorInSelection(view: View, from: Int, to: Int) =
            ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
                duration = 300
                addUpdateListener {
                    view.setBackgroundColor(it.animatedValue as Int)
                }
                start()
            }

        //プライベートプレファレンス保持
        fun <T> setPrefFor(context: Context, key:String, value: T) {

            val prefer = getPrefer(context)

            val editor = prefer.edit()
            when (value) {
                is Int -> { editor.putInt(key,value as Int)}
                is Long -> { editor.putLong(key,value as Long)}
                is String -> { editor.putString(key,value as String)}
                is Float -> { editor.putFloat(key,value as Float)}
                is Boolean -> { editor.putBoolean(key,value as Boolean)}
                else -> { return }
            }
            editor.commit()
        }

        //プレファランス取得
        fun getPrefer(context: Context): SharedPreferences { return  context.getSharedPreferences("myPrefFileName", Context.MODE_PRIVATE)}

        fun removePrefFor(context: Context, key:String) {
            val prefer = getPrefer(context)

            val editor = prefer.edit()
            editor.remove(key)
            editor.commit()
        }

        fun showToast(ctx: Context, msg: String, duration: Int = Toast.LENGTH_LONG) {
            val toast = Toast(ctx)
            val tv = makeTV(
                ctx,
                msg,
                ctx.resources.getColor(R.color.superlightGray)
            )
            toast.view = tv
            toast.duration = duration
            toast.setGravity(Gravity.CENTER_VERTICAL,0,0)
            toast.show()
        }

        //トースト用TextView生成
        fun makeTV(context:Context, msg: String, backcolor: Int) : TextView {

            val tv = TextView(context)
            tv.textSize = 17f
            tv.setTextColor(Color.BLACK)
            tv.text = msg

            val g = GradientDrawable()
            g.cornerRadius = 20f
            g.setColor(backcolor)
            tv.background = g

            return tv
        }

        // 小数点で緯度経度を取り、距離(m)を返す。
        fun calcHubeny(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val WGS84_A = 6378137.0 //長半径
            val WGS84_B = 6356752.314245 //短半径
            val WGS84_E2 = ((WGS84_A * WGS84_A) - (WGS84_B * WGS84_B)) / (WGS84_A * WGS84_A) // 離心率
            val WGS84_A1E2 = WGS84_A * (1 - WGS84_E2) // 赤道上の子午線曲率半径
            val deg2rad:(Double)-> Double = {d ->
                d * Math.PI / 180.0
            }
            val latdiff = deg2rad(lat1 - lat2) // 緯度差
            val lngdiff = deg2rad(lng1 - lng2) // 経度差
            val latavg = deg2rad((lat1 + lat2) / 2.0) // 緯度平均

            //卯酉線曲率半径
            val sinLatAvg = Math.sin(latavg)
            val w2 = 1.0 - WGS84_E2 * (sinLatAvg * sinLatAvg)
            val n = WGS84_A / Math.sqrt(w2)

            //子午線曲率半径
            val m = WGS84_A1E2 / (Math.sqrt(w2) * w2)

            // Hubeny
            val t1 = m * latdiff
            val t2 = n * Math.cos(latavg) * lngdiff

            return Math.sqrt((t1 * t1) + (t2 * t2))
        }

        fun adviceMessage(manager: FragmentManager, resource: Resources) {
            val dialog = SimpleDialog()
            dialog.msg = Localization.word_msg_allwaysON()
            dialog.strOK = "OK"
            dialog.oklistner = DialogInterface.OnClickListener { box, _ ->
                box.dismiss()
            }
            dialog.show(manager, "adviceMessage")
        }

        //a5.0:008:add
//        fun connectionState(isConnected:(Boolean)->Unit){
//            val connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected")
//            connectedRef.addValueEventListener(object : ValueEventListener {
//                override fun onDataChange(snapshot: DataSnapshot) {
//                    val connected = snapshot.getValue(Boolean::class.java) ?: false
//                    isConnected(connected)
//                }
//
//                override fun onCancelled(error: DatabaseError) {
//                    Log.w("!!!!!!", "Listener was cancelled")
//                    isConnected(false)
//                }
//            })
//        }

        //a5.0:008:add：通信状態の監視
        fun connectionState(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork: NetworkInfo? = cm.activeNetworkInfo
            val isConnected: Boolean = activeNetwork?.isConnectedOrConnecting == true
            return isConnected
        }

        //a5.0:008:add:コネクションエラー時のメッセージ
        fun netNGMessage(manager: FragmentManager, resource: Resources) {
            val dialog = SimpleDialog()
            dialog.msg = Localization.msg_connection_NG()
            dialog.strOK = "OK"
            dialog.oklistner = DialogInterface.OnClickListener { box, _ ->
                box.dismiss()
            }
            dialog.show(manager, "NGMessage")
        }
    }
}

class MyApp: Application() {

    override fun onCreate() {
        super.onCreate()
        myContext = this
    }

    companion object {
        private lateinit var myContext: Context
        fun getContext(): Context {
            return myContext
        }
    }

}

class FALog {
    var whendate = Date()
    var os = ""
    var language = ""
    var route = ""
    var startspot = ""

    companion object {

        fun set(fba: FirebaseAnalytics) {
            val ctx = MyApp.getContext()
            var userid = Common.getPrefer(ctx).getString(myPref.userID.key, null)
            if (userid == null) {
                userid = UUID.randomUUID().toString()
            }
            Common.setPrefFor(ctx, myPref.userID.key, userid)
            fba.setUserId(userid)
        }
    }

    fun send(name: String, fba: FirebaseAnalytics) {
        val ctx = MyApp.getContext()
        val userid = Common.getPrefer(ctx).getString(myPref.userID.key, "")
        val dateformat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        dateformat.timeZone = TimeZone.getTimeZone("Asia/Tokyo")
        val now = dateformat.format(whendate)

        fba.logEvent(name) {
            this.param("userID", userid ?: "noID" )
            this.param("when", now)
            this.param("os","androio" + Build.VERSION.SDK_INT.toString())
            this.param("language", mylanguage.dbsign())
            this.param("route", route)
            this.param("startspot",startspot)
        }
    }
}





interface ScreenListner {
    fun onScreenOff()
    fun onScreenOn()
}

class ScreenOFFreceiver(private val listener: ScreenListner) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
//        if (action == Intent.ACTION_SCREEN_ON) {
////            this.listener.onScreenOn()
        if (action == Intent.ACTION_USER_PRESENT) {
            this.listener.onScreenOn()
        } else if (action == Intent.ACTION_SCREEN_OFF) {
            this.listener.onScreenOff()
        }
    }
}

class SimpleDialog : DialogFragment() {
    var title = ""
    var msg = ""
    //        val strOK = getString(R.string.btnset)
//        val strCancel = getString(R.string.btncancel)
    var strOK = "OK"
    var strCancel = ""
    var oklistner : DialogInterface.OnClickListener? =  DialogInterface.OnClickListener { _, _ -> }
    var cancellistner : DialogInterface.OnClickListener? = DialogInterface.OnClickListener { _, _ -> }
    var whenpause: (()->Unit)? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Use the Builder class for convenient dialog construction
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(title)
            .setMessage(msg)
            .setPositiveButton(strOK,oklistner)
            .setNegativeButton(strCancel,cancellistner)
        // Create the AlertDialog object and return it
        return builder.create()
    }

    override fun onPause() {
        super.onPause()
        // onPause でダイアログを閉じる場合
        whenpause?.invoke()
        dismiss()
    }

}

//ローカライズクラス
class Localization() {

    companion object {
        val title_langset = {

            val l = mylanguage
            val words = listOf(
                "言語設定",
                "Language setting",
                "语言设定",
                "語言設定",
                "언어 설정",
                "การตั้งค่าภาษา",
                "Paramètres de langue")

            words[l.localize()]
        }

        val word_langset = {
            val l = mylanguage
            val words = listOf(
                "使用する言語を選択してください",
                "Please select language",
                "请选择您的语言",
                "請選擇您的語言",
                "언어를 선택하십시오",
                "กรุณาเลือกภาษาของคุณ",
                "Veuillez sélectionner  langue")

            words[l.localize()]
        }

        val word_susumu = {
            val l = mylanguage
            val words = listOf(
                "進む",
                "OK",
                "前进",
                "前進",
                "진행",
                "",
                "Aller au suivant")

            words[l.localize()]
        }
        var word_title_course = {
 val l = mylanguage
            val words = listOf(
                "観光ルート一覧",
                "Tour list",
                "旅游路线一览表",
                "旅遊路線一覽表",
                "관광 루트 일람",
                " รายการ ทัวร์",
                "Liste des tournées")

            words[l.localize()]
        }
        var word_select_course = {
 val l = mylanguage
                    val words = listOf(
                "観光ルートを選択してください",
                "Select tour route",
                "请选择路线",
                "請選擇路線",
                "관광 루트를 선택해 주세요.",
                " กรุณาเลือก เส้นทาง ท่องเที่ยว",
                "Sélectionnez l'itinéraire")

            words[l.localize()]
        }
        var word_select_spot = {
 val l = mylanguage
                    val words = listOf(
                "出発地を選択して下さい",
                "Select the place of departure",
                "请选择出发地",
                "請選擇出發地",
                "출발지를 선택해 주세요.",
                "",
                "Sélectionnez le lieu de départ")

            words[l.localize()]
        }
        var word_gps = {
 val l = mylanguage
                    val words = listOf(
                "GPS",
                "GPS",
                "GPS",
                "GPS",
                "GPS",
                "GPS",
                "GPS")

            words[l.localize()]
        }
        var word_timer = {
 val l = mylanguage
                    val words = listOf(
                "タイマー",
                "Timer",
                "计时器",
                "計時器",
                "타이머",
                "",
                "Minuteur")

            words[l.localize()]
        }
        var word_manual = {
 val l = mylanguage
                    val words = listOf(
                "手動",
                "Manual",
                "手动",
                "手動",
                "수동",
                "",
                "Manuel")

            words[l.localize()]
        }
        var word_msg_downloading = {
 val l = mylanguage
                    val words = listOf(
                "画像や音声データをダウンロード中です。\nもう少しお待ち下さい。",
                "Downloading images and audio data. Please wait a moment.",
                "图片及语音文件正在下载中，\n 请稍候。",
                "圖片及語音檔正在下載中，\n請稍候。",
                "화상과 음성 데이터의 다운로드 중입니다.잠시만 기다려 주세요.",
                "",
                "Téléchargement d'images et de données audio. \nPatientez s'il-vous-plait.")

                        words[l.localize()]
        }
        var word_confirm = {
 val l = mylanguage
                    val words = listOf(
                "確認",
                "Confirm",
                "确认",
                "確認",
                "확인",
                " การยืนยัน",
                "Confirmer")

            words[l.localize()]
        }
        var word_msg_abnormal_db = {
 val l = mylanguage
                    val words = listOf(
                "案内情報が異常です。正しい案内ができません。",
                "There is an error in guidance information. The app cannot provide correct guidance.",
                "导航信息异常，无法正常导航。",
                "導航信息異常，無法正常導航。",
                "안내 정보 오류입니다. 올바른 안내를 할 수 없습니다.",
                "",
                "Il y a une erreur dans les informations de guidage.\nL'application ne peut pas fournir de conseils corrects.")

                        words[l.localize()]
        }
        var word_select_direction = {
 val l = mylanguage
                    val words = listOf(
                "進行方向を選んでください",
                "Please select the direction",
                "请选择前进方向",
                "請選擇前進方向",
                "진행 방향을 선택해주세요.",
                " please choose the direction",
                "veuillez choisir la direction")

            words[l.localize()]
        }
        var word_normal_direction = {
 val l = mylanguage
                    val words = listOf(
                "↓（順方向）",
                "↓（Forward）",
                "↓（顺向）",
                "↓（順向）",
                "↓（순방향）",
                " ↓（forward）",
                "↓ （Avant）")

            words[l.localize()]
        }
        var word_reverse_direction = {
 val l = mylanguage
                    val words = listOf(
                "↑（逆方向）",
                "↑（Reverse）",
                "↑（逆向）",
                "↑（逆向）",
                "↑（역방향）",
                " ↑（reverse）",
                "↑（Reverse）")

            words[l.localize()]
        }
        var word_cancel = {
 val l = mylanguage
                    val words = listOf(
                "キャンセル",
                "Cancel",
                "取消",
                "取消",
                "취소",
                " cancel",
                "Annuler")

            words[l.localize()]
        }
        var word_msg_arrival = {
 val l = mylanguage
                    val words = listOf(
                "最終目的地に到着しました。\nお疲れ様でした。",
                "You have arrived at your destination.",
                "已经到达目的地，路上辛苦了。",
                "已經到達目的地，路上辛苦了。",
                "최종 목적지에 도착했습니다.\n수고하셨습니다.",
                "",
                "Vous êtes arrivé à destination.")

            words[l.localize()]
        }
        var word_predict_time = {
 val l = mylanguage
                    val words = listOf(
                "予想所要時間：およそあと",
                "Estimated time required: approximately",
                "预计所需时间：大约还要",
                "預計所需時間：大約還要",
                "예상 소요 시간: 앞으로 약",
                "",
                "Temps estimé requis: environ")

            words[l.localize()]
        }
        var word_unit_min = {
 val l = mylanguage
                    val words = listOf(
                "分",
                "more minutes",
                "分",
                "分",
                "분",
                "",
                "plus de minutes")

            words[l.localize()]
        }
        var word_required_time = {
 val l = mylanguage
                    val words = listOf(
                "予想所要時間：およそ",
                "Estimated time required: approximately",
                "预计所需时间：大约",
                "預計所需時間：大約",
                "예상 소요 시간: 약",
                "",
                "Temps estimé requis: environ")

            words[l.localize()]
        }
        var word_notification_timer_arrival = {
 val l = mylanguage
                    val words = listOf(
                "目的地到着予定通知",
                "Estimated destination arrival notification",
                "預計到达目的地通知",
                "預計到達目的地通知",
                "목적지 도착 예정 통지",
                "",
                "Notification d'arrivée à destination estimée")

            words[l.localize()]
        }
        var word_msg_gps_timer_arrival = {
 val l = mylanguage
                    val words = listOf(
                "***に到着している可能性があります。",
                "You may have arrived at ***.",
                "可能已经到达***。",
                "可能 已經到達***",
                "***에 도착했을 가능성이 있습니다.",
                "",
                "Vous êtes peut-être arrivé à ***.")

            words[l.localize()]
        }
        var word_msg_timer_arrival = {
 val l = mylanguage
                    val words = listOf(
                "***に到着予定の時刻です。",
                "It is the estimated time of arrival at ***.",
                "预计到达 ***的时间。",
                "預計到達 ***的時間。",
                "***에 도착 예정 시간입니다.",
                "",
                "Il s'agit de l'heure d'arrivée estimée à ***.")

            words[l.localize()]
        }
        var word_notification_gps_arrival = {
 val l = mylanguage
                    val words = listOf(
                "目的地到着通知",
                "Destination arrival notification",
                "目的地到达通知",
                "目的地到達通知",
                "목적지 도착 통지",
                "",
                "Notification d'arrivée à destination")

            words[l.localize()]
        }
        var word_msg_gps_arrival = {
 val l = mylanguage
                    val words = listOf(
                "***付近に到着しました。",
                "You have arrived near ***.",
                "已经到达***附近",
                "已經到達 ***附近",
                "***부근에 도착했습니다.",
                "",
                "Vous êtes arrivé près de ***.")

            words[l.localize()]
        }
        var word_msg_change_manual = {
 val l = mylanguage
                    val words = listOf(
                "次の到着時刻の予測が難しいので手動モードに切り替えます。",
                "Since it is difficult to predict the next arrival time, the app will switch to  Manual mode.",
                "因无法预测 下一个到达时间切换到手动模式。",
                "因無法預測下一個到達時間切換到手動模式。",
                "다음 도착 시각의 예측이 어렵기 때문에 수동 모드로 전환하겠습니다.",
                "",
                "Comme il est difficile de prédire la prochaine heure d'arrivée, l'application passera en mode manuel.")

            words[l.localize()]
        }
        var word_msg_change_gps = {
 val l = mylanguage
                    val words = listOf(
                "次の到着時刻の予測が難しいのでGPSモードに切り替えます。",
                "Since it is difficult to predict the next arrival time, the app will switch to GPS mode.",
                "因无法预测 下一个到达时间切换到GPS模式。",
                "因無法預測下一個到達時間切換到GPS模式。",
                "다음 도착 시각의 예측이 어렵기 때문에 GPS 모드로 전환하겠습니다.",
                "",
                "Comme il est difficile de prédire la prochaine heure d'arrivée, l'application passera en mode GPS.")

            words[l.localize()]
        }
        var word_msg_autoplay = {
 val l = mylanguage
                    val words = listOf(
                "GPSモードでのタイマーによる案内を自動再生しますか？（以後同じ）",
                "Do you want to automatically play the timer guidance in GPS mode? (Same thereafter)",
                "是否自动播放GPS模式的定时导航？（下次相同处理）",
                "是否自動播放GPS模式的定時導航？（下次相同處理）",
                "GPS 모드에서 타이머에 의한 안내를 자동 재생하시겠습니까? (이후 같음)",
                "",
                "Voulez-vous lire automatiquement le guidage du minuteur en mode GPS? (Idem par la suite)")

            words[l.localize()]
        }
        var word_autoplay = {
 val l = mylanguage
                    val words = listOf(
                "自動再生する",
                "Play automatically",
                "自动播放",
                "自動播放",
                "자동 재생하기",
                "",
                "Jouez automatiquement")

            words[l.localize()]
        }
        var word_manualplay = {
 val l = mylanguage
                    val words = listOf(
                "再生しない（手動）",
                "Don't play (manual)",
                "不自动播放（手动）",
                "不自動播放（手動）",
                "재생 안 함 (수동)",
                "",
                "Ne jouez pas (manuel)")

            words[l.localize()]
        }
        var word_msg_novoice = {
 val l = mylanguage
                    val words = listOf(
                "このスポットには音声案内はありません。",
                "There is no audio guidance for this spot.",
                "此处无语音导航。",
                "此處無語音導航。",
                "이 장소에는 음성 안내가 없습니다.",
                "",
                "Il n'y a pas de guidage audio pour cet endroit.")

            words[l.localize()]
        }
        var word_msg_allwaysON = {
 val l = mylanguage
                    val words = listOf(
                "位置情報が「常に許可」\nとなっていません。\n位置情報を「常に許可」\nにしないと正確な案内ができません。",
                "Location Services information is not set to \"Always allow\" \n. \n The app cannot provide accurate guidance unless Location Services information is set to \"Always allow\".",
                "位置信息未被设置成\n“总是允许”。位置信息如未被设置成“总是允许”将无法正确导航。",
            "位置信息未被設置成\n“總是允許”。\n位置信息如未被設置成“總是允許”將無法正確導航。",
            "위치정보가 “항상 허용”으로 되어있지 않습니다. 위치정보를 “항상 허용”로 설정하지 않으면 정확한 안내를 할 수 없습니다.",
            "",
            "Les informations des services de localisation ne sont pas définies sur \"Toujours autoriser\".\n L'application ne peut pas fournir de conseils précis à moins que " +
                    "\n les informations des services de localisation ne soient définies sur «Toujours autoriser».")

            words[l.localize()]
        }
        var word_msg_use_timermode = {
 val l = mylanguage
                    val words = listOf(
                "",
                "Use Timer mode",
                "使用定时模式",
                "使用定時模式",
                "타이머 모드에서 사용",
                "Utiliser le mode minuterie")

            words[l.localize()]
        }
        var word_msg_open_settings = {
 val l = mylanguage
                    val words = listOf(
                "",
                "Open Settings",
                "打开设置页面",
                "開啟設置頁面",
                "설정 화면 열기",
                "",
                "Ouvrir les paramètres")

            words[l.localize()]
        }
        var word_button_OK = {
 val l = mylanguage
                    val words = listOf(
                "OK",
                "OK",
                "OK",
                "OK",
                "OK",
                "OK",
                "OK")

            words[l.localize()]
        }

        var word_buttonstart_course
                = {
 val l = mylanguage
                    val words = listOf(
                "次へ進んで案内文をダウンロードする",
                "Proceed to the next, then download the tour guidance",
                "进行下一步并下载路线资料",
                "進行下一步並下載路線資料",
                "다음에 이어 안내문을 다운로드 한다",
                "",
                "Allez à la page suivante et téléchargez le guide")

            words[l.localize()]
        }
        var word_start_course = {
 val l = mylanguage
                    val words = listOf(
                "案内文をダウンロードするまではインターネット接続が必要です",
                "Connect to internet until the tour guidance downloaded.",
                "下载路线资料之前请连接网络。",
                "下載路線資料之前請連接網路。",
                "안내문을 다운로드 할 때까지 인터넷 연결이 필요합니다",
                "",
                "Connectez-vous à Internet jusqu'à ce que le guide touristique soit téléchargé.")

            words[l.localize()]
        }
//        val title_langset = {
//
//            val l = mylanguage
//            val words = listOf(
//                "言語設定",
//                "Language setting",
//                "语言设定",
//                "語言設定",
//                "언어 설정",
//                "การตั้งค่าภาษา")
//
//            words[l.localize()]
//        }
//
//        val word_langset = {
//            val l = mylanguage
//            val words = listOf(
//                "使用する言語を選択してください",
//                "Please select language",
//                "请选择您的语言",
//                "請選擇您的語言",
//                "언어를 선택하십시오",
//                "กรุณาเลือกภาษาของคุณ")
//
//            words[l.localize()]
//        }
//
//        val word_susumu = {
//            val l = mylanguage
//            val words = listOf(
//                "進む",
//                "OK",
//                "前进",
//                "前進",
//                "진행",
//                "")
//
//            words[l.localize()]
//        }
//
//
//        val word_title_course = {
//            val l = mylanguage
//            val words = listOf(
//                "観光ルート一覧",
//                "Tour list",
//                "旅游路线一览表",
//                "旅遊路線一覽表",
//                "관광 루트 일람",
//                " รายการ ทัวร์")
//
//            words[l.localize()]
//        }
//
//        val word_select_course = {
//            val l = mylanguage
//            val words = listOf(
//                "観光ルートを選択してください",
//                "Select tour route",
//                "请选择路线",
//                "請選擇路線",
//                "관광 루트를 선택해 주세요.",
//                " กรุณาเลือก เส้นทาง ท่องเที่ยว")
//
//            words[l.localize()]
//        }
//        //a5.0:009:update
//        val word_start_course = {
//            val l = mylanguage
//            val words = listOf(
//                "案内文をダウンロードするまではインターネット接続が必要です",
//                "Connect to internet until the tour guidance downloaded.",
//                "下载路线资料之前请连接网络。",
//                "下載路線資料之前請連接網路。",
//                "안내문을 다운로드 할 때까지 인터넷 연결이 필요합니다.",
//                "")
//
//            words[l.localize()]
//        }
//        //a5.0:009:update
//        val word_buttonstart_course = {
//            val l = mylanguage
//            val words = listOf(
//                "次へ進んで案内文をダウンロードする",
//                "Proceed to the next, then download the tour guidance",
//                "进行下一步并下载路线资料",
//                "進行下一步並下載路線資料",
//                "다음에 이어 안내문을 다운로드 한다",
//                " เริ่มต้น การแนะแนว")
//
//            words[l.localize()]
//        }
//
//        val word_select_spot = {
//            val l = mylanguage
//            val words = listOf(
//                "出発地を選択して下さい",
//                "Select the place of departure",
//                "请选择出发地",
//                "請選擇出發地",
//                "출발지를 선택해 주세요.",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_gps = {
//            val l = mylanguage
//            val words = listOf(
//                "GPS",
//                "GPS",
//                "GPS",
//                "GPS",
//                "GPS",
//                "GPS")
//
//            words[l.localize()]
//        }
//
//        val word_timer = {
//            val l = mylanguage
//            val words = listOf(
//                "タイマー",
//                "Timer",
//                "计时器",
//                "計時器",
//                "타이머",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_manual = {
//            val l = mylanguage
//            val words = listOf(
//                "手動",
//                "Manual",
//                "手动",
//                "手動",
//                "수동",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_msg_downloading = {
//            val l = mylanguage
//            val words = listOf(
//                "画像や音声データをダウンロード中です。\nもう少しお待ち下さい。",
//                "Downloading images and audio data. Please wait a moment.",
//                "图片及语音文件正在下载中，\n 请稍候。",
//                "圖片及語音檔正在下載中，\n請稍候。",
//                "화상과 음성 데이터의 다운로드 중입니다.\n잠시만 기다려 주세요.",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_confirm = {
//            val l = mylanguage
//            val words = listOf(
//                "確認",
//                "Confirm",
//                "确认",
//                "確認",
//                "확인",
//                " การยืนยัน")
//
//            words[l.localize()]
//        }
//
//        val word_msg_abnormal_db = {
//            val l = mylanguage
//            val words = listOf(
//                "案内情報が異常です。正しい案内ができません。",
//                "There is an error in guidance information. The app cannot provide correct guidance.",
//                "导航信息异常，无法正常导航。",
//                "導航信息異常，無法正常導航。",
//                "안내 정보 오류입니다. 올바른 안내를 할 수 없습니다.",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_select_direction = {
//            val l = mylanguage
//            val words = listOf(
//                "進行方向を選んでください",
//                "Please select the direction",
//                "请选择前进方向",
//                "請選擇前進方向",
//                "진행 방향을 선택해주세요.",
//                " please choose the direction")
//
//            words[l.localize()]
//        }
//
//        val word_normal_direction = {
//            val l = mylanguage
//            val words = listOf(
//                "↓（順方向）",
//                "↓（Forward）",
//                "↓（顺向）",
//                "↓（順向）",
//                "↓（순방향）",
//                " ↓（forward）")
//
//            words[l.localize()]
//        }
//
//        val word_reverse_direction = {
//            val l = mylanguage
//            val words = listOf(
//                "↑（逆方向）",
//                "↑（Reverse）",
//                "↑（逆向）",
//                "↑（逆向）",
//                "↑（역방향）",
//                " ↑（reverse）")
//
//            words[l.localize()]
//        }
//
//        val word_cancel = {
//            val l = mylanguage
//            val words = listOf(
//                "キャンセル",
//                "Cancel",
//                "取消",
//                "取消",
//                "취소",
//                " cancel")
//
//            words[l.localize()]
//        }
//
//        val word_msg_arrival = {
//            val l = mylanguage
//            val words = listOf(
//                "最終目的地に到着しました。\nお疲れ様でした。",
//                "You have arrived at your destination.",
//                "已经到达目的地，路上辛苦了。",
//                "已經到達目的地，路上辛苦了。",
//                "최종 목적지에 도착했습니다.\n수고하셨습니다.",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_predict_time = {
//            val l = mylanguage
//            val words = listOf(
//                "予想所要時間：およそあと ",
//                "Estimated time required: approximately ",
//                "预计所需时间：大约还要 ",
//                "預計所需時間：大約還要 ",
//                "예상 소요 시간: 앞으로 약 ",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_unit_min = {
//            val l = mylanguage
//            val words = listOf(
//                "分",
//                "min.",
//                "分",
//                "分",
//                "분",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_required_time = {
//            val l = mylanguage
//            val words = listOf(
//                "予想所要時間：およそ ",
//                "Estimated time required: approximately ",
//                "预计所需时间：大约 ",
//                "預計所需時間：大約 ",
//                "예상 소요 시간: 약",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_notification_timer_arrival = {
//            val l = mylanguage
//            val words = listOf(
//                "目的地到着予定通知 ",
//                "Estimated destination arrival notification ",
//                "預計到达目的地通知 ",
//                "預計到達目的地通知 ",
//                "목적지 도착 예정 통지 ",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_msg_gps_timer_arrival = {
//            val l = mylanguage
//            val words = listOf(
//                "%s に到着している可能性があります。",
//                "You may have arrived at %s.",
//                "可能已经到达 %s。",
//                "可能 已經到達 %s",
//                "%s 에 도착했을 가능성이 있습니다.",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_msg_timer_arrival = {
//            val l = mylanguage
//            val words = listOf(
//                "%s に到着予定の時刻です。",
//                "It is the estimated time of arrival at %s.",
//                "预计到达 %s 的时间。",
//                "預計到達 %s 的時間。",
//                "%s 에 도착 예정 시간입니다.",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_notification_gps_arrival = {
//            val l = mylanguage
//            val words = listOf(
//                "目的地到着通知",
//                "Destination arrival notification",
//                "目的地到达通知",
//                "目的地到達通知",
//                "목적지 도착 통지",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_msg_gps_arrival = {
//            val l = mylanguage
//            val words = listOf(
//                "%s 付近に到着しました。",
//                "You have arrived near %s.",
//                "已经到达 %s 附近",
//                "已經到達 %s 附近",
//                "%s 부근에 도착했습니다.",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_msg_change_manual = {
//            val l = mylanguage
//            val words = listOf(
//                "次の到着時刻の予測が難しいので手動モードに切り替えます。",
//                "Since it is difficult to predict the next arrival time, the app will switch to  Manual mode.",
//                "因无法预测 下一个到达时间切换到手动模式。",
//                "因無法預測下一個到達時間切換到手動模式。",
//                "다음 도착 시각의 예측이 어렵기 때문에 수동 모드로 전환하겠습니다.",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_msg_change_gps = {
//            val l = mylanguage
//            val words = listOf(
//                "次の到着時刻の予測が難しいのでGPSモードに切り替えます。",
//                "Since it is difficult to predict the next arrival time, the app will switch to GPS mode.",
//                "因无法预测 下一个到达时间切换到GPS模式。",
//                "因無法預測下一個到達時間切換到GPS模式。",
//                "다음 도착 시각의 예측이 어렵기 때문에 GPS 모드로 전환하겠습니다.",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_msg_autoplay = {
//            val l = mylanguage
//            val words = listOf(
//                "GPSモードでのタイマーによる案内を自動再生しますか？（以後同じ）",
//                "Do you want to automatically play the timer guidance in GPS mode? (Same thereafter)",
//                "是否自动播放GPS模式的定时导航？（下次相同处理）",
//                "是否自動播放GPS模式的定時導航？（下次相同處理）",
//                "GPS 모드에서 타이머에 의한 안내를 자동 재생하시겠습니까? (이후 같음)",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_autoplay = {
//            val l = mylanguage
//            val words = listOf(
//                "自動再生する",
//                "Play automatically",
//                "自动播放",
//                "自動播放",
//                "자동 재생하기",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_manualplay = {
//            val l = mylanguage
//            val words = listOf(
//                "再生しない（手動）",
//                "Don't play (manual)",
//                "不自动播放（手动）",
//                "不自動播放（手動）",
//                "재생 안 함 (수동)",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_msg_novoice = {
//            val l = mylanguage
//            val words = listOf(
//                "このスポットには音声案内はありません。",
//                "There is no audio guidance for this spot.",
//                "此处无语音导航。",
//                "此處無語音導航。",
//                "이 장소에는 음성 안내가 없습니다.",
//                "")
//
//            words[l.localize()]
//        }
//
//        val word_msg_allwaysON = {
//            val l = mylanguage
//            val words = listOf(
//                "位置情報が「常に許可」\nとなっていません。\n位置情報を「常に許可」\nにしないと正確な案内ができません。",
//                "Location Services information is not set to  \n\"Always allow\". \n The app cannot provide accurate guidance unless Location Services information is set to \"Always allow\".",
//                "位置信息未被设置成\n“总是允许”。\n位置信息如未被设置成“总是允许”将无法正确导航。",
//            "位置信息未被設置成\n“總是允許”。\n位置信息如未被設置成“總是允許”將無法正確導航。",
//            "위치정보가 \n“항상 허용”\n으로 되어있지 않습니다. 위치정보를 “항상 허용”로 설정하지 않으면 정확한 안내를 할 수 없습니다.",
//            "")
//
//            words[l.localize()]
//        }


        val title_introduction = {
            val l = mylanguage
            val words = listOf(
                "見どころ紹介",
                "Introduction",
                "看点简介",
                "看點簡介",
                "볼만한 곳 소개",
                " บทนำ ไฮไลท์",
                "Présentation des points forts")

            words[l.localize()]
        }

        //a5.0:007:add
        val msg_connection_NG = {
            val l = mylanguage
            val words = listOf(
                "インターネット接続がありません。",
                "No internet connection.",
                "没有连接网络。",
                "沒有連接網路。",
                "인터넷 접속이안됩니다.",
                "No internet connection.",
                "Il n'y a pas de connexion Internet."
            )

            words[l.localize()]
        }

    }
}

//言語
enum class myLanguage {
    JAPANESE{
        override fun sign(): String = "jan"
        override fun dbsign(): String = "japanese"
        override fun index(): Int = 5
        override fun localize(): Int = 0
    },
    ENGLISH{
        override fun sign(): String = "eng"
        override fun dbsign(): String = "english"
        override fun index(): Int = 0
        override fun localize(): Int = 1
    },
    CHINESE_S{
        override fun sign(): String = "chi"
        override fun dbsign(): String = "chinese1"
        override fun index(): Int = 1
        override fun localize(): Int = 2
    },
    CHINESE_T{
        override fun sign(): String = "zho"
        override fun dbsign(): String = "chinese2"
        override fun index(): Int = 2
        override fun localize(): Int = 3
    },
    KOREAN{
        override fun sign(): String = "kor"
        override fun dbsign(): String = "korean"
        override fun index(): Int = 3
        override fun localize(): Int = 4
    },
    THAI{
        override fun sign(): String = "tha"
        override fun dbsign(): String = "thai"
        override fun index(): Int = 0
        override fun localize(): Int = 5
    },
    FRENCH{
        override fun sign(): String = "fra"
        override fun dbsign(): String = "french"
        override fun index(): Int = 4
        override fun localize(): Int = 6
    };


    //fun定義
    abstract fun sign(): String
    abstract fun dbsign(): String
    abstract fun index(): Int   //言語選択画面のrowと対応
    abstract fun localize(): Int    //ローカライズの配列位置を返す

    companion object {
        fun autoSelect() : myLanguage {
            val locale = Locale.getDefault()
            val language = locale.language
            when (language) {
                "ja"-> return JAPANESE
                "en"-> return ENGLISH
                "zh"-> {
                    val sub = locale.toLanguageTag()
                    if (sub.substring(0,7) == "zh-Hans") {
                        return CHINESE_S
                    } else {
                        return CHINESE_T
                    }
                }
                "th"-> return THAI
                "ko"-> return KOREAN
                "fr"-> return FRENCH
                else-> return ENGLISH
            }
        }

        fun useList() = listOf("English", "中文简体","中文繁體","한국어","Français","日本語")
        fun useValue(of: Int): myLanguage {
            when (of) {
                0-> return ENGLISH
                1-> return CHINESE_S
                2-> return CHINESE_T
                3-> return KOREAN
                4-> return FRENCH
                5-> return JAPANESE
                else-> return ENGLISH
            }
        }

        fun getLanguage(from: String): myLanguage {
            when (from) {
                "jan"-> return JAPANESE
                "eng"-> return ENGLISH
                "chi"-> return CHINESE_S
                "zho"-> return CHINESE_T
                "kor"-> return KOREAN
                "fra"-> return FRENCH
                else -> return ENGLISH
            }
        }

    }
}



//プレファランスキー集
enum class myPref(val key: String) {
    mylanguage("mylanguage"),
    naviMode("naviMode"),
    timerInfo("timerInfo"),
    gpsInfo("gpsInfo"),
    allowNotify("allowNotify"),
    allowTimerPlay("allowTimerPlay"),
    timeralarmSet("timeralarmSet"),
    gpsalarmSet("gpsalarmSet"),
    voiceOn("voiceOn"),
    viewTransfering("viewTransfering"),
    userID("userID");
}

//ナビゲーションモード
enum class naviModeType(val rawValue: Int) {
    GPS(0) {
        override fun selectedColor(): Int {
            return Color.BLUE
        }

        override fun selectedFontColor(): Int {
            return Color.WHITE
        }
    },
    TIMER(1){
        override fun selectedColor(): Int {
            return Color.rgb(255,128,0) //orange
        }

        override fun selectedFontColor(): Int {
            return Color.WHITE
        }
    },
    MANUAL(2){
        override fun selectedColor(): Int {
            return Color.WHITE
        }

        override fun selectedFontColor(): Int {
            return Color.BLACK
        }
    };

    abstract fun selectedColor(): Int
    abstract fun selectedFontColor(): Int

}

//ルートタイプ
enum class routeType {
    NORMAL,
    LOOP,
    SHUTTLE;

    companion object {
        fun of(name: String): routeType {
            when (name) {
                "loop"-> return LOOP
                "shuttle"-> return SHUTTLE
                else-> return NORMAL

            }
        }
    }
}