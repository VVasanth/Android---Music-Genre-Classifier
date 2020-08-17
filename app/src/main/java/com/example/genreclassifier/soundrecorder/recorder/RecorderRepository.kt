package com.example.genreclassifier.soundrecorder.recorder

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.arch.lifecycle.MutableLiveData
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Process
import android.util.Log
import android.widget.Toast
import com.example.genreclassifier.soundrecorder.player.Recognition
import com.example.genreclassifier.soundrecorder.player.RecordingRepository
import com.jlibrosa.audio.JLibrosa
import com.jlibrosa.audio.wavFile.WavFileException
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.*
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.util.*


class RecorderRepository{


    companion object {
        @Volatile
        private var instance: RecorderRepository? = null

        fun getInstance() =
            instance ?: synchronized(this) {
                instance ?: RecorderRepository().also { instance = it }
            }
    }

    private var output: String? = null
    private var mediaRecorder: MediaRecorder? = null
    private val dir: File = File(Environment.getExternalStorageDirectory().absolutePath + "/AudioRecorder/")

    private val GENRE_PREFERENCES = "Genre_Prefs"
    private var recordingTime: Long = 0
    private var timer = Timer()
    private val recordingTimeString = MutableLiveData<String>()

    private val RECORDER_BPP = 16
    private val AUDIO_RECORDER_FILE_EXT_WAV = ".wav"
    private val AUDIO_RECORDER_FOLDER = "AudioRecorder"
    private val AUDIO_RECORDER_TEMP_FILE = "record_temp.raw"
    private val RECORDER_SAMPLERATE = 44100
    private val RECORDER_CHANNELS: Int = android.media.AudioFormat.CHANNEL_IN_STEREO
    private val RECORDER_AUDIO_ENCODING: Int = android.media.AudioFormat.ENCODING_PCM_16BIT
    var audioData: ShortArray = ShortArray(2)

    private var recorder: AudioRecord? = null
    private var bufferSize = 0
    private var recordingThread: Thread? = null
    private var isRecording = false


    init {
        try{
            // create a File object for the parent directory
            val recorderDirectory = File(Environment.getExternalStorageDirectory().absolutePath+"/soundrecorder/")
            // have the object build the directory structure, if needed.
            recorderDirectory.mkdirs()
        }catch (e: IOException){
            e.printStackTrace()
        }

        if(dir.exists()){
            val count = dir.listFiles().size
            output = Environment.getExternalStorageDirectory().absolutePath + "/soundrecorder/recording"+count+".mp3"
        }

        bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING)*3;

