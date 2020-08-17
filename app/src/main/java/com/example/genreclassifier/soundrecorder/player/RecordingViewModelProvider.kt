package com.example.genreclassifier.soundrecorder.player

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import com.example.genreclassifier.soundrecorder.recorder.RecorderRepository
import com.example.genreclassifier.soundrecorder.recorder.RecorderViewModel

class RecordingViewModelProvider(val recordingRepository: RecordingRepository): ViewModelProvider.NewInstanceFactory(){
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return RecordingViewModel(recordingRepository) as T
    }
}