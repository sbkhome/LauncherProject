package com.android.medianet.launcher.data.db

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.android.medianet.launcher.data.db.LauncherSettings.Favorites
import com.android.medianet.launcher.data.model.FolderInfo
import com.android.medianet.launcher.data.model.ItemInfo
import com.android.medianet.launcher.middlelayer.AllAppsList
import com.android.medianet.launcher.middlelayer.IconCache
import com.android.medianet.launcher.middlelayer.ShortcutInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.URISyntaxException

class LauncherDataSource(val context: Context, val iconCache: IconCache, val allAppsList: AllAppsList) {


    fun loadWorkspace() : Flow<WorkspaceItems>{
        return flow{
            emit(loadWorkspaceItemsFromDatabase())
        }.flowOn(Dispatchers.IO)
    }


    private fun loadWorkspaceItemsFromDatabase() : WorkspaceItems{
        val listofItems = ArrayList<ItemInfo>()
        val folderMap = mutableMapOf<Long, FolderInfo>()
        val listofShortcuts = ArrayList<ShortcutInfo>()
        val itemsToRemove = ArrayList<Long>()

        val occupied = Array(2){
            Array(4){
                arrayOfNulls<ItemInfo>(4)
            }
        }

        val uri : Uri = Uri.parse("")

        context.contentResolver.query(uri, null, null,null,null)?.use {

            val itemTypeIndex = it.getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE)

            try {

                while(it.moveToNext()){
                    val itemType = it.getInt(itemTypeIndex)

                    when (itemType) {
                        Favorites.ITEM_TYPE_APPLICATION, Favorites.ITEM_TYPE_SHORTCUT -> retrieveShortcutData(
                            it,
                            listofShortcuts,
                            listofItems,
                            folderMap,
                            itemType,
                            occupied
                        )
                    }
                }
            }catch (e : Exception){
                Log.e(TAG, "loadWorkspaceItemsFromDatabase(): error occurred=${e.message}")
            }
        }

        val workspaceItems = WorkspaceItems(listofItems, folderMap, listofShortcuts)
        return workspaceItems
    }

    fun getShortcutInfo(intent: Intent, context: Context, c : Cursor) : ShortcutInfo{
        val info = ShortcutInfo()

        var icon : Bitmap? = null
        val titleIndex = c.getColumnIndexOrThrow(Favorites.TITLE)
        val pkgMgr = context.packageManager
        val resolveInfo = pkgMgr.resolveActivity(intent, 0)
        resolveInfo?.let {
            icon = iconCache.getIcon(intent.component , it)
        }

        if(icon == null) {
            icon = getIconFromCursor(c)
        }

        icon?.let {
            info.setIcon(it)
        }

        if(info.title == null){
            info.title = intent.component?.className

        }

        intent.component?.let {
            info.packagename = it.className
        }
        return info
    }

    fun getIconFromCursor(cursor: Cursor): Bitmap? {
        val iconIndex = cursor.getColumnIndexOrThrow(Favorites.ICON)
        val data = cursor.getBlob(iconIndex)
        return try {
            BitmapFactory.decodeByteArray(data, 0, data.size)
        } catch (e: Exception) {
            Log.e("", "")
            null
        }
    }



    private fun retrieveShortcutData(
        cursor: Cursor,
        listofShortcuts: ArrayList<ShortcutInfo>,
        listofItems : ArrayList<ItemInfo>,
        folderMap : MutableMap<Long,FolderInfo>,
        ItemType : Int,
        occupied : Array<Array<Array<ItemInfo?>>>){

        val intentIndex = cursor.getColumnIndexOrThrow(Favorites.INTENT)
        val idIndex = cursor.getColumnIndexOrThrow(Favorites._ID)
        val containerIndex = cursor.getColumnIndexOrThrow(Favorites.CONTAINER)
        val screenIndex = cursor.getColumnIndexOrThrow(Favorites.SCREEN)
        val cellXIndex = cursor.getColumnIndexOrThrow(Favorites.CELLX)
        val cellyIndex = cursor.getColumnIndexOrThrow(Favorites.CELLY)
        //val packageNameIndex = cursor.getColumnIndexOrThrow()
        val intentDescription = cursor.getString(intentIndex)

        var intent: Intent

        try {
            intent = Intent.parseUri(intentDescription, 0)
        } catch (e: URISyntaxException) {
            Log.e("", "$(Launcher Constants.PROFILE RETRIEVAL FLOW TAG) STAG Cannot parse the intent se")
            return
        }
        val shortcutInfo  = getShortcutInfo(intent , context, cursor)

        shortcutInfo.let {

            // updateSavedIcon(it, oursor)

            it.intent = intent
            it.id = cursor.getLong(idIndex)
            it.container = cursor.getInt(containerIndex)
            it.screenId = cursor.getInt(screenIndex)
            it.cellX = cursor.getInt(cellXIndex)
            it.cellY = cursor.getInt(cellyIndex)

            listofShortcuts.add(it)

            when(it.container){
                Favorites.CONTAINER_DESKTOP, Favorites.CONTAINER_HOTSEAT -> listofItems.add(it)
                else -> {
                    //folder section
                }
            }
        }
    }
}