package com.andrerinas.openheadunit.connection.carkey.fyt

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException

interface RemoteModule : IInterface {

    companion object {
        const val DESCRIPTOR = "com.syu.ipc.IRemoteModule"
        const val TRANSACTION_CMD = 1
        const val TRANSACTION_GET = 2
        const val TRANSACTION_REGISTER = 3
        const val TRANSACTION_UNREGISTER = 4
    }

    fun cmd(cmdCode: Int, ints: IntArray? = null, floats: FloatArray? = null, strings: Array<String>? = null)

    fun get(getCode: Int, ints: IntArray? = null, floats: FloatArray? = null, strings: Array<String>? = null): ModuleObject?

    fun register(callback: ModuleCallback, updateCode: Int, update: Int)

    fun unregister(callback: ModuleCallback, updateCode: Int)


    abstract class Stub : Binder(), RemoteModule {

        init {
            attachInterface(this, DESCRIPTOR)
        }

        companion object {
            @JvmStatic
            fun asInterface(obj: IBinder): RemoteModule {
                val iin = obj.queryLocalInterface(DESCRIPTOR)

                if (iin is RemoteModule)
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
                TRANSACTION_CMD -> {
                    data.enforceInterface(DESCRIPTOR)
                    val cmdCode = data.readInt()
                    val ints: IntArray? = data.createIntArray()
                    val floats: FloatArray? = data.createFloatArray()
                    val strings: Array<String>? = data.createStringArray()
                    cmd(cmdCode, ints, floats, strings)
                    return true
                }

                TRANSACTION_GET -> {
                    data.enforceInterface(DESCRIPTOR)
                    val getCode = data.readInt()
                    val ints = data.createIntArray()
                    val floats = data.createFloatArray()
                    val strings = data.createStringArray()
                    val result: ModuleObject? = get(getCode, ints, floats, strings)
                    reply!!.writeNoException()

                    if (result != null) {
                        reply.writeInt(1)
                        reply.writeIntArray(result.ints)
                        reply.writeFloatArray(result.floats)
                        reply.writeStringArray(result.strings)
                        return true
                    }
                    reply.writeInt(0)
                    return true
                }

                TRANSACTION_REGISTER -> {
                    data.enforceInterface(DESCRIPTOR)
                    val callback: ModuleCallback =
                        ModuleCallback.Stub.asInterface(data.readStrongBinder())
                    val updateCode = data.readInt()
                    val update = data.readInt()
                    register(callback, updateCode, update)
                    return true
                }

                TRANSACTION_UNREGISTER -> {
                    data.enforceInterface(DESCRIPTOR)
                    val callback2: ModuleCallback =
                        ModuleCallback.Stub.asInterface(data.readStrongBinder())
                    val updateCode2 = data.readInt()
                    unregister(callback2, updateCode2)
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

    class Proxy(val mRemote: IBinder) : RemoteModule {

        override fun asBinder(): IBinder {
            return this.mRemote;
        }

        @Throws(RemoteException::class)  // android.os.Binder
        override fun cmd(cmdCode: Int, ints: IntArray?, floats: FloatArray?, strings: Array<String>?) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()

            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(cmdCode)
                data.writeIntArray(ints)
                data.writeFloatArray(floats)
                data.writeStringArray(strings)
                this.mRemote.transact(TRANSACTION_CMD, data, reply, 0)
                reply.readException()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        @Throws(RemoteException::class)  // android.os.Binder
        override fun get(
            getCode: Int,
            ints: IntArray?,
            floats: FloatArray?,
            strings: Array<String>?,
        ): ModuleObject? {
            val result: ModuleObject?
            val data = Parcel.obtain()
            val reply = Parcel.obtain()

            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(getCode)
                data.writeIntArray(ints)
                data.writeFloatArray(floats)
                data.writeStringArray(strings)
                this.mRemote.transact(TRANSACTION_GET, data, reply, 0)
                reply.readException()

                if (reply.readInt() != 0) {
                    result = ModuleObject(
                        reply.createIntArray()!!,
                        reply.createFloatArray()!!,
                        reply.createStringArray()!!,
                    )
                } else {
                    result = null
                }
                return result
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        @Throws(RemoteException::class)  // android.os.Binder
        override fun register(callback: ModuleCallback, updateCode: Int, update: Int) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()

            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeStrongBinder(callback.asBinder())
                data.writeInt(updateCode)
                data.writeInt(update)
                this.mRemote.transact(TRANSACTION_REGISTER, data, reply, 0)
                reply.readException()
            } finally {
                reply.recycle()
                data.recycle()
            }

        }

        @Throws(RemoteException::class)  // android.os.Binder
        override fun unregister(callback: ModuleCallback, updateCode: Int) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()

            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeStrongBinder(callback.asBinder())
                data.writeInt(updateCode)
                this.mRemote.transact(TRANSACTION_UNREGISTER, data, reply, 0)
                reply.readException()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }
    }
}
