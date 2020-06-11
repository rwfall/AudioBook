package com.allsoftdroid.common.base.network

import android.app.Application
import android.content.Context
import androidx.core.content.ContextCompat
import com.allsoftdroid.common.R
import timber.log.Timber
import java.util.*

class ArchiveUtils {
    companion object{

        private const val BASE_DOWNLOAD_URL = "https://archive.org/download"
        private const val BASE_IMAGE_URL = "https://archive.org/services/img"
        const val AppFolderName = "AudioBooksApp"

        /**
         * This function generated the file path on the remote server
         * @param filename unique filename on the server
         * @return complete file path on the remote server
         */
        fun getRemoteFilePath(filename: String,identifier:String):String{
            return "$BASE_DOWNLOAD_URL/$identifier/$filename"
        }

        fun getThumbnail(imageId: String?) = "$BASE_IMAGE_URL/$imageId/"

        fun getLocalSavePath(bookId:String) = "/$AppFolderName/$bookId/"

        fun setDownloadsRootFolder(context: Application, root:String){
            val sharedPref = context.getSharedPreferences(context.getString(R.string.downloads_path),Context.MODE_PRIVATE) ?: return
            with (sharedPref.edit()) {
                putString(context.getString(R.string.downloads_root_directory_key), root)
                commit()
            }
        }

        fun getDownloadsRootFolder(context: Application): String {
            val default = ""
            val sharedPref = context.getSharedPreferences(context.getString(R.string.downloads_path),Context.MODE_PRIVATE)

            val rootFolder =  sharedPref.getString(context.getString(R.string.downloads_root_directory_key),default)

            if(rootFolder.isNullOrEmpty()){

                val dir2 = ContextCompat.getExternalFilesDirs(context, null)

                val dir: MutableList<String> = ArrayList()

                val packageName: String = context.packageName

                for (file in dir2) {
                    Timber.d("external file dir is : %s", file.absolutePath)
                    dir.add(
                        file.absolutePath
                            .replace("/Android/data/$packageName/files", "")
                    )
                }

                setDownloadsRootFolder(context,dir[0])

                return dir[0]
            }

            return rootFolder
        }
    }
}