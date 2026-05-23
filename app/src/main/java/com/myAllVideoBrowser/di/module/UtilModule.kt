package com.myAllVideoBrowser.di.module

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import com.myAllVideoBrowser.DLApplication
import com.myAllVideoBrowser.data.local.room.dao.RemoteVideoDao
import com.myAllVideoBrowser.data.nas.NasCredentialsStore
import com.myAllVideoBrowser.util.*
import com.myAllVideoBrowser.util.nas.NasUploadManager
import com.myAllVideoBrowser.util.nas.SmbUploader
import com.myAllVideoBrowser.util.nas.WebDavUploader
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class UtilModule {

    @Singleton
    @Provides
    fun bindDownloadManager(application: Application): DownloadManager =
        application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    @Singleton
    @Provides
    fun bindFileUtil() = FileUtil()

    @Singleton
    @Provides
    fun bindSystemUtil() = SystemUtil()

    @Singleton
    @Provides
    fun bindIntentUtil(fileUtil: FileUtil) = IntentUtil(fileUtil)

    @Singleton
    @Provides
    fun bindAppUtil() = AppUtil()

    @Singleton
    @Provides
    fun provideNotificationsHelper(dlApplication: DLApplication): NotificationsHelper {
        return NotificationsHelper(dlApplication.applicationContext)
    }

    @Singleton
    @Provides
    fun provideSharedPrefHelper(dlApplication: DLApplication, appUtil: AppUtil): SharedPrefHelper {
        return SharedPrefHelper(dlApplication.applicationContext, appUtil)
    }

    @Singleton
    @Provides
    fun provideNasCredentialsStore(dlApplication: DLApplication): NasCredentialsStore {
        return NasCredentialsStore(dlApplication.applicationContext)
    }

    @Singleton
    @Provides
    fun provideWebDavUploader(): WebDavUploader = WebDavUploader()

    @Singleton
    @Provides
    fun provideSmbUploader(): SmbUploader = SmbUploader()

    @Singleton
    @Provides
    fun provideNasUploadManager(
        credentialsStore: NasCredentialsStore,
        webDavUploader: WebDavUploader,
        smbUploader: SmbUploader,
        remoteVideoDao: RemoteVideoDao,
    ): NasUploadManager {
        return NasUploadManager(credentialsStore, webDavUploader, smbUploader, remoteVideoDao)
    }
}