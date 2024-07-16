package com.nextour_newnexnavi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.nextour_newnexnavi.databinding.ListItemCourseBinding
import org.w3c.dom.Text

//class RowCourseList {
//    var pictId = R.drawable.funabashi
//    var name = "テスト"
//    var introduction = "これはテストデータです。"
//    var startat = "スタート地点"
//}

class CourseListViewHolder(binding: ListItemCourseBinding) : RecyclerView.ViewHolder(binding.root) {
    var pict = binding.pict
    var name = binding.name
    var introduction = binding.introduction
    var startat = binding.textdistance
}

class CourseListViewAdapter(private val list: List<Route>, private val listener: ListListener): RecyclerView.Adapter<CourseListViewHolder>() {
    override fun onBindViewHolder(holder: CourseListViewHolder, row: Int) {
        val rowdata = list[row]
        val imagedata = Media.databyName(rowdata.pictname)
        if (imagedata != null) {
            //a5.0:001:update
            val tempbmp = BitmapFactory.decodeByteArray(imagedata, 0, imagedata.count())
            val ratio = tempbmp.height.toDouble() / tempbmp.width.toDouble()
            val bmp = Bitmap.createScaledBitmap(tempbmp, 120, (120 * ratio).toInt(), true)
            //---update
            Glide.with(holder.pict)
            .load(bmp).transform(MultiTransformation(CenterCrop(), RoundedCorners(20)))
            .into(holder.pict)
//            .transform(MultiTransformation(CenterCrop(), RoundedCorners(120)))
            //centerCrop().apply(RequestOptions.bitmapTransform(RoundedCorners(30)))
        }

        holder.name.text = rowdata.name
        holder.introduction.text = rowdata.short_des
        holder.startat.text = rowdata.start_point

        //タップ通知
        holder.itemView.setOnClickListener {
            listener.onClickRow(it, list[row], row)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseListViewHolder {
        val binding = ListItemCourseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.ballimage.setImageResource(R.drawable.bluebutton)
        binding.locationicon.setImageResource(R.drawable.location_route)
        binding.selectrowicon.setImageResource(R.drawable.rarrow)
        return CourseListViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    interface ListListener {
        fun onClickRow(tappedView: View, rowModel: Route, position: Int)
    }
}

@Suppress("UNUSED_PARAMETER")
class CourseListFragment : Fragment() {
    private var selectedRow = 0
    private var currentloc = Location("mylocation")    //a5.0:007

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_course_list, container, false)
        view.findViewById<TextView>(R.id.title).text = Localization.word_title_course()
        view.findViewById<TextView>(R.id.selectcourse).text = Localization.word_select_course()
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)

        //a5.0:007:add:ルート一覧でもGPS測位データから現在地を取得（距離計算用）
        GPSService.lastLocation?.let {
            currentloc = it
        }
        //a5.0:007:update:距離順そーと
        if (Common.connectionState(this.requireContext())) {
            //通信可能時は、全てのRouteについて基準地点を取得し、その距離が近い順ソート
            var num = 0
            val count = Route.all.count()
            Route.all.forEach { it ->
                val id = it.id
                fetchBaseSpot(id) { _, lat, lon ->
                    val distance = Common.calcHubeny(lat,lon,currentloc.latitude,currentloc.longitude)
                    it.distance = distance
                }
                num ++
                if (num == count) {
                    Route.all.sortBy { it.distance }
                }
            }
        } else {
            //通信不可時はorder順ソート
            Route.all.sortBy { it.order }
        }

        val adapter = CourseListViewAdapter(
            Route.all,
            object : CourseListViewAdapter.ListListener {
                override fun onClickRow(
                    tappedView: View,
                    rowModel: Route,
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

        view.findViewById<ImageButton>(R.id.backbutton).setOnClickListener {
            findNavController().popBackStack()
        }


        //テーブル表示
        val itemDecoration = DividerItemDecoration(this.context, DividerItemDecoration.VERTICAL)
        recyclerView.addItemDecoration(itemDecoration)
//        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter
        return view
    }

    fun onClick(tappedView: View, rowModel: Route, row: Int) {
        selectedRow = row
        val route = Route.all[row]
        val id = route.id
        val action =
            CourseListFragmentDirections.actionCourseListFragmentToIntroductionFragment(
                id
            )
        findNavController().navigate(action)
    }

    //a5.0:007:add:距離計算用の基準地点の取得
    //基準スポット取得
    private fun fetchBaseSpot(routeid: String, complete: (Boolean, Double, Double)-> Unit) {
        val db = FirebaseDatabase.getInstance(getString(R.string.dbURL))
        val myref = db.getReference(mylanguage.dbsign()).child(routeid).child("$routeid-01")
        val mylistener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {

                val r = Spot()
                r.trans(dataSnapshot.getValue(SpotItem::class.java))

                complete(true, r.latitude, r.longitude)
            }
            override fun onCancelled(databaseError: DatabaseError) {
                complete(false, 0.0, 0.0)
            }
        }
        myref.addListenerForSingleValueEvent(mylistener)
    }
}
