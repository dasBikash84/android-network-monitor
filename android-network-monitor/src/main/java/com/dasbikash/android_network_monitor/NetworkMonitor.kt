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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.IllegalStateException
import java.util.concurrent.atomic.AtomicLong

/**
 * ```
 * Helper class for monitoring network status and optionally
 * attach listeners on network state change.
 * ```
 * ### Initialization (any one of below two)
 * * `NetworkMonitor.init(activity)`
 * * `NetworkMonitor.init(fragment)`
 *
 *
 * ### Usage
 * * For checking network connection status : `NetworkMonitor.isConnected()`
 * * For checking if connected to wify network : `NetworkMonitor.isOnWify()`
 * * For checking if connected to cellular network : `NetworkMonitor.isOnMobileDataNetwork()`
 * * To register Network State Listener : `NetworkMonitor.addNetworkStateListener(networkStateListener)`
 * * To un-register Network State Listener : `NetworkMonitor.removeNetworkStateListener(networkStateListener)`
 *
 * @author Bikash Das(das.bikash.dev@gmail.com)
 */
class NetworkMonitor {

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

        /**
         * Initialize `NetworkMonitor` using AppCompatActivity instance
         *
         * @param activity AppCompatActivity
         * @return `true` for init success
         * */
        @JvmStatic
        fun init(context: Context) {
            INSTANCE?.clearInstance(context)
            INSTANCE = null
            GlobalScope.launch {
                INSTANCE = NetworkMonitor()
                INSTANCE?.initialize(context)
            }
        }

        /**
         * For checking network connection status
         *
         * @throws IllegalStateException If `NetworkMonitor` is not initialized
         * @return `true` if connected
         * */
        @JvmStatic
        fun isConnected():Boolean{
            checkInitStatus()
            return INSTANCE!!.checkIfConnected()
        }

        /**
         * For checking if connected to wify network
         *
         * @throws IllegalStateException If `NetworkMonitor` is not initialized
         * @return `true` if connected to wify network
         * */
        @JvmStatic
        fun isOnWify():Boolean{
            checkInitStatus()
            return INSTANCE!!.checkIfOnWify()
        }

        /**
         * For checking if connected to cellular network
         *
         * @throws IllegalStateException If `NetworkMonitor` is not initialized
         * @return `true` if connected to cellular network
         * */
        @JvmStatic
        fun isOnMobileDataNetwork():Boolean{
            checkInitStatus()
            return INSTANCE!!.checkIfOnMobileDataNetwork()
        }

        /**
         * To register Network State Listener
         *
         * @throws IllegalStateException If `NetworkMonitor` is not initialized
         * @param networkStateListener NetworkStateListener instance for addition
         * */
        @JvmStatic
        fun addNetworkStateListener(networkStateListener: NetworkStateListener) {
            checkInitStatus()
            INSTANCE!!.registerNetworkStateListener(networkStateListener)
        }

        /**
         * To remove Network State Listener
         *
         * @throws IllegalStateException If `NetworkMonitor` is not initialized
         * @param networkStateListener NetworkStateListener instance for removal
         * */
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

        @JvmStatic
        fun <T> runWithNetwork(context: Context,task:()->T?):Boolean{
            if (isConnected()){
                task()
                return true
            }else{
                showNoInternetToastAnyWay(context)
                return false
            }
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
    private fun resisterBroadcastReceiver(context: Context):Boolean {
        if (!mReceiverRegistered) {
            context.applicationContext.registerReceiver(broadcastReceiver,
                intentFilterForConnectivityChangeBroadcastReceiver
            )
            mReceiverRegistered = true
            return true
        }
        return false
    }

    private fun initialize(context: Context):Boolean {
        return resisterBroadcastReceiver(context)
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
        mNetworkStateListenerMap[networkStateListener.id] = networkStateListener
    }

    private fun unRegisterNetworkStateListener(networkStateListener: NetworkStateListener) =
        mNetworkStateListenerMap.remove(networkStateListener.id)

    private val mNetworkStateListenerMap = mutableMapOf<String,NetworkStateListener>()

    private var lastOnConnectedInvokeTime = AtomicLong(System.currentTimeMillis())
    private var lastOnDisConnectedInvokeTime = AtomicLong(System.currentTimeMillis())

    private val MIN_NETWORK_STATE_LISTENER_INVOKE_INTERVAL = 3000L

    private fun invokeNetworkStateListeners(){
        GlobalScope.launch(Dispatchers.IO) {
            if (when{
                    checkIfConnected() -> {
                        if (System.currentTimeMillis() - lastOnConnectedInvokeTime.get() >
                            MIN_NETWORK_STATE_LISTENER_INVOKE_INTERVAL){
                            lastOnConnectedInvokeTime.getAndSet(System.currentTimeMillis())
                            true
                        }else{
                            false
                        }
                    }
                    else -> {
                        if (System.currentTimeMillis() - lastOnDisConnectedInvokeTime.get() >
                            MIN_NETWORK_STATE_LISTENER_INVOKE_INTERVAL){
                            lastOnDisConnectedInvokeTime.getAndSet(System.currentTimeMillis())
                            true
                        }else{
                            false
                        }
                    }
                }) {
                mNetworkStateListenerMap.values.asSequence().forEach {
                    runOnMainThread({
                        when (checkIfConnected()) {
                            true -> it.runOnConnected()
                            false -> it.runOnDisConnected()
                        }
                    })
                }
            }
        }
    }

    internal fun clearInstance(context: Context){
        context.applicationContext.unregisterReceiver(broadcastReceiver)
        mNetworkStateListenerMap.clear()
    }

    private fun runOnMainThread(task: () -> Any?){
        Handler(Looper.getMainLooper()).post( { task() })
    }
}

internal val NOT_APP_COMPAT_ACTIVITY_OR_FRAGMENT_MSG = "Should be called only from AppCompatActivity/Fragment!!"
internal fun throwNotAppCompatActivityOrFragmentException() {
    throw IllegalStateException(NOT_APP_COMPAT_ACTIVITY_OR_FRAGMENT_MSG)
}

/**
 * Extension to initialize `NetworkMonitor` from `AppCompatActivity`
 *
 * */
fun AppCompatActivity.initNetworkMonitor() = NetworkMonitor.init(this)

/**
 * Extension to initialize `NetworkMonitor` from `Fragment`
 *
 * */
fun Fragment.initNetworkMonitor() = context?.let { NetworkMonitor.init(it)}

/**
 * Extension to check if connected to network
 *
 * @throws IllegalStateException If `NetworkMonitor` is not initialized
 * */
fun Any.haveNetworkConnection():Boolean = NetworkMonitor.isConnected()

/**
 * Extension to check if connected to wify network
 *
 * @throws IllegalStateException If `NetworkMonitor` is not initialized
 * */
fun Any.isOnWify():Boolean = NetworkMonitor.isOnWify()

/**
 * Extension to check if connected to mobile network
 *
 * @throws IllegalStateException If `NetworkMonitor` is not initialized
 * */
fun Any.isOnMobileDataNetwork():Boolean = NetworkMonitor.isOnMobileDataNetwork()

/**
 * Extension to register Network State Listener
 *
 * */
fun Any.addNetworkStateListener(networkStateListener:NetworkStateListener) = NetworkMonitor.addNetworkStateListener(networkStateListener)

/**
 * Extension to remove Network State Listener
 *
 * */
fun Any.removeNetworkStateListener(networkStateListener:NetworkStateListener) = NetworkMonitor.removeNetworkStateListener(networkStateListener)