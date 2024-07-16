package com.nextour_newnexnavi

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.nextour_newnexnavi.databinding.FragmentContentBinding
import java.util.*
import kotlin.concurrent.schedule


class ContentFragment : Fragment() {

    private val args: ContentFragmentArgs by navArgs()
    private var canplay = true
    private lateinit var spot: Spot
    private var voiceplayer = MediaPlayer()
    private val popbackTime = 180000L
    private lateinit var backTimer: TimerTask
    private val standByTime = 500L
    private lateinit var startTimer: TimerTask
    private lateinit var binding: FragmentContentBinding

//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_content, container, false)
        binding.backbutton.setOnClickListener {
            changeVoice(false)  //a5.0:002:add
            findNavController().popBackStack()
        }
        val spotid = args.spotID
        spot = Spot.forguide.first{ it.id == spotid }

        binding.title.text = spot.name
        val imagedata = Media.databyName(spot.pictname)
        val bmp = BitmapFactory.decodeByteArray(imagedata, 0, imagedata?.count() ?: 0)
        binding.pict.setImageBitmap(bmp)
        binding.spotdetail.text = spot.introduction

        //音声設定の再現
        canplay = Common.getPrefer(requireContext())
            .getBoolean(myPref.voiceOn.key, true)
        startTimer = Timer().schedule(standByTime) {
            changeVoice(canplay)    //0.5秒後に再生開始
        }

        binding.soundOnOff.setOnClickListener {
            canplay = !canplay
            val imageid = if (canplay) R.drawable.speakeroff else R.drawable.speakeron
            binding.soundOnOff.setImageResource(imageid)
            Common.setPrefFor(
                requireContext(),
                myPref.voiceOn.key,
                canplay
            )
            changeVoice(canplay)
        }

        //一定時間後に自動back遷移
        backTimer = Timer().schedule(popbackTime) {
            changeVoice(false)  //a5.0:002:add
            this.cancel()
            findNavController().popBackStack()
        }

        return view
    }

    override fun onDestroy() {
        startTimer.cancel()
        backTimer.cancel()
//        changeVoice(false)    //a5.0:002:del
        super.onDestroy()
    }

    private fun changeVoice(on: Boolean) {
        if (on) {
            val fileuri = Media.uribyName(spot.soudname)
            if (fileuri != null) {
                voiceplayer = MediaPlayer.create(requireContext(), fileuri)
                voiceplayer.isLooping = false
                voiceplayer.setOnCompletionListener {
                    // 2秒後に処理を実行する
                    Handler().postDelayed(Runnable {
                        findNavController().popBackStack()
                    },standByTime )
                }
                voiceplayer.start()
            } else {
                //音声なし警告
                Common.showToast(
                    requireContext(),
                    Localization.word_msg_novoice()
                )
            }
        } else {
            //オフの場合//a5.0:002:add
            try {
                if (voiceplayer.isPlaying) {
                    voiceplayer.stop()
                }

            } catch (e:Exception) {
                println(e.localizedMessage)
            } finally {
                voiceplayer.release()
            }
            //---add
        }
    }
}
