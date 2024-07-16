package com.nextour_newnexnavi

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.nextour_newnexnavi.databinding.ListItemLanguageBinding

class RowLanguageList {
    var languagename = ""
    var imageIds = mutableListOf<Int>()
}

class LanguageListViewHolder(binding: ListItemLanguageBinding) : RecyclerView.ViewHolder(binding.root) {
    var languagename: TextView = binding.languageName
    var image1: ImageView = binding.imageview1
    var image2: ImageView = binding.imageview2
    var image3: ImageView = binding.imageview3
    var isselected = false
}

class LanguageListViewAdapter(private val list: List<RowLanguageList>, private val listener: ListListener): RecyclerView.Adapter<LanguageListViewHolder>() {
    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: LanguageListViewHolder, row: Int) {

        holder.languagename.text = list[row].languagename
        for (i in 0..2) {
            val id = list[row].imageIds[i]
            when (i) {
                0 -> holder.image1.setImageResource(id)
                1 -> holder.image2.setImageResource(id)
                2 -> holder.image3.setImageResource(id)
            }
        }

        //タップ通知
        holder.itemView.setOnClickListener {
            //チェックマークオンオフ
            for (r in 0..list.size-1) {
                if (r == row) list[r].imageIds[2] = R.drawable.selected else list[r].imageIds[2] = 0
            }
            this.notifyDataSetChanged()
            listener.onClickRow(it, list[row], row)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageListViewHolder {
        val rowView: View = LayoutInflater.from(parent.context).inflate(R.layout.list_item_language, parent, false)
        return LanguageListViewHolder(ListItemLanguageBinding.bind(rowView));
    }

    override fun getItemCount(): Int {
        return list.size
    }

    interface ListListener {
        fun onClickRow(tappedView: View, rowModel: RowLanguageList, position: Int)
    }
}

class LanguageFragment : Fragment() {
    var rowcount = 0
    var lastlanguage = myLanguage.JAPANESE
    private var currentview: View? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_language, container, false)
        currentview = view

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        rowcount = dataList().count()
        val languageset = Common.getPrefer(requireContext())
            .getString(myPref.mylanguage.key, null)
        mylanguage = if (languageset == null) myLanguage.autoSelect() else myLanguage.getLanguage(
            languageset
        )
        lastlanguage = mylanguage

        val adapter = LanguageListViewAdapter(
            dataList(),
            object : LanguageListViewAdapter.ListListener {
                override fun onClickRow(
                    tappedView: View,
                    rowModel: RowLanguageList,
                    position: Int
                ) {
//                    Common.changeColorInSelection(
//                        tappedView,
//                        Color.GRAY,
//                        Color.TRANSPARENT
//                    )
                    onClick(tappedView, rowModel, position)
                }
            })

        view.findViewById<ProgressBar>(R.id.progressBar).visibility = View.INVISIBLE

        //ダミーで一回DLしておく。
        loadInfo()

        //OKボタンで画面遷移
        view.findViewById<Button>(R.id.nextbutton).setOnClickListener {
            it.isEnabled = false
            val button = it
            if (Route.all.count() == 0 || mylanguage != lastlanguage) {
                //ルート一覧の取得
                //a5.0:008:update:接続状態を確認してからの取得処理へ変更
                val isConnected = Common.connectionState(this.requireContext())
                if (isConnected) {
                    //通信可能状態
                    Route.all.clear()   //一旦クリア
                    view.findViewById<ProgressBar>(R.id.progressBar).visibility = View.VISIBLE
                    loadRoutes {
                        loadPicts {
                            view.findViewById<ProgressBar>(R.id.progressBar).visibility = View.INVISIBLE
                            Media.save(requireActivity().applicationContext)
                            findNavController().navigate(R.id.action_languageFragment_to_courseListFragment)
                        }
                    }
                } else {
                    //通信不可状態
                    button.isEnabled = true
                    requireActivity().runOnUiThread {
                        Common.netNGMessage(this.requireFragmentManager(), requireActivity().resources)
                    }
                }
            } else {
                findNavController().navigate(R.id.action_languageFragment_to_courseListFragment)
            }
        }

        //テーブル表示
        val itemDecoration = DividerItemDecoration(this.context, DividerItemDecoration.VERTICAL)
        recyclerView.addItemDecoration(itemDecoration)
//        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter
        return view
    }

    override fun onResume() {
        super.onResume()
        reflectTexts()
    }

    fun onClick(tappedView: View, rowModel: RowLanguageList, row: Int) {
        mylanguage =
            myLanguage.useValue(row)
        Common.setPrefFor(
            requireContext(),
            myPref.mylanguage.key,
            mylanguage.sign()
        )
        reflectTexts()
    }

    private fun reflectTexts() {
        requireView().findViewById<TextView>(R.id.title)?.text = Localization.title_langset()
        requireView().findViewById<TextView>(R.id.selectlanguage)?.text = Localization.word_langset()
        requireView().findViewById<Button>(R.id.nextbutton)?.text = Localization.word_susumu()
    }

    private fun dataList() : List<RowLanguageList> {
        val datalist = mutableListOf<RowLanguageList>()
        val languages = myLanguage.useList()
        val current = mylanguage.index()
        val images = mutableListOf(
            Pair(R.drawable.ame,R.drawable.eng),
            Pair(0,R.drawable.china),
            Pair(R.drawable.taiwan,R.drawable.honkon),
            Pair(0,R.drawable.korea),
            Pair(0,R.drawable.french),
            Pair(0,R.drawable.jpn)
            )
        for (i in 0..languages.count()-1) {
            val data = RowLanguageList().also {
                it.languagename = languages[i]
                val pairimage = images[i]
                val checkimage =  if (i == current) R.drawable.selected else 0
                it.imageIds = mutableListOf(pairimage.first, pairimage.second, checkimage)
            }
            datalist.add(data)
        }
        return datalist
    }

    private fun loadInfo() {
        val db = FirebaseDatabase.getInstance(getString(R.string.dbURL))
        val myref = db.getReference("info")
        val mylistener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {

            }

            override fun onCancelled(databaseError: DatabaseError) {

            }
        }
        myref.addListenerForSingleValueEvent(mylistener)
    }

    private fun loadRoutes(complete:(Boolean)->Unit){
        println("start-------==")
        val db = FirebaseDatabase.getInstance(getString(R.string.dbURL))
        val myref = db.getReference(mylanguage.dbsign()).child("route")

        val mylistener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                println("gotten-------==")
                var num = 0L
                val count = dataSnapshot.childrenCount
                for (sp in dataSnapshot.children) {
                    num ++
                    val r = Route()
                    r.id = sp.key ?: ""
                    r.trans(sp.getValue<RouteItem>(RouteItem::class.java))
                    Route.all.add(r)
                    if (num == count) {
                        println("complete-------==")
                        complete(true)
                    }
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {
                complete(false)
            }
        }
        myref.addListenerForSingleValueEvent(mylistener)
    }

    private fun loadPicts(complete:(Boolean)->Unit) {
        println("startpict-------==")
        val storage = FirebaseStorage.getInstance(getString(R.string.strageURL))
        val count = Route.all.count()
        var num = 0
        val checkcomplete = {
            num ++
            if (num == count) {
                complete(true)
            }
        }

        var haserror = false
        val onmemories = Media.all.map { it.name }  //既に読み込み済みのメディアファイル
        for ((id,route) in Route.all.withIndex()) {
            val routepicid = "route_" + route.id + ".jpg"
            route.pictname = routepicid
            if (onmemories.contains(routepicid)) {
                //既に取得済みのデータはDLを省略
                println("-------- exist:" + routepicid)
                checkcomplete()
                continue
            }
            val strageref = storage.reference.child(routepicid)
            val TENMEGA: Long = 10 * 1024 * 1024
            if (haserror) {
                complete(false)
                break
            }
            strageref.getBytes(TENMEGA).addOnSuccessListener {
                val media = Media(routepicid, it)
                Media.all.add(media)
                checkcomplete()
            }.addOnFailureListener {
                haserror = true
            }
        }
    }

    companion object {
        fun newInstance() = LanguageFragment()
    }
}