        audioData =  ShortArray(bufferSize)



    }

    @SuppressLint("RestrictedApi")
    fun startRecording() {

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDER_SAMPLERATE,
            RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING,
            bufferSize
        )
        val i: Int = recorder!!.getState()
        if (i == 1) recorder!!.startRecording()

        isRecording = true

        recordingThread = Thread(Runnable { writeAudioDataToFile() }, "AudioRecorder Thread")

        recordingThread!!.start()
        startTimer()


    }


    private fun getTempFilename(): String {
        val filepath = Environment.getExternalStorageDirectory().absolutePath
        val file = File(filepath, AUDIO_RECORDER_FOLDER)
        if (!file.exists()) {
            file.mkdirs()
        }
        val tempFile = File(filepath, AUDIO_RECORDER_TEMP_FILE)
        if (tempFile.exists()) tempFile.delete()
        return file.absolutePath + "/" + AUDIO_RECORDER_TEMP_FILE
    }

    private fun writeAudioDataToFile() {
        val data = ByteArray(bufferSize)
        val filename: String? = getTempFilename()
        var os: FileOutputStream? = null
        try {
            os = FileOutputStream(filename)
        } catch (e: FileNotFoundException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        var read = 0
        if (null != os) {
            while (isRecording) {
                read = recorder!!.read(data, 0, bufferSize)
                if (read > 0) {
                }
                if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                    try {
                        os.write(data)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
            try {
                os.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("RestrictedApi")
    fun stopRecording(context: Context){
        /*mediaRecorder?.stop()
        mediaRecorder?.release()
        */
        if (null != recorder) {
            isRecording = false
            val i = recorder!!.state
            if (i == 1) recorder!!.stop()
            recorder!!.release()
            recorder = null
            recordingThread = null
        }
        stopTimer()
        resetTimer()

        var fileName = getFilename()
        copyWaveFile(getTempFilename(), fileName)
        deleteTempFile()
        initRecorder()

        classifyRecording(fileName, context)

    }


    private fun classifyRecording(fileName: String, context: Context){

        val loadRunnable = Runnable {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            // Creates a toast pop-up.
            // This is to know if this runnable is running on UI thread or not!
            try {
                var sharedPreferences: SharedPreferences? = null


                sharedPreferences = context.getSharedPreferences(GENRE_PREFERENCES, Context.MODE_PRIVATE);


                val audioFilePath = fileName;

                try {

                    val defaultSampleRate =  -1 //-1 value implies the method to use default sample rate

                    val defaultAudioDuration = -1 //-1 value implies the method to process complete audio duration


                    var jLibrosa: JLibrosa = JLibrosa();
                    //val audioFeatureValues: FloatArray = jLibrosa.loadAndRead(audioFilePath, defaultSampleRate, defaultAudioDuration)
                    val audioFeatureValuesList: ArrayList<Float> = jLibrosa.loadAndReadAsList(
                        audioFilePath,
                        defaultSampleRate,
                        defaultAudioDuration
                    )


                    var splitSongs: ArrayList<List<Float>> =
                        RecordingRepository.splitSongs(audioFeatureValuesList, 0.5)

                    var predictionList : ArrayList<String?> = ArrayList<String?>()
                    for(i in 0 until splitSongs.size){
                        var audioArr: FloatArray = splitSongs[i].toFloatArray()

                        var melSpectrogram: Array<FloatArray> =
                            jLibrosa.generateMelSpectroGram(audioArr, 22050, 1024, 128, 256)


                        var prediction : String? = loadModelAndMakePredictions(melSpectrogram, context);
                        predictionList.add(prediction)
                        println("test")
                    }

                    println(predictionList.groupingBy { it }.eachCount().filter { it.value > 1 })

                    val predList = predictionList.groupingBy { it }.eachCount()
                    val sortedPredList = predList.entries.sortedByDescending { it.value }.associate { it.toPair()}

                    val predValue: String? = sortedPredList.iterator().next().key

                    val editor: SharedPreferences.Editor = sharedPreferences.edit()
                    editor.putString(fileName, predValue);
                    editor.commit()


                }
                catch(e: WavFileException){
                    println(e.message)
                }


            } catch (ex: Exception) {
                println(ex.message)
            }
        }
        val predictionThread = Thread(loadRunnable);
        predictionThread.start();




    }




    protected fun loadModelAndMakePredictions(meanMFCCValues : Array<FloatArray>, context: Context) : String? {



        var predictedResult: String? = "unknown"


        val tflite: Interpreter

        //load the TFLite model in 'MappedByteBuffer' format using TF Interpreter
        val tfliteModel: MappedByteBuffer =  FileUtil.loadMappedFile(context, getModelPath())
        /** Options for configuring the Interpreter.  */
        val tfliteOptions =
            Interpreter.Options()
        tfliteOptions.setNumThreads(2)
        tflite = Interpreter(tfliteModel, tfliteOptions)

        //get the datatype and shape of the input tensor to be fed to tflite model
        val imageTensorIndex = 0

        val imageDataType: DataType = tflite.getInputTensor(imageTensorIndex).dataType()

        val imageDataShape: IntArray = tflite.getInputTensor(imageTensorIndex).shape()

        //get the datatype and shape of the output prediction tensor from tflite model
        val probabilityTensorIndex = 0
        val probabilityShape =
            tflite.getOutputTensor(probabilityTensorIndex).shape()
        val probabilityDataType: DataType =
            tflite.getOutputTensor(probabilityTensorIndex).dataType()




        var byteBuffer : ByteBuffer = ByteBuffer.allocate(4*meanMFCCValues.size*meanMFCCValues[0].size)

        for(i in 0 until meanMFCCValues.size){
            val valArray = meanMFCCValues[i]
            val inpShapeDim: IntArray = intArrayOf(1,1,meanMFCCValues[0].size,1)
            val valInTnsrBuffer: TensorBuffer = TensorBuffer.createDynamic(imageDataType)
            valInTnsrBuffer.loadArray(valArray, inpShapeDim)
            val valInBuffer : ByteBuffer = valInTnsrBuffer.getBuffer()
            byteBuffer.put(valInBuffer)
        }

        byteBuffer.rewind()

        //val inpBuffer: ByteBuffer? = convertBitmapToByteBuffer(bitmp)
        val outputTensorBuffer: TensorBuffer =
            TensorBuffer.createFixedSize(probabilityShape, probabilityDataType)
        //run the predictions with input and output buffer tensors to get probability values across the labels
        tflite.run(byteBuffer, outputTensorBuffer.getBuffer())


        //Code to transform the probability predictions into label values
        val ASSOCIATED_AXIS_LABELS = "labels.txt"
        var associatedAxisLabels: List<String?>? = null
        try {
            associatedAxisLabels = FileUtil.loadLabels(context, ASSOCIATED_AXIS_LABELS)
        } catch (e: IOException) {
            Log.e("tfliteSupport", "Error reading label file", e)
        }

        //Tensor processor for processing the probability values and to sort them based on the descending order of probabilities
        val probabilityProcessor: TensorProcessor = TensorProcessor.Builder()
            .add(NormalizeOp(0.0f, 255.0f)).build()
        if (null != associatedAxisLabels) {
            // Map of labels and their corresponding probability
            val labels = TensorLabel(
                associatedAxisLabels,
                probabilityProcessor.process(outputTensorBuffer)
            )

            // Create a map to access the result based on label
            val floatMap: Map<String, Float> =
                labels.getMapWithFloatValue()

            //function to retrieve the top K probability values, in this case 'k' value is 1.
            //retrieved values are storied in 'Recognition' object with label details.
            val resultPrediction: List<Recognition>? = getTopKProbability(floatMap);

            //get the top 1 prediction from the retrieved list of top predictions
            predictedResult = getPredictedValue(resultPrediction)

        }
        return predictedResult

    }

    fun getPredictedValue(predictedList:List<Recognition>?): String?{
        val top1PredictedValue : Recognition? = predictedList?.get(0)
        return top1PredictedValue?.getTitle()
    }

    fun getModelPath(): String {
        // you can download this file from
        // see build.gradle for where to obtain this file. It should be auto
        // downloaded into assets.
        return "MusicGenreClassifierModel.tflite"
    }

    /** Gets the top-k results.  */
    protected fun getTopKProbability(labelProb: Map<String, Float>): List<Recognition>? {
        // Find the best classifications.
        val MAX_RESULTS: Int = 1
        val pq: PriorityQueue<Recognition> = PriorityQueue(
            MAX_RESULTS,
            Comparator<Recognition> { lhs, rhs -> // Intentionally reversed to put high confidence at the head of the queue.
                java.lang.Float.compare(rhs.getConfidence(), lhs.getConfidence())
            })
        for (entry in labelProb.entries) {
            pq.add(Recognition("" + entry.key, entry.key, entry.value))
        }
        val recognitions: ArrayList<Recognition> = ArrayList()
        val recognitionsSize: Int = Math.min(pq.size, MAX_RESULTS)
        for (i in 0 until recognitionsSize) {
            recognitions.add(pq.poll())
        }
        return recognitions
    }



    private fun getFilename(): String {
        val filepath = Environment.getExternalStorageDirectory().path
        val file = File(filepath, AUDIO_RECORDER_FOLDER)
        if (!file.exists()) {
            file.mkdirs()
        }
        return file.absolutePath.toString() + "/" + System.currentTimeMillis() +
                AUDIO_RECORDER_FILE_EXT_WAV
    }


    private fun copyWaveFile(
        inFilename: String,
        outFilename: String
    ) {
        var `in`: FileInputStream? = null
        var out: FileOutputStream? = null
        var totalAudioLen: Long = 0
        var totalDataLen = totalAudioLen + 36
        val longSampleRate = RECORDER_SAMPLERATE.toLong()
        val channels = 2
        val byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels / 8.toLong()
        val data = ByteArray(bufferSize)
        try {
            `in` = FileInputStream(inFilename)
            out = FileOutputStream(outFilename)
            totalAudioLen = `in`.getChannel().size()
            totalDataLen = totalAudioLen + 36
           // AppLog.logString("File size: $totalDataLen")
            WriteWaveFileHeader(
                out, totalAudioLen, totalDataLen,
                longSampleRate, channels, byteRate
            )
            while (`in`.read(data) !== -1) {
                out.write(data)
            }
            `in`.close()
            out.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun WriteWaveFileHeader(
        out: FileOutputStream, totalAudioLen: Long,
        totalDataLen: Long, longSampleRate: Long, channels: Int,
        byteRate: Long
    ) {
        val header = ByteArray(44)
        header[0] = 'R'.toByte() // RIFF/WAVE header
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.toByte()
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()
        header[12] = 'f'.toByte() // 'fmt ' chunk
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = (longSampleRate shr 8 and 0xff).toByte()
        header[26] = (longSampleRate shr 16 and 0xff).toByte()
        header[27] = (longSampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (2 * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = RECORDER_BPP.toByte() // bits per sample
        header[35] = 0
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()
        out.write(header, 0, 44)
    }

    private fun deleteTempFile() {
        val file = File(getTempFilename())
        file.delete()
    }


    @TargetApi(Build.VERSION_CODES.N)
    @SuppressLint("RestrictedApi")
    fun pauseRecording(){
        stopTimer()
        mediaRecorder?.pause()
    }

    @TargetApi(Build.VERSION_CODES.N)
    @SuppressLint("RestrictedApi")
    fun resumeRecording(){
        timer = Timer()
        startTimer()
        mediaRecorder?.resume()
    }

    private fun initRecorder() {
        mediaRecorder = MediaRecorder()

        if(dir.exists()){
            val count = dir.listFiles().size
            output = Environment.getExternalStorageDirectory().absolutePath + "/soundrecorder/recording"+count+".mp3"
        }

        mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder?.setOutputFile(output)
    }

    private fun startTimer(){
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                recordingTime += 1
                updateDisplay()
            }
        }, 1000, 1000)
    }

    private fun stopTimer(){
        timer.cancel()
    }


    private fun resetTimer() {
        timer.cancel()
        recordingTime = 0
        recordingTimeString.postValue("00:00")
    }

    private fun updateDisplay(){
        val minutes = recordingTime / (60)
        val seconds = recordingTime % 60
        val str = String.format("%d:%02d", minutes, seconds)
        recordingTimeString.postValue(str)
    }

    fun getRecordingTime() = recordingTimeString
}