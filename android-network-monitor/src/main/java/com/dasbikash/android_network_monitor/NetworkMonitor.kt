package com.dasbikash.android_network_monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.IllegalStateException

/**
 * Helper class for network state detection
 *
 * 'fun initialize(context: Context)' must be called
 * on app start to to activate network state listener
 *
 * @author Bikash Das
 */
class NetworkMonitor : DefaultLifecycleObserver {

    companion object{

        private const val NO_INTERNET_TOAST_MESSAGE = "No internet connection!!!"
        private const val NOT_INITIALIZED_MESSAGE = "\"NetworkMonitor\" is not initialized!! Please call \"init\" first."

        @Volatile
        private var INSTANCE: NetworkMonitor?=null

        private enum class NETWORK_TYPE {
            MOBILE, WIFI, WIMAX, ETHERNET, BLUETOOTH, DC, OTHER, UN_INITIALIZED
        }

        private fun checkInitStatus(){
            if (INSTANCE == null){
                throw IllegalStateException(NOT_INITIALIZED_MESSAGE)
            }
        }

        @JvmStatic
        fun init(activity: AppCompatActivity): Boolean {
            if (INSTANCE == null) {
                if (INSTANCE == null) {
                    GlobalScope.launch {
                        INSTANCE = NetworkMonitor()
                        INSTANCE!!.initialize(activity)
                    }
                    return true
                }
            }
            return false
        }

        @JvmStatic
        fun init(fragment: Fragment): Boolean {
            fragment.activity?.let {
                if (it is AppCompatActivity){
                    return init(it)
                }
            }
            return false
        }

        @JvmStatic
        fun isConnected():Boolean{
            checkInitStatus()
            return INSTANCE!!.checkIfConnected()
        }

        @JvmStatic
        fun isOnWify():Boolean{
            checkInitStatus()
            return INSTANCE!!.checkIfOnWify()
        }

        @JvmStatic
        fun isOnMobileDataNetwork():Boolean{
            checkInitStatus()
            return INSTANCE!!.checkIfOnMobileDataNetwork()
        }

        @JvmStatic
        fun addNetworkStateListener(networkStateListener: NetworkStateListener) {
            checkInitStatus()
            INSTANCE!!.registerNetworkStateListener(networkStateListener)
        }

        @JvmStatic
        fun removeNetworkStateListener(networkStateListener: NetworkStateListener){
            checkInitStatus()
            INSTANCE!!.unRegisterNetworkStateListener(networkStateListener)
        }

        fun showNoInternetToast(context: Context) {
            checkInitStatus()
            return INSTANCE!!.showNoInternetToast(context)
        }

        fun showNoInternetToastAnyWay(context: Context) {
            checkInitStatus()
            return INSTANCE!!.showNoInternetToastAnyWay(context)
        }
    }

    private var mNoInternetToastShown = false
    private var mReceiverRegistered = false
    private var mCurrentNetworkType = NETWORK_TYPE.UN_INITIALIZED
    private val CONNECTIVITY_CHANGE_FILTER = "android.net.conn.CONNECTIVITY_CHANGE"
    private val intentFilterForConnectivityChangeBroadcastReceiver: IntentFilter
        get() = IntentFilter(CONNECTIVITY_CHANGE_FILTER)

