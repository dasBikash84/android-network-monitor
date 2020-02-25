package com.dasbikash.android_network_monitor

import java.util.*

/**
 * Listener class, instance of which can be registered to run task(s) on network state change.
 *
 * @author Bikash Das(das.bikash.dev@gmail.com)
 * @param id For internal usage
 * @param doOnConnected If non null then will be invoked when network becomes available
 * @param doOnDisConnected If non null then will be invoked when network becomes unavailable
 *
 * */
class NetworkStateListener private constructor(
    val id:String = UUID.randomUUID().toString(),
    val doOnConnected:(()->Any?)?,
    val doOnDisConnected:(()->Any?)?
){
    companion object{
        @JvmStatic
        fun getInstance(doOnConnected:(()->Any?)?=null,
                        doOnDisConnected:(()->Any?)?=null):NetworkStateListener =
            NetworkStateListener(doOnConnected = doOnConnected,
                                    doOnDisConnected = doOnDisConnected)

    }
}