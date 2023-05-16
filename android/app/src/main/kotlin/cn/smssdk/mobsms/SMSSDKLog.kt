package cn.smssdk.mobsms

import android.util.Log

object SMSSDKLog {

    private val TAG = "SMS_Flutter"

    fun d(msg: String): Int {
        return Log.d(TAG, msg)
    }

    fun d(msg: String, t: Throwable?): Int {
        return Log.d(TAG, msg, t)
    }

    fun i(msg: String): Int {
        return Log.i(TAG, msg)
    }

    fun i(msg: String?, t: Throwable?): Int {
        return Log.i(TAG, msg, t)
    }

    fun w(msg: String): Int {
        return Log.w(TAG, msg)
    }

    fun w(msg: String?, t: Throwable?): Int {
        return Log.w(TAG, msg, t)
    }

    fun e(msg: String): Int {
        return Log.e(TAG, msg)
    }

    fun e(msg: String?, t: Throwable?): Int {
        return Log.e(TAG, msg, t)
    }
}