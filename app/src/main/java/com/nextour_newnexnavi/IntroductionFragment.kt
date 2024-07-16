package com.nextour_newnexnavi

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import org.w3c.dom.Text

class IntroductionFragment : Fragment() {
    val args: IntroductionFragmentArgs by navArgs()
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_language, container, false)

        view.findViewById<TextView>(R.id.title).text = Localization.title_introduction()
        view.findViewById<TextView>(R.id.instruction).text = Localization.word_start_course()
        view.findViewById<Button>(R.id.startbutton).text =
            Localization.word_buttonstart_course()
        val routeid = args.routeID
        val route = Route.itemFrom(routeid)
        view?.let { reflectInfo(it,route) }

        view.findViewById<ProgressBar>(R.id.progressBar).visibility = View.INVISIBLE

        view.findViewById<Button>(R.id.backbutton).setOnClickListener {
            it.isEnabled = false
            findNavController().popBackStack()
        }

        firebaseAnalytics = Firebase.analytics  //a5.1:002:add:アナリティクス初期化
        //案内開始
        view.findViewById<Button>(R.id.startbutton).setOnClickListener {
            it.isEnabled = false
            view.findViewById<ProgressBar>(R.id.progressBar).visibility = View.VISIBLE
            //アナリティクスデータ送信
            val log = FALog()
            log.route = routeid
            log.send(mylanguage.dbsign() + routeid, firebaseAnalytics )

            //スポットの取得
            //a5.0:007:update:既にスポット情報が存在すれば、改めて通信しない（無通信状態でも動作させる）
            if (Common.connectionState(this.requireContext())) {
                //通信ありの場合は最新をDL
                val exist = Spot.all.filter { it.id.substring(0,2)  == routeid }.count()
                if (exist > 0) {
                    //既にスポットデータある場合は、該当ルート分を削除しておく
                    Spot.all = Spot.all.filter {it.id.substring(0,2)  != routeid}.toMutableList()
                }
                fetchspots(routeid){
                    view.findViewById<ProgressBar>(R.id.progressBar).visibility = View.INVISIBLE
                    view.findViewById<Button>(R.id.startbutton).isEnabled = true
                    val action =
                        IntroductionFragmentDirections.actionIntroductionFragmentToPlaceListFragment(
                            routeid
                        )
                    findNavController().navigate(action)
                }
            } else {
                //通信なし
                val exist = Spot.all.filter { it.id.substring(0,2)  == routeid }.count()
                if (exist > 0) {
                    //既にスポットデータあり
                    val action =
                        IntroductionFragmentDirections.actionIntroductionFragmentToPlaceListFragment(
                            routeid
                        )
                    findNavController().navigate(action)
                } else {
                    //対象スポットデータのDLはまだなし:通信不能のメッセージ
                    requireActivity().runOnUiThread {
                        Common.netNGMessage(this.requireFragmentManager(), requireActivity().resources)
                        it.isEnabled = true
                        view.findViewById<ProgressBar>(R.id.progressBar).visibility = View.INVISIBLE
                    }
                }
            }

        }

        return view
    }

    override fun onResume() {
        super.onResume()
        
    }

    private fun reflectInfo(view: View, route: Route?){
        val route = if (route != null) route!! else return
        val imagedata = Media.databyName(route.pictname)
        val bmp = BitmapFactory.decodeByteArray(imagedata, 0, imagedata?.count() ?: 0)
        view.findViewById<ImageView>(R.id.pict).setImageBitmap(bmp)
        view.findViewById<TextView>(R.id.name).text = route.name
        view.findViewById<TextView>(R.id.content).text = route.introduction
    }

    //スポット取得
    private fun fetchspots(routeid: String, complete: (Boolean)-> Unit) {
        val db = FirebaseDatabase.getInstance(getString(R.string.dbURL))
        val myref = db.getReference(mylanguage.dbsign()).child(routeid)
        val mylistener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                println("gotten-------==")
                for (sp in dataSnapshot.children) {
                    val r = Spot()
                    r.id = sp.key ?: ""
                    r.trans(sp.getValue<SpotItem>(SpotItem::class.java))
                    Spot.all.add(r)
                }
                println("complete-------==")
                complete(true)
            }
            override fun onCancelled(databaseError: DatabaseError) {
                complete(false)
            }
        }
        myref.addListenerForSingleValueEvent(mylistener)
    }


}
