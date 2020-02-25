package com.dasbikash.android_network_monitor

import java.util.*

class NetworkStateListener private constructor(
    val id:String = UUID.randomUUID().toString(),
    val doOnConnected:()->Any?,
    val doOnDisConnected:()->Any?
){
    companion object{
        fun getInstance(doOnConnected:()->Any?,
                        doOnDisConnected:()->Any?):NetworkStateListener =
            NetworkStateListener(doOnConnected = doOnConnected,
                                    doOnDisConnected = doOnDisConnected)

    }
}