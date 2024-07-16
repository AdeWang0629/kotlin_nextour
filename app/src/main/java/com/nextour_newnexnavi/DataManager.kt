package com.nextour_newnexnavi

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.net.toUri
import com.google.firebase.database.IgnoreExtraProperties
import java.io.*
import java.lang.Exception


//FirebaseのRoute１単位項目
@IgnoreExtraProperties
data class RouteItem (
    var introduction: String = "",
    var name: String = "",
    var order: Int = 0,
    var short_des: String = "",
    var start_point: String = "",
    var type: String = ""
)

class Route {
    companion object {
        var all = mutableListOf<Route>()

        fun itemFrom(id: String): Route? {
            val route: Route? = all.first { it.id == id }
            return route
        }
    }
    var id = ""
    var introduction: String = ""
    var name: String = ""
    var order: Int = 0
    var short_des: String = ""
    var start_point: String = ""
    var type: String = ""
    var pictname: String = ""
    var distance: Double = 0.0  //a5.0:007:add

    fun trans(item: RouteItem?){
        if (item == null) return
        introduction = item.introduction
        name = item.name
        order = item.order
        short_des = item.short_des
        start_point = item.start_point
        type = item.type
    }
}

//FirebaseのRoute１単位項目
@IgnoreExtraProperties
data class SpotItem (
    var introduction: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var name: String = "",
    var order: Int = 0,
    var radius: Int = 0,
    var reverse: String = "false",
    var reversetimer: Int = 0,
    var spottimer: Double = 0.0,
    var transport: String = ""
)

class Spot {
    companion object {
        var all = mutableListOf<Spot>()
        var forguide = mutableListOf<Spot>()
    }
    var id: String = ""
    var latitude: Double = 0.0
    var longitude: Double = 0.0
    var radius: Int = 0
    var introduction: String = ""
    var name: String = ""
    var order: Int = 0
    var reverse: Boolean = false
    var reversetimer: Int = 0
    var spottimer: Double = 0.0
    var transport: String = ""
    var pictname: String = ""
    var soudname: String = ""

    fun trans(item: SpotItem?) {
        if (item == null) return
        introduction = item.introduction
        latitude = item.latitude
        longitude = item.longitude
        radius = item.radius
        name = item.name
        order = item.order
        reverse = item.reverse == "true"
        reversetimer = item.reversetimer
        spottimer = item.spottimer
        transport = item.transport
    }

    //乗り物アイコンIDを返す
   fun iconResouce(): Int {
        when (this.transport) {
            "bus"-> return R.drawable.bus1
            "boat"-> return R.drawable.boat
            else -> return 0
        }
    }
}

class Media(name: String, data: ByteArray?) {
    companion object {
        var all = mutableListOf<Media>()

        fun load(context: Context) {
            all.clear()

            val ext = listOf("jpg", "mp3")
            try {
                val files = context.filesDir.listFiles()
                files.forEach { println(it.name) }
                files.forEach {
                    if (ext.contains(it.extension) && it.exists()){
                        when (it.extension) {
                            "jpg"->{
                                val inputstream = BufferedInputStream(context.openFileInput(it.name))
                                val media =
                                    Media(it.name, inputstream.readBytes())
                                all.add(media)
                            }
                            "mp3"->{
                                //音声ファイルは存在すれば名前だけ持って、データロードしない。
                                val media = Media(it.name, null)
                                all.add(media)
                            }
                        }

                    }
                }
            } catch(e:Exception) {
                println("!!!!!!!!!!!!!!!!!!!!!!!" + e.toString())
            }
        }

        //imageデータ専用
        fun databyName(from: String): ByteArray? {
            if (from == "") return null
            val res = all.filter { it.name == from }
            if (res.count() == 0) return null
            val media = res.first()
            return  media.data
        }

        //音声データはuriを生成して返す関数を使う
        fun uribyName(from: String): Uri? {
            if (from == "") return null
            val res = all.filter { it.name == from }
            if (res.count() == 0) return null
            try {
                val files = MyApp.getContext().filesDir.listFiles() ?: return null
                files.first { it.name.contains(from) }.let {
                    return it.toUri()
                }
            } catch(e: Exception) {
                return null
            }
        }

        fun save(context: Context, overwrite: Boolean = false) {
            val filenames = context.filesDir.listFiles()?.map {it.name} ?: listOf()
            filenames.forEach{println(it)}
            all.forEach {
                try {
                    val filename = it.name
                    //上書き指定または既存ファイルに元データがない場合は保存処理
                    if (overwrite || !filenames.contains(filename)) {
                        val fos = context.openFileOutput(filename, Context.MODE_PRIVATE)
                        fos.write(it.data ?: byteArrayOf())
                        it.saved = true
                        fos.close()
                    }
                } catch (e: Exception) {
                    println("!!!!!!!!!!!!!!!!!!!!!!!" + e.toString())
                    it.saved = false
                }
            }
        }
    }

    var name = name
    var data: ByteArray? = data
    var saved = false

    //音声ファイルはデータを持たないので、個別にDL都度保存する。
    fun save(context: Context,data: ByteArray){
        try {
            val fos = context.openFileOutput(name, Context.MODE_PRIVATE)
            fos.write(data)
            saved = true
        } catch (e:Exception){
            println("!!!!!!!!!!!!!!!!!!!!!!!" + e.toString())
            saved = false
        }
    }
}
