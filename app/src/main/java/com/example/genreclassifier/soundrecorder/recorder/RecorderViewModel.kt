package com.example.genreclassifier.soundrecorder.recorder

import android.arch.lifecycle.ViewModel
import android.content.Context
import com.example.genreclassifier.soundrecorder.util.RecorderState

class RecorderViewModel(val recorderRepository: RecorderRepository): ViewModel() {

    var recorderState: RecorderState = RecorderState.Stopped

    fun startRecording() = recorderRepository.startRecording()

    fun stopRecording(context: Context) = recorderRepository.stopRecording(context)

    fun pauseRecording() = recorderRepository.pauseRecording()

    fun resumeRecording() = recorderRepository.resumeRecording()

    fun getRecordingTime() = recorderRepository.getRecordingTime()

}