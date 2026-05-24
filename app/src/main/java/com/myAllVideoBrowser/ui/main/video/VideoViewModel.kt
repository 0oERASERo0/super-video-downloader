package com.myAllVideoBrowser.ui.main.video

import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.lifecycle.viewModelScope
//import com.allVideoDownloaderXmaster.OpenForTesting
import com.myAllVideoBrowser.data.local.model.LocalVideo
import com.myAllVideoBrowser.data.local.room.dao.RemoteVideoDao
import com.myAllVideoBrowser.data.local.room.entity.RemoteVideoInfo
import com.myAllVideoBrowser.ui.main.base.BaseViewModel
import com.myAllVideoBrowser.util.AppLogger
import com.myAllVideoBrowser.util.ContextUtils
import com.myAllVideoBrowser.util.FileUtil
import com.myAllVideoBrowser.util.SingleLiveEvent
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

//@OpenForTesting
class VideoViewModel @Inject constructor(
    private val fileUtil: FileUtil,
    private val remoteVideoDao: RemoteVideoDao,
) : BaseViewModel() {

    companion object {
        const val FILE_EXIST_ERROR_CODE = 1
        const val FILE_INVALID_ERROR_CODE = 2
    }

    var localVideos: ObservableField<MutableList<LocalVideo>> = ObservableField(mutableListOf())
    var isLoading: ObservableBoolean = ObservableBoolean(false)

    val renameErrorEvent = SingleLiveEvent<Int>()
    val shareEvent = SingleLiveEvent<Uri>()

    override fun start() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(1000)
                val newList = getFilesList().toMutableList()
                newList.sortWith(
                    compareBy({ !it.isRemote }, { it.name.lowercase() })
                )
                localVideos.set(newList)
            }
        }
    }


    override fun stop() {
    }

    private fun getFilesList(): List<LocalVideo> {
        val listVideos: MutableList<LocalVideo> = mutableListOf()
        fileUtil.listFiles.forEach { entry ->
            val fileUri = entry.value.second
            val fileSize = fileUtil.getContentLength(ContextUtils.getApplicationContext(), fileUri)
            val readableSize = FileUtil.getFileSizeReadable(fileSize.toDouble())
            val video = LocalVideo(
                entry.value.first,
                fileUri,
                entry.key
            )
            video.size = readableSize
            listVideos.add(video)
        }

        try {
            val localNames = listVideos.map { it.name }.toHashSet()
            remoteVideoDao.listAll().forEach { remote ->
                if (localNames.contains(remote.name)) {
                    listVideos.find { it.name == remote.name }?.let { match ->
                        match.isRemote = true
                        match.remoteLabel = remote.destinationLabel
                        match.remoteRecordId = remote.id
                    }
                } else {
                    listVideos.add(remote.toLocalVideo())
                }
            }
        } catch (e: Throwable) {
            AppLogger.e("Loading remote videos failed: ${e.message}")
        }

        return listVideos.toList()
    }

    private fun RemoteVideoInfo.toLocalVideo(): LocalVideo {
        val uri = try {
            Uri.parse(remoteUri)
        } catch (e: Throwable) {
            Uri.EMPTY
        }
        return LocalVideo(
            id = id.hashCode().toLong(),
            uri = uri,
            name = name,
            isRemote = true,
            remoteLabel = destinationLabel,
            remoteRecordId = id,
        ).also {
            it.size = FileUtil.getFileSizeReadable(sizeBytes.toDouble())
        }
    }

    fun deleteVideo(context: Context, video: LocalVideo) {
        if (video.isRemote && video.remoteRecordId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    remoteVideoDao.deleteById(video.remoteRecordId!!)
                } catch (e: Throwable) {
                    AppLogger.e("Remote video delete failed: ${e.message}")
                }
            }
            val list = localVideos.get()?.toMutableList()
            list?.remove(video)
            localVideos.set(list ?: mutableListOf())
            return
        }
        localVideos.get()?.find { it.uri.path == video.uri.path }?.let {
            fileUtil.deleteMedia(context, video.uri)

            val list = localVideos.get()?.toMutableList()
            list?.remove(it)
            localVideos.set(list ?: mutableListOf())
        }
    }

    fun renameVideo(context: Context, uri: Uri, newName: String) {
        if (newName.isNotEmpty()) {
            val exists = fileUtil.isUriExists(context, uri)
            if (exists) {
                val isFileWithNameNotExists =
                    fileUtil.isFileWithNameNotExists(context, uri, newName)
                if (isFileWithNameNotExists) {
                    val newMediaNameUri = fileUtil.renameMedia(context, uri, newName)
                    if (newMediaNameUri != null) {
                        localVideos.get()?.find { it.uri.toString() == uri.toString() }?.let {
                            it.uri = newMediaNameUri.second
                            it.name = newMediaNameUri.first

                            localVideos.get().let { list ->
                                list?.set(list.indexOf(it), it)
                            }
                        }
                        return
                    }
                }

                renameErrorEvent.value = FILE_EXIST_ERROR_CODE
                return
            }
        }

        renameErrorEvent.value = FILE_INVALID_ERROR_CODE
    }

    fun findVideoByName(downloadFilename: String?): Observable<LocalVideo> {
        return Observable.create { emitter ->
            val videos = getFilesList()
            val found =
                videos.find { it.name.contains(File(downloadFilename.toString()).name) }
            if (found != null) {
                emitter.onNext(found)
                emitter.onComplete()
            }
        }
    }
}