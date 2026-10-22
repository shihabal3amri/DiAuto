package com.andrerinas.openheadunit.hud

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.Parcelable
import com.byd.spi.ipc.cursor.BinderCursor
import dalvik.system.PathClassLoader
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * Binder transport to the installed car server's property service. The service checks
 * the original caller's permissions before writing. Use synchronous setProperties:
 * setMultiProperties only acknowledges queueing and hides later permission failures.
 */
internal object BydCarServerTransport {
    private var service: Any? = null
    private var valueCtor: Constructor<*>? = null
    private var valueClass: Class<*>? = null
    private var setMethod: Method? = null
    private var readMethod: Method? = null

    @Synchronized
    private fun ensureService(context: Context): Any {
        service?.let { return it }
        val loader = PathClassLoader(CAR_SERVER_APK, context.classLoader)
        val binder = queryBinder(context)
        check(binder.interfaceDescriptor == INTERFACE) { "Unexpected property-service interface" }
        val stub = Class.forName("$INTERFACE\$Stub", true, loader)
        val proxy = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        runCatching { proxy.javaClass.getMethod("setPackageInfo", String::class.java, Int::class.javaPrimitiveType)
            .invoke(proxy, context.packageName, android.os.Process.myPid()) }
        valueClass = Class.forName("com.byd.car.property.CarPropertyValue", true, loader)
        valueCtor = valueClass!!.getConstructor(String::class.java, Any::class.java)
        val arrayClass = ReflectArray.newInstance(valueClass, 0).javaClass
        setMethod = proxy.javaClass.getMethod("setProperties", arrayClass)
        readMethod = proxy.javaClass.getMethod("getProperty", String::class.java)
        service = proxy
        return proxy
    }

    private fun queryBinder(context: Context): IBinder {
        context.contentResolver.query(
            Uri.parse(PROVIDER_URI), null, null, arrayOf(INTERFACE), null,
        )?.use { cursor ->
            val extras: Bundle = cursor.extras ?: error("Property provider returned no extras")
            extras.classLoader = BinderCursor.BinderParcelable::class.java.classLoader
            @Suppress("DEPRECATION")
            val wrapper = extras.getParcelable<Parcelable>("binder") as? BinderCursor.BinderParcelable
            return wrapper?.binder ?: error("Property provider returned no binder")
        }
        error("Property provider query failed")
    }

    /** Returns the completed property-handler status; 0 still does not prove rendering. */
    fun write(context: Context, key: String, value: Int): Int {
        val proxy = ensureService(context)
        val values = ReflectArray.newInstance(valueClass, 1)
        ReflectArray.set(values, 0, valueCtor!!.newInstance(key, value as Any))
        val result = setMethod!!.invoke(proxy, values)
        val code = result.javaClass.getField("code").getInt(result)
        if (code != 0) {
            val description = result.javaClass.getField("description").get(result)
            HudLog.w("BYD-CarServer-Navi", "Property write rejected key=$key code=$code: $description")
        }
        return code
    }

    /** Reads a property through the same service; returns "code=value" description. */
    fun read(context: Context, key: String): String {
        val proxy = ensureService(context)
        val response = readMethod!!.invoke(proxy, key)
        val status = response.javaClass.getField("status").get(response)
        val code = status.javaClass.getField("code").getInt(status)
        val result = response.javaClass.getField("result").get(response)
        val value = result?.javaClass?.getField("mValue")?.get(result)
        return "code=$code value=$value"
    }

    private const val CAR_SERVER_APK = "/system/priv-app/DiCarServer/DiCarServer.apk"
    private const val PROVIDER_URI = "content://com.byd.car.server.provider.CarServiceProvider/sync_binder"
    private const val INTERFACE = "com.byd.car.property.ICarPropertyService"
}
