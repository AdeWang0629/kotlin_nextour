package com.nextour_newnexnavi

import android.app.*
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.nextour_newnexnavi.databinding.FragmentGuideBinding
import com.nextour_newnexnavi.databinding.ListItemGuideArriveBinding
import com.nextour_newnexnavi.databinding.ListItemGuideMiddleBinding
import com.nextour_newnexnavi.databinding.ListItemGuideStartBinding
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.roundToLong

//class RowGuideSpot {
//    var name = ""
//    var pictId = 0
//
//}

enum class viewState (val rawValue: Int) {
    MOVING(0),PASSED(1),OTHER(2);

    companion object {
        fun get(liverow:Int, changerow:Int, size: Int): viewState {
            if (liverow == changerow && liverow < size - 1) {
                return MOVING   //ゴールではない移動中のセル
            } else if (liverow > changerow){
                return PASSED   //既に通過したセル
            } else {
                return OTHER    //ゴールか、未到達セル
            }
        }

    }
}

class GuideViewHolder(private val binding: ViewBinding) : RecyclerView.ViewHolder(binding.root) {
    var pict = when (binding) {
        is ListItemGuideStartBinding -> binding.pict
        is ListItemGuideArriveBinding -> binding.pict
        is ListItemGuideMiddleBinding -> binding.pict
        else -> throw IllegalArgumentException("Unsupported binding type")
    }

    var name = when (binding) {
        is ListItemGuideStartBinding -> binding.name
        is ListItemGuideArriveBinding -> binding.name
        is ListItemGuideMiddleBinding -> binding.name
        else -> throw IllegalArgumentException("Unsupported binding type")
    }

    var icon = when (binding) {
        is ListItemGuideStartBinding -> binding.icon
        is ListItemGuideArriveBinding -> binding.icon
        is ListItemGuideMiddleBinding -> binding.icon
        else -> throw IllegalArgumentException("Unsupported binding type")
    }

    var road = when (binding) {
        is ListItemGuideStartBinding -> binding.roadbar
        is ListItemGuideArriveBinding -> binding.roadbar
        is ListItemGuideMiddleBinding -> binding.roadbar
        else -> throw IllegalArgumentException("Unsupported binding type")
    }

    var timerbar = when (binding) {
        is ListItemGuideStartBinding -> binding.timerbar
        is ListItemGuideArriveBinding -> binding.timerbar
        is ListItemGuideMiddleBinding -> binding.timerbar
        else -> throw IllegalArgumentException("Unsupported binding type")
    }

    var gpsbar = when (binding) {
        is ListItemGuideStartBinding -> binding.gpsbar
        is ListItemGuideArriveBinding -> binding.gpsbar
        is ListItemGuideMiddleBinding -> binding.gpsbar
        else -> throw IllegalArgumentException("Unsupported binding type")
    }

    var remain = when (binding) {
        is ListItemGuideStartBinding -> binding.remaintime
        is ListItemGuideArriveBinding -> binding.remaintime
        is ListItemGuideMiddleBinding -> binding.remaintime
        else -> throw IllegalArgumentException("Unsupported binding type")
    }

    //GPSBarのvisual管理、更新
    fun updateGPSBar(ratio: Double, mode: naviModeType, liverow: Int, pos: Int, size: Int) {
        if (mode == naviModeType.GPS) {
            gpsbar.visibility = View.INVISIBLE
            return
        }
        val state = viewState.get(liverow, pos, size)
        val fullgage = road.height
        when (state) {
            viewState.MOVING -> {
                gpsbar.visibility = View.VISIBLE
                gpsbar.layoutParams.height = (fullgage * ratio).toInt()
            }
            viewState.PASSED -> {
                gpsbar.visibility = View.VISIBLE
                gpsbar.layoutParams.height = fullgage
            }
            viewState.OTHER -> {
                gpsbar.visibility = View.INVISIBLE
            }
        }
    }

    //TimerBarのvisual, 長さ更新
    fun updateTimerBar(ratio: Double, mode: naviModeType, liverow: Int, pos: Int, size: Int) {
        if (mode == naviModeType.TIMER) {
            timerbar.visibility = View.INVISIBLE
            return
        }

        val state = viewState.get(liverow, pos, size)
        val fullgage = road.height
        when (state) {
            viewState.MOVING -> {
                timerbar.visibility = View.VISIBLE
                timerbar.layoutParams.height = (fullgage * ratio).toInt()
            }
            viewState.PASSED -> {
                timerbar.visibility = View.VISIBLE
                timerbar.layoutParams.height = fullgage
            }
            viewState.OTHER -> {
                timerbar.visibility = View.INVISIBLE
            }
        }
    }


