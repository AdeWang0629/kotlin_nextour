package com.nextour_newnexnavi

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Handler
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.nextour_newnexnavi.databinding.FragmentPlaceListBinding
import com.nextour_newnexnavi.databinding.ListItemPlaceBinding
import kotlin.math.round
import kotlin.math.roundToInt


//class RowPlaceList {
//    var pictId = R.drawable.funabashi
//    var name = "テストネーム"
//    var distancetext = "テスト距離"
//}

class PlaceListViewHolder(private val binding: ListItemPlaceBinding) : RecyclerView.ViewHolder(binding.root) {
    var pict = binding.pict
    var name = binding.name
    var distance = binding.textdistance
}

class PlaceListViewAdapter(private val list: List<Spot>, private val listener: ListListener): RecyclerView.Adapter<PlaceListViewHolder>() {
    var currentlocation = Location("mylocation")

    override fun onBindViewHolder(holder: PlaceListViewHolder, row: Int) {
        val rowdata = list[row]

        val imagedata = Media.databyName(rowdata.pictname)
        if (imagedata != null) {
            //a5.0:001:update
            val tempbmp = BitmapFactory.decodeByteArray(imagedata, 0, imagedata?.count() ?: 0)
            val ratio = tempbmp.height.toDouble() / tempbmp.width.toDouble()
            val bmp = Bitmap.createScaledBitmap(tempbmp, 120, (120 * ratio).toInt(), true)
            //---update
            Glide.with(holder.pict)
                .load(bmp)
                .load(bmp).transform(MultiTransformation(CenterCrop(), RoundedCorners(20)))
                .into(holder.pict)
        }
        holder.name.text = rowdata.name
        if (Pair(currentlocation.latitude, currentlocation.longitude) == Pair(0.0,0.0)) {
            holder.distance.text = "-"
        } else {
            val distance = Common.calcHubeny(
                rowdata.latitude,
                rowdata.longitude,
                currentlocation.latitude,
                currentlocation.longitude
            )
            holder.distance.text = distance.roundToInt().toString() + "m"
        }


        //タップ通知
        holder.itemView.setOnClickListener {
            listener.onClickRow(it, list[row], row)
        }
    }

    override fun onBindViewHolder(
        holder: PlaceListViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.any()) {
                val rowdata = list[position]
                val distance = Common.calcHubeny(
                    rowdata.latitude,
                    rowdata.longitude,
                    currentlocation.latitude,
                    currentlocation.longitude
                )
                holder.distance.text = distance.roundToInt().toString() + "m"
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceListViewHolder {
        val binding = ListItemPlaceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.locationicon.setImageResource(R.drawable.location_route)
        binding.selectrowicon.setImageResource(R.drawable.rarrow)
        return PlaceListViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    interface ListListener {
        fun onClickRow(tappedView: View, rowModel: Spot, position: Int)
    }
}
class PlaceListFragment : Fragment() {
    var selectedRow = 0
    var alldownloaded = false
    val args: PlaceListFragmentArgs by navArgs()
    var donotTap = false    //一定時間タップ禁止
    var filescount = 0    //DLファイル数
    var downloaded = 0  //DLした数
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private var locationUpdateReceiver: BroadcastReceiver? = null
    private lateinit var binding: FragmentPlaceListBinding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        binding = FragmentPlaceListBinding.inflate(inflater, container, false)
        val view = binding.root

        val route = Route.all.first { it.id == args.routeID }
        binding.title.text = route.name
        binding.selectstart.text = ""
        binding.downloadbar.progress = 0
        binding.downloadbar.visibility = View.VISIBLE
        filescount = Spot.all.count() * 2   //DLすべき画像＆音声ファイル数
        firebaseAnalytics = Firebase.analytics  //a5.1:002:add:アナリティクス初期化
        //GPS無効時には警告
        val service = (activity as MainActivity).gpsService
        if (service == null) {
            Common.adviceMessage(
                this.requireFragmentManager(),
                requireActivity().resources
            )
        } else {
            service.startUpdatingLocation()
        }

        val recyclerView = binding.recyclerView

        val adapter = PlaceListViewAdapter(
            Spot.all,
            object : PlaceListViewAdapter.ListListener {
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

        GPSService.lastLocation?.let {
            adapter.currentlocation = it
        }

        loadpict(adapter)   //画像のDL
        loadvoice()  //音声のDL

        binding.backbutton.setOnClickListener {
            if (!alldownloaded) {
                if (donotTap) return@setOnClickListener
                prohibitTap()
                Common.showToast(
                    this.requireContext(),
                    Localization.word_msg_downloading()
                )
                return@setOnClickListener
            }
            service?.stopUpdatingLocation()
            findNavController().popBackStack()
        }

        locationUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val newLocation = intent.getParcelableExtra<Location>("location")

                if (newLocation != null) {
                    println(System.currentTimeMillis().toString() + "===============> " + newLocation.latitude + "," + newLocation.longitude)
                }
                if (newLocation != null) {
                    adapter.currentlocation = newLocation
                }
                for (i in 0..Spot.all.count()-1) {
                    adapter.notifyItemChanged(i, "forDistance")
                }
            }
        }

        locationUpdateReceiver?.let{
            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                it,
                IntentFilter("LocationUpdated")
            )
        }


        //テーブル表示
        val itemDecoration = DividerItemDecoration(this.context, DividerItemDecoration.VERTICAL)
        recyclerView.addItemDecoration(itemDecoration)
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter
        return view
    }

