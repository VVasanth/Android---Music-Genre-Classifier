package com.example.genreclassifier.soundrecorder.player

import android.arch.lifecycle.ViewModel

class RecordingViewModel(val recordingRepository: RecordingRepository): ViewModel(){

    fun getRecordings() = recordingRepository.getRecordings()
}