    fun showIcon(liverow: Int, pos: Int, mode: naviModeType, gpsratio: Double, timerratio: Double) {
        val fullgage = road.height
        val constset = ConstraintSet()
        var cnstlayout = when (binding) {
            is ListItemGuideStartBinding -> binding.cnstlayout
            is ListItemGuideArriveBinding -> binding.cnstlayout
            is ListItemGuideMiddleBinding -> binding.cnstlayout
            else -> null
        }

        var constlayout = null
        if (constlayout==null) return
//        constset.clone(constlayout)
        var topmargin = 0
        if (liverow == pos || (liverow == -1 && pos == 0)) {
            when (mode) {
                naviModeType.GPS -> topmargin = (fullgage * gpsratio).toInt() - icon.height / 2
                naviModeType.TIMER -> topmargin = (fullgage * timerratio).toInt() - icon.height / 2
                else-> topmargin = fullgage / 2 - icon.height / 2
            }

            constset.connect(icon.id, ConstraintSet.TOP, road.id, ConstraintSet.TOP, topmargin)
            constset.applyTo(constlayout)
            icon.visibility = View.VISIBLE
        } else {
            icon.visibility = View.INVISIBLE
        }
    }

}

class GuideViewAdapter(private val list: List<Spot>, private val isReverse: Boolean, private val listener: ListListener): RecyclerView.Adapter<GuideViewHolder>() {
    private var liverow = 0
    private var timerratio = 0.0
    private var gpsratio = 0.0
    private var currentmode = naviModeType.GPS
    private var starttime = System.currentTimeMillis()  //スタート時刻