    override fun onResume() {
        super.onResume()
        dispProgress()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationUpdateReceiver?.clearAbortBroadcast()
        LocalBroadcastManager.getInstance(MyApp.getContext()).unregisterReceiver(locationUpdateReceiver!!)
        locationUpdateReceiver = null
    }

    fun onClick(tappedView: View, rowModel: Spot, row: Int) {
        if (!alldownloaded) {
            if (donotTap) return
            prohibitTap()

            Common.showToast(
                this.requireContext(),
                Localization.word_msg_downloading()
            )
            return
        }

        selectedRow = row
        adjustOrder(rowModel.order)
    }

    private fun prohibitTap(){
        donotTap = true
        // 2秒後にタップ禁止解除
        Handler().postDelayed(Runnable {
            donotTap = false
        },2000 )
    }

    private fun loadpict(adapter: PlaceListViewAdapter){
        val onmemories = Media.all.map { it.name }  //既に読み込み済みのメディアファイル
        for ((id,spot) in Spot.all.withIndex()) {
            val spotpictid = spot.id + "_image.jpg"
            spot.pictname = spotpictid
            if (onmemories.contains(spotpictid)) {
                incrementNum()
                println("############### pict continue")
                continue
            }
            loadaMedia(spotpictid) {
                println("############### pict dl")
                incrementNum()
                if (it) {
                    requireActivity().runOnUiThread {
                        adapter.notifyItemChanged(id)
                    }
                }
            }
        }

    }

    private fun incrementNum(){
        downloaded ++
        dispProgress()
    }

    private fun dispProgress() {
        val ratio = if (filescount > 0) downloaded.toDouble() / filescount.toDouble() else 1.0
        val progres = round(ratio * 100).toInt()
        if (binding.downloadbar == null) return
        binding.downloadbar.progress = progres
        if (ratio == 1.0) {
            downloaded = 0
            binding.downloadbar.visibility = View.INVISIBLE
            binding.selectstart.text = Localization.word_select_spot()
        }
    }

