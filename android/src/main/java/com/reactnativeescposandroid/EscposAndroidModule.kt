package com.reactnativeescposandroid

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.usb.UsbConnection
import com.dantsu.escposprinter.connection.usb.UsbPrintersConnections
import com.facebook.react.bridge.*
import java.util.regex.Pattern
import java.util.regex.Matcher
import com.bumptech.glide.Glide;



class EscposAndroidModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
  private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"

  private var mPermissionPromise: Promise? = null
  private val mPermissionReceiver: BroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      val action = intent.action
      if (ACTION_USB_PERMISSION == action) {
        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
          mPermissionPromise?.resolve(true)
        } else {
          mPermissionPromise?.resolve(false)
        }
        mPermissionPromise = null
      }
    }
  }

  private var mWriteRaw: ByteArray? = null
  private var mWriteText: String? = null
  private var mWritePromise: Promise? = null
  private var mWriteAndCut: Boolean = false
  private val mWriteReceiver: BroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      val action = intent.action
      if (ACTION_USB_PERMISSION == action) {
        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
          synchronized(this) {
            val usbManager = reactContext.getSystemService(Context.USB_SERVICE) as UsbManager?
            val usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as UsbDevice?
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
              if (usbManager != null && usbDevice != null && mWritePromise != null) {
                var usbConnection = UsbConnection(usbManager, usbDevice)
                var printer = EscPosPrinter(usbConnection, 203, 48f, 32);
                if (mWriteRaw != null) {
                  usbConnection.write(mWriteRaw);
                  usbConnection.send(100);
                }
                if (mWriteAndCut) {
                  var text = if (mWriteText != null) mWriteText else "[C]"
                  printer.printFormattedTextAndCut(text);
                } else if (mWriteText !== null) {
                  printer.printFormattedText(mWriteText);
                }
                mWritePromise?.resolve(null);
              }
            }
          }
        } else {
          mWritePromise?.reject("Not permitted", "Not permitted")
        }
        mWritePromise = null
      }
    }
  }

  override fun getName(): String {
    return "EscposAndroid"
  }

  @ReactMethod
  fun requestPermission(promise: Promise) {
    var context = reactApplicationContext
    var usbConnection = UsbPrintersConnections.selectFirstConnected(context);
    var usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager?;
    if (usbConnection != null && usbManager != null) {
      var permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), 0);
      var filter = IntentFilter(ACTION_USB_PERMISSION);
      context.registerReceiver(mPermissionReceiver, filter);
      mPermissionPromise = promise;
      usbManager.requestPermission(usbConnection.getDevice(), permissionIntent);
    } else {
      promise.resolve(false)
    }
  }

  private fun readableArrayToByteArray (readableArray: ReadableArray?): ByteArray? {
    if (readableArray == null) {
      return null
    }
    var byteArray = ByteArray(readableArray.size())
    for (i in 0 until readableArray.size()) {
      byteArray[i] = readableArray.getInt(i).toByte()
    }
    return byteArray
  }

  private fun getBitmapFromUrl(url: String): Bitmap? {
    return try {
        val bitmap = Glide.with(currentActivity)
            .asBitmap()
            .load(url)
            .submit()
            .get()
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun preprocessImgTag(printer: EscPosPrinter, text: String): String {
    val p = Pattern.compile("(?<=<img>)(.*)(?=</img>)")
    val m: Matcher = p.matcher(text)
    val sb = StringBuffer()

    while (m.find()) {
        val firstGroup = m.group(1)
        m.appendReplacement(
            sb,
            PrinterTextParserImg.bitmapToHexadecimalString(printer, getBitmapFromUrl(firstGroup))
        )
    }
    m.appendTail(sb)

    return sb.toString()
}

  @ReactMethod
  fun write(params: ReadableMap, promise: Promise) {
    var keyCut = "cut"
    var cut = if (params.hasKey(keyCut)) params.getBoolean(keyCut) else false
    var keyText = "text"
    var text = if (params.hasKey(keyText)) params.getString(keyText) else null
    text = preprocessImgTag(printer, text);
    var keyRaw = "raw"
    var raw = if (params.hasKey(keyRaw)) readableArrayToByteArray(params.getArray(keyRaw)) else null
    var context = reactApplicationContext
    var usbConnection = UsbPrintersConnections.selectFirstConnected(context);
    var usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager?;
    if (usbConnection != null && usbManager != null) {
      var permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), 0);
      var filter = IntentFilter(ACTION_USB_PERMISSION);
      context.registerReceiver(mWriteReceiver, filter);
      mWritePromise = promise;
      mWriteText = text;
      mWriteAndCut = cut;
      mWriteRaw = raw;
      usbManager.requestPermission(usbConnection.getDevice(), permissionIntent);
    } else {
      promise.reject("EUNSPECIFIED", "usb does not avairable");
    }
  }
}
