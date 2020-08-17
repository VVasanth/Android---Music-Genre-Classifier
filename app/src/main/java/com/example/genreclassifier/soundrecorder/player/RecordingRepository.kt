package com.example.genreclassifier.soundrecorder.player

import android.R
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import java.io.File
import kotlin.collections.ArrayList


class RecordingRepository{
    companion object {
        @Volatile
        private var instance: RecordingRepository? = null

        fun getInstance() =
            instance ?: synchronized(this) {
                instance ?: RecordingRepository().also { instance = it }
            }


        fun playRecording(context: Context, title: String){

            val path = Uri.parse(Environment.getExternalStorageDirectory().absolutePath+"/AudioRecorder/$title")

            val manager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if(manager.isMusicActive) {
                Toast.makeText(context, "Another recording is just playing! Wait until it's finished!", Toast.LENGTH_SHORT).show()
            }else{
                val mediaPlayer: MediaPlayer? = MediaPlayer().apply {
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                    setDataSource(context, path)
                    prepare()
                    start()
                }
            }

        }


        fun splitSongs(magValuesList: ArrayList<Float>, overlap: Double): ArrayList<List<Float>> {
            val chunk = 33000
            val offset = (chunk * (1 - overlap)).toInt()
            val xShape: Int = magValuesList.size

            val xMaxInd = xShape / chunk
            val xMax = (xMaxInd * chunk) - chunk



            var splitSongValList : ArrayList<List<Float>> = ArrayList<List<Float>>();

            for( i in 0 until xMax + 1 step offset){
                splitSongValList.add(magValuesList.subList(i, i+chunk))

            }

            return splitSongValList
        }


        fun deleteRecording(context: Context, title: String){
            val extDirectoryPath = Environment.getExternalStorageDirectory().path
            var audioFilePath = extDirectoryPath + "/" + "AudioRecorder" + "/" + title
            val file = File(audioFilePath)
            val deleted: Boolean = file.delete()
            val dialog: AlertDialog.Builder = AlertDialog.Builder(context)
            dialog.setMessage("Recording " + title + " is successfully deleted.")
            dialog.setTitle("Delete Recording - Success")
            val alertDial : AlertDialog = dialog.create()
            alertDial.show()



        }

        fun classifyRecording(context:Context, title:String){


            val extDirectoryPath = Environment.getExternalStorageDirectory().path
            var audioFilePath = extDirectoryPath + "/" + "AudioRecorder" + "/" + title

            val pref: SharedPreferences = context.getSharedPreferences("Genre_Prefs", 0)



            val predVal = pref.getString(audioFilePath, "-1");
            println(pref.getString(title, "-1"));

            val dialog: AlertDialog.Builder = AlertDialog.Builder(context)
            dialog.setMessage("Predicted Value is " + predVal)
            dialog.setTitle("Dialog Box")
            val alertDial : AlertDialog = dialog.create()
            alertDial.show()


            Toast.makeText(context, "Predicted Music Genre is " + predVal, Toast.LENGTH_SHORT).show()

        }

    }

    private val recorderDirectory = File(Environment.getExternalStorageDirectory().absolutePath+"/AudioRecorder/")
    private var file : ArrayList<String>? = null

    init {
        file = ArrayList<String>()
        getRecording()
    }

    private fun getRecording(){
        val files: Array<out File>? = recorderDirectory.listFiles()
        for(i in files!!){
            println(i.name)
            file?.add(i.name)
        }
    }

    fun getRecordings() = file

}