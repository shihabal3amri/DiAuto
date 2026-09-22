package com.andrerinas.openheadunit.connection.carkey.fyt

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException

interface RemoteToolkit : IInterface {

    companion object {
        const val DESCRIPTOR = "com.syu.ipc.IRemoteToolkit"
        const val TRANSACTION_GET = 1
        const val TRANSACTION_IS_MAP_APPLICATION = 2
        const val TRANSACTION_PROC_NAME = 3
        const val TRANSACTION_SEND_TO_SYU_SERVICE_AUDIO_INFO = 4
        const val TRANSACTION_NOTIFY = 5
    }

    fun getRemoteModule(moduleCode: Int): RemoteModule?

    fun isMapApplication(moduleCode: Int): Int

    fun notify(moduleCode: Int, notification: String?)

    fun procName(moduleCode: Int): String

    fun sendToSyuServiceAudioInfo(
        moduleCode: Int,
        ints: IntArray?,
        floats: FloatArray?,
        strings: Array<String>?,
    )


    abstract class Stub : Binder(), RemoteToolkit {

        init {
            attachInterface(this, DESCRIPTOR)
        }

        companion object {
            @JvmStatic
            fun asInterface(obj: IBinder): RemoteToolkit {
                val iin = obj.queryLocalInterface(DESCRIPTOR)

                if (iin is RemoteToolkit)
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
                TRANSACTION_GET -> {
                    data.enforceInterface(DESCRIPTOR)
                    val moduleCode = data.readInt()
                    val result: RemoteModule? = getRemoteModule(moduleCode)
                    reply!!.writeNoException()

                    if (result != null)
                        reply.writeStrongBinder(result.asBinder())
                    else
                        reply.writeStrongBinder(null)
                    return true
                }

                TRANSACTION_IS_MAP_APPLICATION -> {
                    data.enforceInterface(DESCRIPTOR)
                    val iIsmapapplication: Int = isMapApplication(data.readInt())
                    reply!!.writeNoException()
                    reply.writeInt(iIsmapapplication)
                    return true
                }

                TRANSACTION_PROC_NAME -> {
                    data.enforceInterface(DESCRIPTOR)
                    val strProcName: String? = procName(data.readInt())
                    reply!!.writeNoException()
                    data.writeString(strProcName)
                    return true

                }

                TRANSACTION_SEND_TO_SYU_SERVICE_AUDIO_INFO -> {
                    data.enforceInterface(DESCRIPTOR)
                    sendToSyuServiceAudioInfo(data.readInt(), data.createIntArray(), data.createFloatArray(), data.createStringArray())
                    reply!!.writeNoException()
                    return true;

                }

                TRANSACTION_NOTIFY -> {
                    data.enforceInterface(DESCRIPTOR)
                    notify(data.readInt(), data.readString())
                    reply!!.writeNoException()
                    return true;
                }

                1598968902 -> {
                    reply!!.writeString(DESCRIPTOR)
                    return true
                }

                else -> return super.onTransact(code, data, reply, flags)
            }
        }
    }

    class Proxy(val mRemote: IBinder) : RemoteToolkit {

        override fun asBinder(): IBinder {
            return this.mRemote
        }

        override fun getRemoteModule(moduleCode: Int): RemoteModule? {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()

            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(moduleCode)
                mRemote.transact(TRANSACTION_GET, data, reply, 0)
                reply.readException()

                val binder: IBinder? = reply.readStrongBinder()

                return if (binder == null)
                    null
                else
                    RemoteModule.Stub.asInterface(binder)
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        // com.syu.ipc.IRemoteToolkit
        override fun isMapApplication(moduleCode: Int): Int {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()

            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(moduleCode)
                this.mRemote.transact(
                    TRANSACTION_IS_MAP_APPLICATION,
                    data,
                    reply,
                    0,
                )
                reply.readException()
                return reply.readInt()
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        override fun notify(moduleCode: Int, notification: String?) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()

            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(moduleCode)
                data.writeString(notification)
                this.mRemote.transact(TRANSACTION_NOTIFY, data, reply, 0)
                reply.readException()
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        override fun procName(moduleCode: Int): String {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()

            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(moduleCode)
                this.mRemote.transact(TRANSACTION_PROC_NAME, data, reply, 0)
                reply.readException()
                return reply.readString()!!
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        override fun sendToSyuServiceAudioInfo(
            moduleCode: Int,
            ints: IntArray?,
            floats: FloatArray?,
            strings: Array<String>?,
        ) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()

            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(moduleCode)
                data.writeIntArray(ints)
                data.writeFloatArray(floats)
                data.writeStringArray(strings)
                this.mRemote.transact(
                    TRANSACTION_SEND_TO_SYU_SERVICE_AUDIO_INFO,
                    data,
                    reply,
                    0,
                )
                reply.readException()
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }
}
