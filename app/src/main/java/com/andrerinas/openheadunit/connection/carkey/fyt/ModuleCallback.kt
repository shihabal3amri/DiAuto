package com.andrerinas.openheadunit.connection.carkey.fyt

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException

// com.syu.ipc.IRemoteToolkit
interface ModuleCallback : IInterface {

    companion object {
        const val DESCRIPTOR = "com.syu.ipc.IModuleCallback"
        const val TRANSACTION_UPDATE = 1;
    }

    fun update(updateCode: Int, ints: IntArray?, floats: FloatArray?, strings: Array<String>?)


    abstract class Stub : Binder(), ModuleCallback {

        init {
            attachInterface(this, DESCRIPTOR)
        }

        companion object {
            @JvmStatic
            fun asInterface(obj: IBinder): ModuleCallback {
                val iin = obj.queryLocalInterface(DESCRIPTOR)

                if (iin is ModuleCallback)
                    return iin
                else
                    return Proxy(obj)
            }
        }

        override fun asBinder(): IBinder {
            return this
        }

        @Throws(RemoteException::class)  // android.os.Binder
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                TRANSACTION_UPDATE -> {
                    data.enforceInterface(DESCRIPTOR)
                    val updateCode = data.readInt()
                    val ints: IntArray? = data.createIntArray()
                    val floats: FloatArray? = data.createFloatArray()
                    val strings: Array<String>? = data.createStringArray()
                    update(updateCode, ints, floats, strings)
                    return true
                }

                1598968902 -> {
                    reply!!.writeString(DESCRIPTOR)
                    return true
                }

                else -> return super.onTransact(code, data, reply, flags)
            }
        }
    }

    class Proxy(val mRemote: IBinder) : ModuleCallback {

        override fun asBinder(): IBinder {
            return this.mRemote;
        }

        override fun update(
            updateCode: Int,
            ints: IntArray?,
            floats: FloatArray?,
            strings: Array<String>?,
        ) {
            val data = Parcel.obtain()

            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(updateCode)
                data.writeIntArray(ints)
                data.writeFloatArray(floats)
                data.writeStringArray(strings)
                this.mRemote.transact(TRANSACTION_UPDATE, data, null, 0)
            } finally {
                data.recycle()
            }

        }
    }
}
