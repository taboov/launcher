package com.mrmannwood.hexlauncher.colorpicker

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ColorPickerViewModel : ViewModel() {
    val colorLiveData = MutableLiveData<Int>()
    val completionLiveData = MutableLiveData<Boolean>()
    val cancellationLiveData = MutableLiveData<Boolean>()
}