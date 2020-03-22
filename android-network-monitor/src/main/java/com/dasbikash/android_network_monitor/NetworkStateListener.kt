package com.dasbikash.android_network_monitor

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.*

/**
 * ```
 * Listener class, instance of which can be registered to run task(s) on network state change.
 * If lifecycle-owner provided on init, then tasks will fire only if lifecycle-owner is not destroyed.
 * ```
 *
 * @author Bikash Das(das.bikash.dev@gmail.com)
 * @param id For internal usage
 * @param doOnConnected If non null then will be invoked when network becomes available
 * @param doOnDisConnected If non null then will be invoked when network becomes unavailable
 *
 * */
class NetworkStateListener private constructor(
    internal val id:String = UUID.randomUUID().toString(),
    private val doOnConnected:(()->Unit)?,
    private val doOnDisConnected:(()->Unit)?
):DefaultLifecycleObserver{
    private var isDestroyed = false
    override fun onDestroy(owner: LifecycleOwner) {
        isDestroyed = true
    }

    internal fun runOnConnected(){
        if (!isDestroyed){
            doOnConnected?.invoke()
        }
    }

    internal fun runOnDisConnected(){
        if (!isDestroyed){
            doOnDisConnected?.invoke()
        }
    }

    companion object{
        @JvmStatic
        fun getInstance(doOnConnected:(()->Unit)?=null,
                        doOnDisConnected:(()->Unit)?=null,
                        lifecycleOwner: LifecycleOwner?=null):NetworkStateListener {
            val networkStateListener = NetworkStateListener(
                                            doOnConnected = doOnConnected,
                                            doOnDisConnected = doOnDisConnected)
            lifecycleOwner?.lifecycle?.addObserver(networkStateListener)
            return networkStateListener
        }

    }
}