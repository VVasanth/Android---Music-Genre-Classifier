package com.example.genreclassifier.soundrecorder.util

import android.content.Context
import com.example.genreclassifier.soundrecorder.player.RecordingRepository
import com.example.genreclassifier.soundrecorder.player.RecordingViewModelProvider
import com.example.genreclassifier.soundrecorder.recorder.RecorderRepository
import com.example.genreclassifier.soundrecorder.recorder.RecorderViewModelProvider

object InjectorUtils {
    fun provideRecorderViewModelFactory(): RecorderViewModelProvider {
        val recorderRepository = RecorderRepository.getInstance()
        return RecorderViewModelProvider(recorderRepository)
    }

    fun provideRecordingViewModelFactory(): RecordingViewModelProvider {
        val recordingRepository = RecordingRepository.getInstance()
        return RecordingViewModelProvider(recordingRepository)
    }
}