    override fun onBindViewHolder(holder: GuideViewHolder, row: Int) {

        val rowdata = list[row]
        val size = list.size
        val imagedata = Media.databyName(rowdata.pictname)

        if (imagedata != null) {
            val tempbmp = BitmapFactory.decodeByteArray(imagedata, 0, imagedata?.count() ?: 0)
            val ratio = tempbmp.height.toDouble() / tempbmp.width.toDouble()
            val bmp = Bitmap.createScaledBitmap(tempbmp, 480, (480 * ratio).toInt(), true)
            Glide.with(holder.pict)
                .load(bmp)
                .transform(MultiTransformation(CenterCrop(), RoundedCorners(30)))
                .into(holder.pict)
        }

        holder.name.text = rowdata.name
        //タイマーバー、GPSバー
        holder.updateGPSBar(gpsratio, currentmode, liverow, row, size)
        holder.updateTimerBar(timerratio, currentmode, liverow, row, size)
        holder.road.visibility = if (row == size - 1) View.INVISIBLE else  View.VISIBLE

        //乗り物icon表示&移動
        val trans = rowdata.iconResouce()
        holder.icon.setImageResource(trans)
        holder.showIcon(liverow, row, currentmode, gpsratio, timerratio)

        //予測到着時間表示
        if (row < size-1) {
            val nextspottime = if (isReverse) list[row].spottimer else list[row+1].spottimer
            if (nextspottime < 0) {
                holder.remain.text = ""
            } else {
                val unit = Localization.word_unit_min()
                if (row == liverow) {
                    val predicttime = starttime + (nextspottime * 60000).toLong()    //予想到着時刻(ms）
                    val remaintime = (predicttime - System.currentTimeMillis()) / 60000 //(min)
                    val yosojikan =
                        Localization.word_predict_time()
                    holder.remain.text = String.format(yosojikan + remaintime + unit)
                } else {
                    val yosojikan =
                        Localization.word_required_time()
                    holder.remain.text = String.format(yosojikan + nextspottime.roundToLong() + unit)
                }
            }
        } else {
            holder.remain.text = ""
        }
        //タップ通知
        holder.itemView.setOnClickListener {
            listener.onClickRow(it, list[row], row)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GuideViewHolder {
        val binding = when (viewType) {
            0 -> ListItemGuideStartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            2 -> ListItemGuideArriveBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            else -> ListItemGuideMiddleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        }

        if (binding.root.findViewById<ImageView>(R.id.spoticon) != null) {
            binding.root.findViewById<ImageView>(R.id.spoticon)?.setImageResource(R.drawable.location)
        }
        if (binding.root.findViewById<ImageView>(R.id.triangle) != null) {
            binding.root.findViewById<ImageView>(R.id.triangle)?.setImageResource(R.drawable.tamgiac)
        }
        if (binding.root.findViewById<ImageView>(R.id.roadbar) != null) {
            binding.root.findViewById<ImageView>(R.id.roadbar)?.setImageResource(R.drawable.roadline)
        }
        return GuideViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    //位置タイプを行番号から付与
    override fun getItemViewType(position: Int): Int {
        when (position) {
            0-> return 0
            itemCount - 1 -> return 2
            else -> return 1
        }
    }

    fun refreshTbar(at: Int, ratio: Double, mode: naviModeType, start: Long) {

        liverow = at
        timerratio = ratio
        currentmode = mode
        starttime = start
        notifyItemChanged(at)
    }

    fun refreshGbar(atrow: Int, ratio: Double, mode: naviModeType) {

        liverow = atrow
        gpsratio = ratio
        currentmode = mode

        notifyItemChanged(atrow)
    }

    interface ListListener {
        fun onClickRow(tappedView: View, rowModel: Spot, position: Int)
    }
}

class GuideFragment : Fragment(), ScreenListner {
    var isReverse = false    //リバースモードか？
    var mode = naviModeType.GPS
    val args: GuideFragmentArgs by navArgs()
    private val viewRefleshSpan: Long = 1000  //画面更新間隔（ミリ秒）
    private var targetRow = 1  //現在向かっているスポットのスポット.row
    private lateinit var adapter: GuideViewAdapter
    private var locationUpdateReceiver: BroadcastReceiver? = null
    private var frompreDistance = 0.0
    private var tonextDistance = 0.0
    private var startTask: TimerTask? = null
    private var timertask: TimerTask? = null
    private var alarmlisteners = arrayListOf<AlarmManager.OnAlarmListener>()
    private var timerArrival = false    //タイマー到着フラグ
    private var gpsArrival = false   //GPS到着フラグ
    private lateinit var recyclerView: RecyclerView
    private var underPlaying = false    //画面自動遷移開始したか
    private var fromScreenOff = false
    private var onChecking = false  //AlertDialogで確認中はresume無視
    private var screceiver: ScreenOFFreceiver? = null
    private lateinit var binding: FragmentGuideBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetRow = -1
        //firstGuideInterval秒後に最初の遷移
        startTask = Timer().schedule(firstGuideInterval) {
            val spotid = Spot.forguide[0].id
            transToContentFragment(spotid)
            startTask = null
        }


        //画面ONOFF検知設定
        screceiver = ScreenOFFreceiver(this)
        requireContext().registerReceiver(screceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        requireContext().registerReceiver(screceiver, IntentFilter(Intent.ACTION_USER_PRESENT))    //ONも登録
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        binding = FragmentGuideBinding.inflate(inflater, container, false)
        val view = binding.root

        binding.title.text = args.routename
        binding.gpsbutton.text = Localization.word_gps()
        binding.timerbutton.text = Localization.word_timer()
        binding.manualbutton.text = Localization.word_manual()

        recyclerView = binding.recyclerView

        Common.setPrefFor(
            requireContext(),
            myPref.timeralarmSet.key,
            false
        )  //for debug

        adapter = GuideViewAdapter(
            Spot.forguide,
            isReverse,
            object : GuideViewAdapter.ListListener {
                override fun onClickRow(
                    tappedView: View,
                    rowModel: Spot,
                    position: Int
                ) {
                    Common.changeColorInSelection(
                        tappedView,
                        Color.GRAY,
                        Color.TRANSPARENT
                    )
                    onClick(tappedView, rowModel, position)
                }
            })

        binding.backbutton.visibility = View.INVISIBLE //スタート時点はバックボタンを消す
        binding.backbutton.setOnClickListener {
            if (underPlaying || startTask != null) return@setOnClickListener
            findNavController().popBackStack()
        }

        binding.gpsbutton.setOnClickListener {
            changeMode(naviModeType.GPS)
        }

        binding.timerbutton.setOnClickListener {
            changeMode(naviModeType.TIMER)
        }

        binding.manualbutton.setOnClickListener {
            changeMode(naviModeType.MANUAL)
        }

        if (locationUpdateReceiver == null) {
            locationUpdateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {

                    if (targetRow == Spot.forguide.size) {
                        //最終目的地の案内になればLocation更新不要
                        locationUpdateReceiver?.clearAbortBroadcast()
                        LocalBroadcastManager.getInstance(MyApp.getContext())
                            .unregisterReceiver(locationUpdateReceiver!!)
                        return
                    }

                    intent.getParcelableExtra<Location>("location")?.let { reflectGages(it) }
                }
            }
            locationUpdateReceiver?.let{
                LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                    it,
                    IntentFilter("LocationUpdated")
                )

            }
        }



        //テーブル表示
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter

        return view
    }

    override fun onResume() {
        super.onResume()
        println("========== " + underPlaying.toString())
        if (startTask == null) binding.backbutton.visibility = View.VISIBLE

        if (onChecking) { return }  //a5.0:005:add

        val ontransfering = Common.getPrefer(requireContext()).getBoolean(myPref.viewTransfering.key, false)
        if (ontransfering || targetRow == -1) {
            //画面遷移中からの復帰または初期画面起動時
            Common.setPrefFor(requireContext(), myPref.viewTransfering.key, false)
            println("------- targetRow++ ---------")
            underPlaying = false
            timerArrival = false
            gpsArrival = false
            targetRow ++
        }

        //案内終了
        if (targetRow >= Spot.forguide.size) {
            val dialog = SimpleDialog()
            dialog.msg = Localization.word_msg_arrival()
            dialog.strOK = "OK"
            dialog.oklistner = DialogInterface.OnClickListener { dialog, _ ->
                dialog.dismiss()
                findNavController().popBackStack()
            }
            dialog.whenpause = {
                findNavController().popBackStack()
            }
            dialog.show(this.requireFragmentManager(),null)
            return
//            val ft = this.fragmentManager?.beginTransaction()
//            ft?.add(dialog, null)
//            ft?.commitAllowingStateLoss()
        }

        changeMode(mode)

        if (targetRow > 0 && targetRow < Spot.forguide.size) {
            if (timertask == null) resetTimer()
            if (locationUpdateReceiver == null) startGPS()
        }
        recyclerView.layoutManager?.scrollToPosition(targetRow - 1)
        adapter.refreshTbar(targetRow - 1, 0.0, mode, System.currentTimeMillis())
        adapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        stopNotifyAndTimers()
        locationUpdateReceiver?.clearAbortBroadcast()
        screceiver?.let { requireContext().unregisterReceiver(it)}
        LocalBroadcastManager.getInstance(MyApp.getContext()).unregisterReceiver(locationUpdateReceiver!!)
        locationUpdateReceiver = null
        super.onDestroy()
    }

    private fun stopNotifyAndTimers() {
        println("stopTimers")
        allAlarmClear()
        startTask?.cancel()
        startTask = null
        timertask?.cancel()
        timertask = null
    }

//    //回転検知
//    override fun onConfigurationChanged(newConfig: Configuration) {
//        super.onConfigurationChanged(newConfig)
//        adapter.notifyDataSetChanged()
//    }
//
    //画面がOFFするとき
    override fun onScreenOff() {
        fromScreenOff = true
        println("¥¥¥¥¥¥¥¥¥¥¥¥ off")
    }

    override fun onScreenOn() {
        fromScreenOff = false
        if (gpsArrival || timerArrival) {
            autoPlay(7)
        }
        println("¥¥¥¥¥¥¥¥¥¥¥¥ on")
    }

    fun onClick(tappedView: View, rowModel: Spot, row: Int) {
        if (startTask != null) return
        targetRow = row
        val spotid = Spot.forguide[row].id
        transToContentFragment(spotid)
    }

    fun changeMode(newmode: naviModeType) {


        when (newmode) {

            naviModeType.GPS -> {
                mode = naviModeType.GPS

            }
            naviModeType.TIMER -> {
                //次のスポットにはタイマー値が設定されているか？
                val nextspot = if (isReverse) Spot.forguide[targetRow-1] else Spot.forguide[targetRow]
                if (nextspot.spottimer < 0) {
                    val service = (activity as MainActivity).gpsService
                    if (service == null) {
                        //GPSが許可されていないので、手動モードに切り替える
                        mode = naviModeType.MANUAL
                        Common.showToast(
                            this.requireContext(),
                            Localization.word_msg_change_manual()
                        )
                    } else {
                        //GPSが使えるからGPSに切り替える
                        mode = naviModeType.GPS
                        Common.showToast(
                            this.requireContext(),
                            Localization.word_msg_change_gps()
                        )
                    }
                } else {
                    mode = naviModeType.TIMER
                }
            }
            naviModeType.MANUAL -> {
                mode = naviModeType.MANUAL
            }
        }
        //フラグ保存
        Common.setPrefFor(
            this.requireContext(),
            myPref.naviMode.key,
            mode.rawValue
        )

        //カラー等調整
        val setselected: (Button)-> Unit = { button ->
            val selected = mode.selectedColor()
            val tint = mode.selectedFontColor()
            button.setBackgroundTintList(ColorStateList.valueOf(selected))
            button.setTextColor(tint)
        }
        val setdefault: (Button)-> Unit = { button ->
            button.setBackgroundTintList(ColorStateList.valueOf(resources.getColor(R.color.lightGray)))
            button.setTextColor(Color.BLACK)
        }

//        if (newmode == naviModeType.GPS) setselected(gpsbutton) else setdefault(gpsbutton)
//        if (newmode == naviModeType.TIMER) setselected(timerbutton) else setdefault(timerbutton)
//        if (newmode == naviModeType.MANUAL) setselected(manualbutton) else setdefault(manualbutton)

    }

    //GPSゲージ更新
    private fun reflectGages(newLocation: Location) {
        println("================= called reflectGages =================" + newLocation.longitude + "," + newLocation.latitude)
        if (targetRow <= 0) return
        val nextspot = Spot.forguide[targetRow]
        val curspot = Spot.forguide[targetRow - 1]
        frompreDistance = Common.calcHubeny(
            newLocation.latitude,
            newLocation.longitude,
            curspot.latitude,
            curspot.longitude
        )
        tonextDistance = Common.calcHubeny(
            newLocation.latitude,
            newLocation.longitude,
            nextspot.latitude,
            nextspot.longitude
        )

        //GPSモードの場合、次のスポットに到達したら、自動ガイダンスを開始
        if (mode == naviModeType.GPS && tonextDistance < nextspot.radius && !gpsArrival) {
//            if (mode == naviModeType.GPS && (System.currentTimeMillis()-debugtime > 10000) && !gpsArrival ) {
                gpsArrival = true   //２回以上呼ばないようすぐフラグON
//                stopGPS()   //到着したのでGPS更新をいったん止める
                println("================= GPS Check =================")
            val isactive = (activity as MainActivity).isActive
            if (isactive) {
                //画面表示中の場合:詳細案内画面へ自動遷移
                autoPlay(4)
            } else {
                //バックグラウンドの場合は通知設定
                makeGPSnotification(nextspot.name)
            }
        }

        println("----------------- received ---------------------")
        val total = frompreDistance + tonextDistance
        if (total > 0) {
            val ratio = frompreDistance / total
            adapter.refreshGbar(targetRow - 1, ratio, mode)
        }


    }

    private fun resetTimer(){
        if (mode == naviModeType.MANUAL) return //マニュアルモードはタイマーなし。
        val preStartTime = System.currentTimeMillis()
        val nextspot = Spot.forguide[targetRow]
        val curspot = Spot.forguide[targetRow-1]
        val time = if (isReverse) curspot.spottimer else nextspot.spottimer

        val nextSPotTime = (time * 60 * 1000).toLong()
        if (nextSPotTime < 0) {
            //タイマーがセットされていない場合のタイマーモードなら、GPSモードに切り替えて通知なし
            if (mode == naviModeType.TIMER) changeMode(naviModeType.GPS)
        } else {
            //通知の設定
            makeNotification(preStartTime, nextSPotTime, mode, nextspot.name)

            if (timertask != null) {
                timertask?.cancel()
                timertask = null
            }

            //画面更新間隔
            timertask = Timer().schedule(0, viewRefleshSpan) {
                println("--------- timer -------")
                val curtime = System.currentTimeMillis() - preStartTime

                var ratio = curtime.toDouble() / nextSPotTime.toDouble()
                if (ratio >= 1) {
                    //タイマー到着予想到達
                    ratio = 1.0
                    if (fromScreenOff) {
                        timerArrival = true
                    } else {
                        autoPlay(3)
                    }
                }
                requireActivity().runOnUiThread {
                    adapter.refreshTbar(targetRow - 1, ratio, mode, preStartTime)
                }


            }
        }

    }

    //タイマー到着予想通知
    private fun makeNotification(start: Long,
                                 span: Long,
                                 mode: naviModeType,
                                 spotname: String) {

        //指定日付から通知を１分間隔で３つ(notificationTimes)を作る
        val cal = Calendar.getInstance()
        cal.timeInMillis = start
        var msg = ""
        if (mode == naviModeType.GPS) {
            msg = String.format(Localization.word_msg_gps_timer_arrival(), spotname)
        } else {
            msg = String.format(Localization.word_msg_timer_arrival(), spotname)
        }
        val context = this.requireContext()
        var addtime = span.toInt()

        for (i in 1..3) {
            cal.add(Calendar.MILLISECOND, addtime)
            addtime = 60000    //残る２つは1分後ずつ追加する
//            cal.add(Calendar.MILLISECOND, 5000)   //for debug
            val intent = Intent(context, AlarmNotification::class.java)

            intent.putExtra("reqcode", i)
            intent.putExtra("title",
                Localization.word_notification_timer_arrival()
            )
            intent.putExtra("message",msg)
            val pending = PendingIntent.getBroadcast(context.applicationContext,i,intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
            val alarmman = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            //a5.0:004:update
            if (Build.VERSION.SDK_INT >= 26) {
                //backgroundでもforegroundでも、Androidでは目的地到着を常にアラート表示させる
                val alisterner = AlarmManager.OnAlarmListener {
                    //画面が表示中の場合は、タイマーを全て削除し詳細案内自動遷移処理へ
                    if ((activity as MainActivity).isActive) {
                        autoPlay(2)
                    } else {
                        //画面Lock中なら復帰時に自動遷移処理させるために、タイマー到着フラグをON
                        timerArrival = true
                    }

                }
                alarmlisteners.add(alisterner)
                alarmman.setExact(AlarmManager.RTC_WAKEUP,cal.timeInMillis, String.format("dialogmessage%d",i),alisterner, Handler())//画面dialog用
            }

            alarmman.setExact(AlarmManager.RTC_WAKEUP,cal.timeInMillis,pending) //通知用


        }

        //タイマーアラーム設定フラグON
        Common.setPrefFor(
            context,
            myPref.timeralarmSet.key,
            true
        )

    }

    //GPSモードの到着通知
    private fun makeGPSnotification(spotname: String){
        if (mode != naviModeType.GPS) return

        //指定日付から通知を１分間隔で３つ(notificationTimes)を作る
        val cal = Calendar.getInstance()

        val msg = String.format(Localization.word_msg_gps_arrival(), spotname)

        val context = this.requireContext()
        var addtime = 5000  //１発目は５秒後

        for (i in 4..6) { //GPS通知は4番からidを振る
            cal.add(Calendar.MILLISECOND, addtime)
            addtime = 60000    //残る２つは1分後ずつ追加する

            val intent = Intent(context, AlarmNotification::class.java)

            intent.putExtra("reqcode", i)
            intent.putExtra("title",
                Localization.word_notification_gps_arrival()
            )
            intent.putExtra("message",msg)
            val pending = PendingIntent.getBroadcast(context.applicationContext,i,intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
            val alarmman = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            //a5.0:004:update
            if (Build.VERSION.SDK_INT >= 26) {
                //backgroundでもforegroundでも、Androidでは目的地到着を常にアラート表示させる
                val alisterner = AlarmManager.OnAlarmListener {
                    gpsArrival = true   //GPS到着フラグを立てる
//                    stopGPS()   //到着したのでGPS更新をいったん止める
                    //画面が表示中の場合は、タイマーを全て削除し詳細案内自動遷移処理へ
                    if ((activity as MainActivity).isActive) {
                        autoPlay(1)  //画面アクティブならそのまま遷移
                    }
                }
                alarmlisteners.add(alisterner)

                alarmman.setExact(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    String.format("dialogmessage%d", i),
                    alisterner,
                    Handler()
                )

            }

            alarmman.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending) //通知用

        }


        //GPSアラーム設定フラグON
        Common.setPrefFor(
            context,
            myPref.gpsalarmSet.key,
            true
        )
    }

    private fun stopGPS(){
        val service = (activity as? MainActivity)?.gpsService
        service?.stopUpdatingLocation()
        println("================= stopGPS =================")
    }

    private fun startGPS(){
        val service = (activity as? MainActivity)?.gpsService
        service?.startUpdatingLocation()
        println("================= startGPS =================")
    }


    //登録済アラームの消去
    private fun allAlarmClear(){
        val alarmman = MyApp.getContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for (reqcode in 1..6) {
            val intent = Intent(MyApp.getContext(), AlarmNotification::class.java)
            intent.putExtra("reqcode", reqcode)
            val pending = PendingIntent.getBroadcast(this.requireContext(), reqcode, intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
            pending.cancel()
            alarmman.cancel(pending)
        }
        //callbackを消去
        if (Build.VERSION.SDK_INT >= 26 ) { //a5.0:004:update
            alarmlisteners.forEach { alarmman.cancel(it) }
            alarmlisteners.clear()
        }
        //タイマーアラームフラグOFF
        Common.setPrefFor(
            this.requireContext(),
            myPref.timeralarmSet.key,
            false
        )
        Common.setPrefFor(
            this.requireContext(),
            myPref.gpsalarmSet.key,
            false
        )
    }

    //目的地到着通知や到着トリガーからの詳細案内自動遷移(from: 遷移元デバグno)
    private fun autoPlay(from: Int) {
        if (underPlaying) return    //二重遷移&ポップバック時起動防止
        underPlaying = true
        //既に設定済みアラートを消去
        stopNotifyAndTimers()

        //遷移処理
        val autotransition = {
            timertask?.cancel()
            val spotid = Spot.forguide[targetRow].id
//            timerArrival = false
//            gpsArrival = false
            transToContentFragment(spotid)
        }
        println("******* autoplay ******" + from.toString())
        when (mode) {
            naviModeType.GPS -> {
                val timerplayIsAllowed = Common.getPrefer(requireContext())
                    .getString(myPref.allowTimerPlay.key, null)
                if (timerplayIsAllowed != null) {
                    //タイマープレイが既に許可されている場合、コンテンツへ自動遷移へ
                    if (timerplayIsAllowed == "true") {
                        autotransition()
                    } else {
                        //タイマープレイは禁止されたので、何もせず離脱
                        return
                    }
                } else {

                    activity?.runOnUiThread {
                        //まだ確認されていないならば、許可dialogを表示
                        val builder = AlertDialog.Builder(activity)
                        builder.setTitle(Localization.word_msg_autoplay())
                            .setItems(
                                arrayOf(
                                    Localization.word_autoplay(),
                                    Localization.word_manualplay()
                                )
                            ) { dialog, which ->
                                dialog.dismiss()
                                when (which) {
                                    0 -> {  //自動再生
                                        //確認したので、記録。以後、画面ON時は自動遷移（画面OFF時は通知タップで画面ONしてから自動遷移）
                                        Common.setPrefFor(
                                            requireContext(),
                                            myPref.allowTimerPlay.key,
                                            "true"
                                        )
                                        //autoplay開始
                                        onChecking = false
                                        autotransition()
                                    }
                                    1 -> {  //手動再生
                                        //確認したので、記録。以後、通知のみで自動遷移なし。
                                        Common.setPrefFor(
                                            requireContext(),
                                            myPref.allowTimerPlay.key,
                                            "false"
                                        )
                                        onChecking = false
                                    }
                                }
                            }
                            .create()
                            .show()
                        onChecking = true
                    }
                }
            }

            naviModeType.TIMER -> {
                //Timerモードはデフォルトでauto transition
                autotransition()
            }

            else -> {
            }

            //マニュアル時はautoplay機能は停止
        }


    }

    private fun transToContentFragment(spotid: String) {
        stopNotifyAndTimers()
        val action = GuideFragmentDirections.actionGuideFragmentToContentFragment(spotid)
        Common.setPrefFor(requireContext(), "viewTransfering", true)
        findNavController().navigate(action)
    }

    companion object {

    }
}