    private val broadcastReceiver = object : BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            debugLog("onReceive")
            if (intent != null && intent.action != null &&
                intent.action!!.equals(CONNECTIVITY_CHANGE_FILTER, ignoreCase = true)) {
                context?.let {
                    refreshNetworkType(it)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun refreshNetworkType(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val activeNetworkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo

        if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                mCurrentNetworkType = when (activeNetworkInfo.type) {
                    ConnectivityManager.TYPE_WIFI -> NETWORK_TYPE.WIFI
                    ConnectivityManager.TYPE_MOBILE,
                    ConnectivityManager.TYPE_MOBILE_DUN,
                    ConnectivityManager.TYPE_MOBILE_HIPRI,
                    ConnectivityManager.TYPE_MOBILE_MMS,
                    ConnectivityManager.TYPE_MOBILE_SUPL -> NETWORK_TYPE.MOBILE
                    ConnectivityManager.TYPE_BLUETOOTH -> NETWORK_TYPE.BLUETOOTH
                    ConnectivityManager.TYPE_ETHERNET -> NETWORK_TYPE.ETHERNET
                    ConnectivityManager.TYPE_WIMAX -> NETWORK_TYPE.WIMAX
                    else -> NETWORK_TYPE.OTHER
                }
            } else {
                val network = connectivityManager.activeNetwork
                val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false ||
                    networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE) ?: false) {
                    mCurrentNetworkType =
                        NETWORK_TYPE.WIFI
                } else if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)  ?: false) {
                    mCurrentNetworkType =
                        NETWORK_TYPE.MOBILE
                } else if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)  ?: false) {
                    mCurrentNetworkType =
                        NETWORK_TYPE.BLUETOOTH
                } else if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)  ?: false) {
                    mCurrentNetworkType =
                        NETWORK_TYPE.ETHERNET
                } else {
                    mCurrentNetworkType =
                        NETWORK_TYPE.OTHER
                }
            }
            mNoInternetToastShown = false
        } else {
            mCurrentNetworkType =
                NETWORK_TYPE.DC
        }
        debugLog("Current Network Type: ${mCurrentNetworkType.name}")
        invokeNetworkStateListeners()
    }

    private fun debugLog(message: String) {
        Log.d(this.javaClass.simpleName, message)
    }

    /**
     * To check if connected to mobile network
     *
     * @return true if connected to mobile network else false
     * */
    private fun checkIfOnMobileDataNetwork(): Boolean
            = mCurrentNetworkType == NETWORK_TYPE.MOBILE

    /**
     * To check if connected to Wify network
     *
     * @return true if connected to mobile network else false
     * */
    private fun checkIfOnWify(): Boolean = mCurrentNetworkType == NETWORK_TYPE.WIFI

    /**
     * To check network connectivity status
     *
     * @return true if connected else false
     * */
    private fun checkIfConnected(): Boolean =
        mCurrentNetworkType != NETWORK_TYPE.DC &&
                mCurrentNetworkType != NETWORK_TYPE.UN_INITIALIZED

    /**
     * Method to initialize class. Should be called on app start up.
     *
     * @param context Android Context
     * */
    private fun resisterBroadcastReceiver(activity: AppCompatActivity):Boolean {
        if (!mReceiverRegistered) {
            activity.applicationContext.registerReceiver(broadcastReceiver,
                intentFilterForConnectivityChangeBroadcastReceiver
            )
            mReceiverRegistered = true
            return true
        }
        return false
    }

    private fun initialize(activity: AppCompatActivity):Boolean {
        return resisterBroadcastReceiver(activity).apply {
            if (this) {
                activity.lifecycle.addObserver(this@NetworkMonitor)
            }
        }
    }

    /**
     * Will show no internet message toast
     * if no internet and toast not shown already.
     *
     * @param context Android Context
     * */
    private fun showNoInternetToast(context: Context) {
        if (!checkIfConnected() && !mNoInternetToastShown) {
            mNoInternetToastShown = true
            showShortToast(context,NO_INTERNET_TOAST_MESSAGE)
        }
    }

    /**
     * Will show no internet message toast if no internet.
     *
     * @param context Android Context
     * */
    private fun showNoInternetToastAnyWay(context: Context) {
        if (!checkIfConnected()) {
            mNoInternetToastShown = true
            showShortToast(context,NO_INTERNET_TOAST_MESSAGE)
        }
    }

    private fun showShortToast(context: Context,message: String) =
        Toast.makeText(context,message, Toast.LENGTH_SHORT).show()

    private fun registerNetworkStateListener(networkStateListener: NetworkStateListener) {
        unRegisterNetworkStateListener(networkStateListener)
        mNetworkStateListenerMap[networkStateListener.id] = networkStateListener
    }

    private fun unRegisterNetworkStateListener(networkStateListener: NetworkStateListener) =
        mNetworkStateListenerMap.remove(networkStateListener.id)

    private val mNetworkStateListenerMap = mutableMapOf<String,NetworkStateListener>()

    private fun invokeNetworkStateListeners(){
        GlobalScope.launch(Dispatchers.IO) {
            mNetworkStateListenerMap.values.asSequence().forEach {
                runOnMainThread({
                    if (checkIfConnected()){
                        it.doOnConnected?.invoke()
                    }else{
                        it.doOnDisConnected?.invoke()
                    }
                })
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        mNetworkStateListenerMap.clear()
        (owner as AppCompatActivity).applicationContext.unregisterReceiver(broadcastReceiver)
        INSTANCE = null
    }

    private fun runOnMainThread(task: () -> Any?){
        Handler(Looper.getMainLooper()).post( { task() })
    }
}

internal val NOT_APP_COMPAT_ACTIVITY_OR_FRAGMENT_MSG = "Should be called only from AppCompatActivity/Fragment!!"
internal fun throwNotAppCompatActivityOrFragmentException() {
    throw IllegalStateException(NOT_APP_COMPAT_ACTIVITY_OR_FRAGMENT_MSG)
}

fun LifecycleOwner.initNetworkMonitor() {
    when (this) {
        is AppCompatActivity -> NetworkMonitor.init(this)
        is Fragment -> NetworkMonitor.init(this)
        else -> {
            throwNotAppCompatActivityOrFragmentException()
        }
    }
}

fun LifecycleOwner.haveNetworkConnection():Boolean {
    return when  {
        this is AppCompatActivity || this is Fragment -> NetworkMonitor.isConnected()
        else -> {
            throwNotAppCompatActivityOrFragmentException()
            return false
        }
    }
}

fun LifecycleOwner.isOnWify():Boolean {
    return when  {
        this is AppCompatActivity || this is Fragment -> NetworkMonitor.isOnWify()
        else -> {
            throwNotAppCompatActivityOrFragmentException()
            return false
        }
    }
}

fun LifecycleOwner.isOnMobileDataNetwork():Boolean {
    return when  {
        this is AppCompatActivity || this is Fragment -> NetworkMonitor.isOnMobileDataNetwork()
        else -> {
            throwNotAppCompatActivityOrFragmentException()
            return false
        }
    }
}