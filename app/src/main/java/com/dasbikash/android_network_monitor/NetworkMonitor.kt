package com.dasbikash.android_network_monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dasbikash.android_extensions.runOnMainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Helper class for network state detection
 *
 * 'fun initialize(context: Context)' must be called
 * on app start to to activate network state listener
 *
 * @author Bikash Das
 */
class NetworkMonitor : BroadcastReceiver(), DefaultLifecycleObserver {

    companion object{

        @Volatile
        private var INSTANCE: NetworkMonitor?=null

        private const val NO_INTERNET_TOAST_MESSAGE = "No internet connection!!!"

        private enum class NETWORK_TYPE {
            MOBILE, WIFI, WIMAX, ETHERNET, BLUETOOTH, DC, OTHER, UN_INITIALIZED
        }

        @JvmStatic
        fun init(activity: AppCompatActivity): Boolean {
            if (INSTANCE == null) {
                synchronized(NetworkMonitor::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = NetworkMonitor()
                        INSTANCE!!.inititialize(activity)
                        return true
                    }
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
        fun getInstance():NetworkMonitor? = INSTANCE
    }

    private var mNoInternetToastShown = false
    private var mReceiverRegistered = false
    private var mCurrentNetworkType = NETWORK_TYPE.UN_INITIALIZED


    override fun onReceive(context: Context, intent: Intent?) {
        if (intent != null && intent.action != null &&
                intent.action!!.equals(CONNECTIVITY_CHANGE_FILTER, ignoreCase = true)) {
            refreshNetworkType(context)
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
        Log.d(this.javaClass.simpleName,"Current Network Type: ${mCurrentNetworkType.name}")
        invokeNetworkStateListeners()
    }

    private val CONNECTIVITY_CHANGE_FILTER = "android.net.conn.CONNECTIVITY_CHANGE"

    private val intentFilterForConnectivityChangeBroadcastReceiver: IntentFilter
        get() = IntentFilter(CONNECTIVITY_CHANGE_FILTER)

    /**
     * To check if connected to mobile network
     *
     * @return true if connected to mobile network else false
     * */
    fun isOnMobileDataNetwork(): Boolean
        = mCurrentNetworkType == NETWORK_TYPE.MOBILE

    /**
     * To check if connected to Wify network
     *
     * @return true if connected to mobile network else false
     * */
    fun isOnWify(): Boolean = mCurrentNetworkType == NETWORK_TYPE.WIFI

    /**
     * To check network connectivity status
     *
     * @return true if connected else false
     * */
    fun isConnected(): Boolean =
        mCurrentNetworkType != NETWORK_TYPE.DC &&
            mCurrentNetworkType != NETWORK_TYPE.UN_INITIALIZED

    /**
     * Method to initialize class. Should be called on app start up.
     *
     * @param context Android Context
     * */
    private fun resisterBroadcastReceiver(activity: AppCompatActivity):Boolean {
        if (!mReceiverRegistered) {
            activity.applicationContext.registerReceiver(this,
                intentFilterForConnectivityChangeBroadcastReceiver
            )
            mReceiverRegistered = true
            return true
        }
        return false
    }

    private fun inititialize(activity: AppCompatActivity):Boolean {
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
    fun showNoInternetToast(context: Context) {
        if (!isConnected() && !mNoInternetToastShown) {
            mNoInternetToastShown = true
            showShortToast(context,NO_INTERNET_TOAST_MESSAGE)
        }
    }

    /**
     * Will show no internet message toast if no internet.
     *
     * @param context Android Context
     * */
    fun showNoInternetToastAnyWay(context: Context) {
        if (!isConnected()) {
            mNoInternetToastShown = true
            showShortToast(context,NO_INTERNET_TOAST_MESSAGE)
        }
    }

    private fun showShortToast(context: Context,message: String) =
        Toast.makeText(context,message, Toast.LENGTH_SHORT).show()

    fun addNetworkStateListener(networkStateListener: NetworkStateListener) {
        removeNetworkStateListener(networkStateListener)
        mNetworkStateListenerMap.put(networkStateListener.id,networkStateListener)
    }

    fun removeNetworkStateListener(networkStateListener: NetworkStateListener) =
        mNetworkStateListenerMap.remove(networkStateListener.id)

    private val mNetworkStateListenerMap = mutableMapOf<String,NetworkStateListener>()

    private fun invokeNetworkStateListeners(){
        GlobalScope.launch(Dispatchers.IO) {
            mNetworkStateListenerMap.values.asSequence().forEach {
                runOnMainThread({
                    if (isConnected()){
                        it.doOnConnected()
                    }else{
                        it.doOnDisConnected
                    }
                })
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        INSTANCE = null
        mNetworkStateListenerMap.clear()
        (owner as AppCompatActivity).unregisterReceiver(this)
    }
}
