package com.aibuilt.wifihack.data

import android.content.Context
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object AppContainer {
    lateinit var appContext: Context
        private set
    lateinit var okHttp: OkHttpClient
        private set
    lateinit var scanner: WifiScanner
        private set
    lateinit var vault: VaultRepository
        private set
    lateinit var connector: WifiConnector
        private set
    lateinit var admin: AdminCracker
        private set

    @Volatile private var inited = false

    fun init(context: Context) {
        if (inited) return
        synchronized(this) {
            if (inited) return
            appContext = context.applicationContext
            okHttp = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
            vault = VaultRepository(appContext)
            scanner = WifiScanner(appContext)
            connector = WifiConnector(appContext, okHttp, vault)
            admin = AdminCracker(okHttp)
            inited = true
        }
    }
}
