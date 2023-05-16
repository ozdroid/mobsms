package cn.smssdk.mobsms

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import androidx.annotation.NonNull
import cn.smssdk.EventHandler
import cn.smssdk.SMSSDK
import cn.smssdk.mobsms.SMSSDKLog.d
import cn.smssdk.mobsms.SMSSDKLog.e
import cn.smssdk.utils.SPHelper
import cn.smssdk.wrapper.TokenVerifyResult
import com.mob.MobSDK
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import org.json.JSONException
import org.json.JSONObject
import java.lang.IllegalStateException


/** MobsmsPlugin */
class MobsmsPlugin: FlutterPlugin, MethodCallHandler {

  private val TAG = "MobsmsPlugin"
  val CHANNEL = "com.mob.smssdk"
  private val KEY_CODE = "code"
  private val KEY_MSG = "msg"
  private val BRIDGE_ERR = 700
  private val ERROR_INTERNAL = "Flutter bridge internal error: "
  private var tokenVerifyResult: TokenVerifyResult? = null

  private lateinit var channel : MethodChannel

  /** Plugin registration.  */
  fun registerWith(registrar: Registrar) {
    SMSSDKLog.d("registerWith() called")
    val channel = MethodChannel(registrar.messenger(), CHANNEL)
    channel.setMethodCallHandler(MobsmsPlugin())
  }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    SMSSDKLog.d("onAttachedToEngine called")
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "mobsms")
    channel.setMethodCallHandler(this)
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull rst: Result) {
    SMSSDKLog.d("onMethodCall. method: " + call.method)
    when (call.method) {
      "getTextCode" -> getTextCode(call, rst)
      "getVoiceCode" -> getVoiceCode(call, rst)
      "commitCode" -> commitCode(call, rst)
      "getSupportedCountries" -> getSupportedCountries(call, rst)
      "login" -> login(call, rst)
      "getToken" -> getToken(call, rst)
      "submitUserInfo" -> submitUserInfo(call, rst)
      "getVersion" -> getVersion(call, rst)
      "enableWarn" -> enableWarn(call, rst)
      "uploadPrivacyStatus" -> uploadPrivacyStatus(call, rst)
      "getPlatformVersion" ->getPlatformVersion(call, rst)
      else -> rst.notImplemented()
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    SMSSDKLog.d("onDetachedFromEngine called")
    channel.setMethodCallHandler(null)
    SMSSDK.unregisterAllEventHandler()
  }

  ////////////////////////////////////////////////////////////////////////////////////////////
  //获得系统版本
  private fun getPlatformVersion(call:MethodCall , result:Result){
    result.success("Android ${android.os.Build.VERSION.RELEASE}")
  }

  //更新隐私
  private fun uploadPrivacyStatus(call: MethodCall, rst: Result) {
    if (call.hasArgument("status")) {
      val grantResult = call.argument<Boolean>("status")
      MobSDK.submitPolicyGrantResult(grantResult!!, null)
    }
  }

  //开启警告
  private fun enableWarn(call: MethodCall, rst: Result) {
    val isWarn = call.argument<Boolean>("isWarn")!!
    SPHelper.getInstance().setWarnWhenReadContact(isWarn)
    val map: Map<String, Any> = HashMap()
    onSuccess(rst, map)
  }

  //获得SMSSDK版本
  private fun getVersion(call: MethodCall, rst: Result) {
    val version = SMSSDK.getVersion()
    val map: MutableMap<String, Any> = HashMap()
    map["version"] = version
    onSuccess(rst, map)
  }

  //提交用户信息
  private fun submitUserInfo(call: MethodCall, rst: Result) {
    // 注册监听器
    val callback: EventHandler = object : EventHandler() {
      override fun afterEvent(event: Int, result: Int, data: Any?) {
        if (result == SMSSDK.RESULT_COMPLETE) {
          if (event == SMSSDK.EVENT_SUBMIT_USER_INFO) {
            // callback onSuccess
            // data示例：{}
            val map: Map<String, Any> = HashMap()
            onSuccess(rst, map)
          }
        } else {
          if (event == SMSSDK.EVENT_SUBMIT_USER_INFO) {
            // callback onError
            if (data is Throwable) {
              val msg = data.message
              onSdkError(rst, msg!!)
            } else {
              val msg = "Sdk returned 'RESULT_ERROR', but the data is NOT an instance of Throwable"
              e("submitUserInfo() internal error: $msg")
              onInternalError(rst, msg)
            }
          }
        }
      }
    }
    // Flutter的Result对象只能返回一次数据，同一个Result对象如果再次提交数据会crash（错误信息：数据已被提交过），所以要把前一次的EventHandler注销掉
    // 否则重复调用统一个接口时，smssdk会针对所有EventHandler发送回调，旧的Result对象就会被触发，导致Flutter层crash
    SMSSDK.unregisterAllEventHandler()
    SMSSDK.registerEventHandler(callback)
    val zone = call.argument<String>("country")
    val phoneNumber = call.argument<String>("phone")
    val uid = call.argument<String>("uid")
    val nickname = call.argument<String>("nickname")
    val avatar = call.argument<String>("avatar")
    d("zone: $zone")
    d("phoneNumber: $phoneNumber")
    d("uid: $uid")
    d("nickname: $nickname")
    d("avatar: $avatar")
    SMSSDK.submitUserInfo(uid, nickname, avatar, zone, phoneNumber)
  }

  private fun getToken(call: MethodCall, rst: Result) {
    // 注册监听器
    val callback: EventHandler = object : EventHandler() {
      override fun afterEvent(event: Int, result: Int, data: Any) {
        if (result == SMSSDK.RESULT_COMPLETE) {
          if (event == SMSSDK.EVENT_GET_VERIFY_TOKEN_CODE) {
            tokenVerifyResult = data as TokenVerifyResult
            val map: MutableMap<String, Any> = HashMap()
            map["opToken"] = tokenVerifyResult!!.getOpToken()
            map["token"] = tokenVerifyResult!!.getToken()
            map["operator"] = tokenVerifyResult!!.getOperator()
            onSuccess(rst, map)
          }
        } else {
          if (event == SMSSDK.EVENT_GET_VERIFY_TOKEN_CODE) {
            // callback onError
            if (data is Throwable) {
              val msg = data.message
              onSdkError(rst, msg!!)
            } else {
              val msg = "Sdk returned 'RESULT_ERROR', but the data is NOT an instance of Throwable"
              e("getToken() internal error: $msg")
              onInternalError(rst, msg)
            }
          }
        }
      }
    }
    // Flutter的Result对象只能返回一次数据，同一个Result对象如果再次提交数据会crash（错误信息：数据已被提交过），所以要把前一次的EventHandler注销掉
    // 否则重复调用统一个接口时，smssdk会针对所有EventHandler发送回调，旧的Result对象就会被触发，导致Flutter层crash
    SMSSDK.unregisterAllEventHandler()
    SMSSDK.registerEventHandler(callback)
    SMSSDK.getToken()
  }

  private fun login(call: MethodCall, rst: Result) {
    // 注册监听器
    val callback: EventHandler = object : EventHandler() {
      override fun afterEvent(event: Int, result: Int, data: Any) {
        if (result == SMSSDK.RESULT_COMPLETE) {
          if (event == SMSSDK.EVENT_VERIFY_LOGIN) {
            tokenVerifyResult = null
            val map: MutableMap<String, Any> = HashMap()
            map["success"] = true
            onSuccess(rst, map)
          }
        } else {
          if (event == SMSSDK.EVENT_VERIFY_LOGIN) {
            // callback onError
            tokenVerifyResult = null
            if (data is Throwable) {
              val msg = data.message
              onSdkError(rst, msg!!)
            } else {
              val msg = "Sdk returned 'RESULT_ERROR', but the data is NOT an instance of Throwable"
              e("login() internal error: $msg")
              onInternalError(rst, msg)
            }
          }
        }
      }
    }
    // Flutter的Result对象只能返回一次数据，同一个Result对象如果再次提交数据会crash（错误信息：数据已被提交过），所以要把前一次的EventHandler注销掉
    // 否则重复调用统一个接口时，smssdk会针对所有EventHandler发送回调，旧的Result对象就会被触发，导致Flutter层crash
    SMSSDK.unregisterAllEventHandler()
    SMSSDK.registerEventHandler(callback)
    val phoneNumber = call.argument<String>("phoneNumber")
    if (tokenVerifyResult == null) {
      try {
        val errorJson = JSONObject()
        errorJson.putOpt("detail", "请先调用获取token方法")
        onSdkError(rst, errorJson.toString())
      } catch (e: JSONException) {
      }
    } else {
      SMSSDK.login(phoneNumber, tokenVerifyResult)
    }
  }

  private fun getSupportedCountries(call: MethodCall, rst: Result) {
    // 注册监听器
    val callback: EventHandler = object : EventHandler() {
      override fun afterEvent(event: Int, result: Int, data: Any) {
        if (result == SMSSDK.RESULT_COMPLETE) {
          if (event == SMSSDK.EVENT_GET_SUPPORTED_COUNTRIES) {
            // callback onSuccess
            // data示例：[{zone=590, rule=^\d+}, {zone=680, rule=^\d+}]
            val list = data as ArrayList<HashMap<String, Any>>
            val map: MutableMap<String, Any> = HashMap()
            map["countries"] = list
            onSuccess(rst, map)
          }
        } else {
          if (event == SMSSDK.EVENT_GET_SUPPORTED_COUNTRIES) {
            // callback onError
            if (data is Throwable) {
              val msg = data.message
              onSdkError(rst, msg!!)
            } else {
              val msg = "Sdk returned 'RESULT_ERROR', but the data is NOT an instance of Throwable"
              e("getSupportedCountries() internal error: $msg")
              onInternalError(rst, msg)
            }
          }
        }
      }
    }
    // Flutter的Result对象只能返回一次数据，同一个Result对象如果再次提交数据会crash（错误信息：数据已被提交过），所以要把前一次的EventHandler注销掉
    // 否则重复调用统一个接口时，smssdk会针对所有EventHandler发送回调，旧的Result对象就会被触发，导致Flutter层crash
    SMSSDK.unregisterAllEventHandler()
    SMSSDK.registerEventHandler(callback)
    SMSSDK.getSupportedCountries()
  }

  private fun commitCode(call: MethodCall, rst: Result) {
    // 注册监听器
    val callback: EventHandler = object : EventHandler() {
      override fun afterEvent(event: Int, result: Int, data: Any) {
        if (result == SMSSDK.RESULT_COMPLETE) {
          if (event == SMSSDK.EVENT_SUBMIT_VERIFICATION_CODE) {
            // callback onSuccess
            // data示例：{country=86, phone=13362206853}
            val dataMap = data as HashMap<String, Any>
            val map: Map<String, Any> = HashMap()
            onSuccess(rst, map)
          }
        } else {
          if (event == SMSSDK.EVENT_SUBMIT_VERIFICATION_CODE) {
            // callback onError
            if (data is Throwable) {
              val msg = data.message
              onSdkError(rst, msg!!)
            } else {
              val msg = "Sdk returned 'RESULT_ERROR', but the data is NOT an instance of Throwable"
              e("commitCode() internal error: $msg")
              onInternalError(rst, msg)
            }
          }
        }
      }
    }
    // Flutter的Result对象只能返回一次数据，同一个Result对象如果再次提交数据会crash（错误信息：数据已被提交过），所以要把前一次的EventHandler注销掉
    // 否则重复调用统一个接口时，smssdk会针对所有EventHandler发送回调，旧的Result对象就会被触发，导致Flutter层crash
    SMSSDK.unregisterAllEventHandler()
    SMSSDK.registerEventHandler(callback)
    val phoneNumber = call.argument<String>("phoneNumber")
    val zone = call.argument<String>("zone")
    val code = call.argument<String>("code")
    d("zone: $zone")
    d("phoneNumber: $phoneNumber")
    d("code: $code")
    SMSSDK.submitVerificationCode(zone, phoneNumber, code)
  }

  private fun getVoiceCode(call: MethodCall, rst: Result) {
    // 注册监听器
    val callback: EventHandler = object : EventHandler() {
      override fun afterEvent(event: Int, result: Int, data: Any) {
        if (result == SMSSDK.RESULT_COMPLETE) {
          if (event == SMSSDK.EVENT_GET_VOICE_VERIFICATION_CODE) {
            // callback onSuccess
            // 此接口data=null
            val map: Map<String, Any> = HashMap()
            onSuccess(rst, map)
          }
        } else {
          if (event == SMSSDK.EVENT_GET_VOICE_VERIFICATION_CODE) {
            // callback onError
            if (data is Throwable) {
              val msg = data.message
              onSdkError(rst, msg!!)
            } else {
              val msg = "Sdk returned 'RESULT_ERROR', but the data is NOT an instance of Throwable"
              e("getVoiceCode() internal error: $msg")
              onInternalError(rst, msg)
            }
          }
        }
      }
    }
    // Flutter的Result对象只能返回一次数据，同一个Result对象如果再次提交数据会crash（错误信息：数据已被提交过），所以要把前一次的EventHandler注销掉
    // 否则重复调用统一个接口时，smssdk会针对所有EventHandler发送回调，旧的Result对象就会被触发，导致Flutter层crash
    SMSSDK.unregisterAllEventHandler()
    SMSSDK.registerEventHandler(callback)
    val phoneNumber = call.argument<String>("phoneNumber")
    val zone = call.argument<String>("zone")
    d("zone: $zone")
    d("phoneNumber: $phoneNumber")
    SMSSDK.getVoiceVerifyCode(zone, phoneNumber)
  }

  private fun getTextCode(call: MethodCall, rst: Result) {
    // 注册监听器
    val callback: EventHandler = object : EventHandler() {
      override fun afterEvent(event: Int, result: Int, data: Any) {
        if (result == SMSSDK.RESULT_COMPLETE) {
          if (event == SMSSDK.EVENT_GET_VERIFICATION_CODE) {
            val smart = data as Boolean
            // callback onSuccess
            val map: MutableMap<String, Any> = HashMap()
            map["smart"] = smart
            onSuccess(rst, map)
          }
        } else {
          if (event == SMSSDK.EVENT_GET_VERIFICATION_CODE) {
            // callback onError
            if (data is Throwable) {
              val msg = data.message
              onSdkError(rst, msg!!)
            } else {
              val msg = "Sdk returned 'RESULT_ERROR', but the data is NOT an instance of Throwable"
              e("getTextCode() internal error: $msg")
              onInternalError(rst, msg)
            }
          }
        }
      }
    }
    // Flutter的Result对象只能返回一次数据，同一个Result对象如果再次提交数据会crash（错误信息：数据已被提交过），所以要把前一次的EventHandler注销掉
    // 否则重复调用统一个接口时，smssdk会针对所有EventHandler发送回调，旧的Result对象就会被触发，导致Flutter层crash
    SMSSDK.unregisterAllEventHandler()
    SMSSDK.registerEventHandler(callback)
    val phoneNumber = call.argument<String>("phoneNumber")
    val zone = call.argument<String>("zone")
    val tempCode = call.argument<String>("tempCode")
    d("tempCode: $tempCode")
    d("zone: $zone")
    d("phoneNumber: $phoneNumber")
    SMSSDK.getVerificationCode(tempCode, zone, phoneNumber)
  }

  fun recycle() {
    SMSSDK.unregisterAllEventHandler()
  }

  //回调
  ////////////////////////////////////////////////////////////////////////////////////////////

  //成功的回调
  private fun onSuccess(result: Result, ret: Map<String, Any>) {
    val map: MutableMap<String, Any> = HashMap()
    map["ret"] = ret
    Handler(Looper.getMainLooper()).post(Runnable {
      try {
        result.success(map)
      } catch (e: IllegalStateException) {
        // ignore
        e.printStackTrace()
      }
    })
  }

  private fun onSdkError(result: Result, error: String) {
    try {
      val errorJson = JSONObject(error)
      val code: Int = errorJson.optInt("status")
      var msg: String = errorJson.optString("detail")
      if (TextUtils.isEmpty(msg)) {
        msg = errorJson.optString("error")
      }
      val errMap: MutableMap<String, Any> = HashMap()
      errMap[KEY_CODE] = code
      errMap[KEY_MSG] = msg
      val map: MutableMap<String, Any> = HashMap()
      map["err"] = errMap
      Handler(Looper.getMainLooper()).post(Runnable {
        try {
          result.success(map)
        } catch (e: IllegalStateException) {
          // ignore
          e.printStackTrace()
        }
      })
    } catch (err: JSONException) {
      SMSSDKLog.e("Smssdk Flutter plugin internal error. msg= " + err.message, err)
      onInternalError(result, "Generate JSONObject error")
    }
  }

  private fun onInternalError(result: Result, errMsg: String) {
    val errMap: MutableMap<String, Any> = HashMap()
    errMap[KEY_CODE] = BRIDGE_ERR
    errMap[KEY_MSG] = ERROR_INTERNAL + errMsg
    val map: MutableMap<String, Any> = HashMap()
    map["err"] = errMap
    Handler(Looper.getMainLooper()).post(Runnable {
      try {
        result.success(map)
      } catch (e: IllegalStateException) {
        // ignore
        e.printStackTrace()
      }
    })
  }

}