    private fun loadvoice() {
        val onmemories = Media.all.map { it.name }  //既に読み込み済みのメディアファイル
        val count = Spot.all.count()
        val checkfinish: (Int)->Unit = {
            if (it == count - 1) alldownloaded = true
        }
        for ((id,spot) in Spot.all.withIndex()) {
            val soundfilename = spot.id + mylanguage.sign() + ".mp3"
            spot.soudname = soundfilename
            if (onmemories.contains(soundfilename)) {
                incrementNum()
                println("############### voice continue")
                checkfinish(id)
                continue
            }
            loadaMedia(soundfilename) {
                println("############### voice dl")
                incrementNum()
                checkfinish(id)
            }
        }
    }

    private fun loadaMedia(fileid: String, complete: ((Boolean)->Unit)) {
        val storage = FirebaseStorage.getInstance(getString(R.string.strageURL))
        val strageref = storage.reference.child(fileid)
        val TENMEGA: Long = 10 * 1024 * 1024
        strageref.getBytes(TENMEGA).addOnSuccessListener {
            val media = Media(fileid, it)
            println("-------- downloaded:" + fileid)
            media.save(requireContext(), it)
            Media.all.add(media)
            complete?.invoke(true)
        }.addOnFailureListener {
            println("error media")
            complete?.invoke(false)
        }
    }

    private fun adjustOrder(selected: Int) {
        val startspot = Spot.all.first{it.order == selected}
        val routeid = args.routeID
        val route = Route.itemFrom(routeid)!!
        val type = routeType.of(route.type)
        //a5.1:002:add:イベントログ送信
        //アナリティクスデータ送信
        val log = FALog()
        log.route = routeid
        val logname = startspot.id.replace("-","_")
        log.startspot = startspot.id
        log.send(mylanguage.dbsign() + logname, firebaseAnalytics )
        //---add
        if (startspot.reverse) {
            //開始地点に逆方向案内が許可されていたら選択アラート
            val builder = AlertDialog.Builder(activity)
            builder.setTitle(Localization.word_select_direction())
                .setItems(
                    arrayOf(
                        Localization.word_normal_direction(),
                        Localization.word_reverse_direction(),
                        Localization.word_cancel()
                    )
                ) { dialog, which ->
                    dialog.dismiss()
                    when (which) {
                        0 -> changeOrder(type, selected, false)
                        1 -> changeOrder(type, selected, true)
                    }
                }
                .create()
                .show()
        } else {
            changeOrder(type, selected, false)
        }
    }

    //spotのorder変更
    private fun changeOrder(type: routeType, order: Int, isReverse: Boolean){
        Spot.forguide.clear()   //ガイド用の抽出スポットリストをクリア

        val spots = Spot.all.toMutableList()
        if (isReverse) { //逆方向
            spots.reverse()
            val selectspots = spots.filter{it.order <= order}.toList()
            if (type == routeType.LOOP) {   //巡回タイプはさらに、飛ばした分を後付け
                val toafters = spots.filter{it.order > order}.toList()
                Spot.forguide = selectspots.plus(toafters).toMutableList()
            } else {
                Spot.forguide = selectspots.toMutableList()
            }
        } else { //順方向
            val selectspots = spots.filter{it.order >= order}.toList()
            if (type == routeType.LOOP) {   //巡回タイプはさらに、飛ばした分を後付け
                val toafters = spots.filter{it.order < order}.toList()
                Spot.forguide = selectspots.plus(toafters).toMutableList()
            } else {
                Spot.forguide = selectspots.toMutableList()
            }
        }

        //PlaceListのGPS受信はいったん停止
        locationUpdateReceiver?.clearAbortBroadcast()
        LocalBroadcastManager.getInstance(MyApp.getContext()).unregisterReceiver(locationUpdateReceiver!!)
        locationUpdateReceiver = null
        //案内画面へ遷移
        val route = Route.all.first { it.id == args.routeID }

        val action =
            PlaceListFragmentDirections.actionPlaceListFragmentToGuideFragment(
                isReverse.toString(),
                route.name
            )
        findNavController().navigate(action)

    }
}

private fun Any.actionPlaceListFragmentToGuideFragment(
    routename: String,
    reverse: String
): NavDirections {
    TODO("Not yet implemented")
}
