package com.android.medianet.launcher.middlelayer

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import com.android.medianet.launcher.data.db.LauncherSettings
import com.android.medianet.launcher.data.db.LauncherSettings.Favorites
import com.android.medianet.launcher.data.model.ApplicationInfo
import com.android.medianet.launcher.data.model.ItemInfo

class ShortcutInfo : ItemInfo {

    var title : CharSequence? = null
    var intent : Intent? = null

    private var mIcon : Bitmap? = null
    lateinit var packagename : String
    var iconResource : Intent.ShortcutIconResource? = null

    constructor() {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT
    }

    constructor(appType : Int)  :this(){
    }
    public constructor(info:ShortcutInfo) {
        title =  info.title?.toString()
        intent = Intent(info.intent)
        if(info.iconResource!=null){
            iconResource = Intent.ShortcutIconResource().apply {
                packageName = info.iconResource?.packageName
                resourceName = info.iconResource?.resourceName
            }
        }
        mIcon = info.mIcon;

    }

    public constructor(info:ApplicationInfo) {
        title =  info.title?.toString()
        intent = Intent(info.intent)
    }

    fun setIcon(b : Bitmap){
        mIcon = b
    }

    fun getIcon(iconCache: IconCache) : Bitmap?{

        if(mIcon == null){
            if(this.intent!=null){
                mIcon = iconCache.getIcon(this.intent)
            }else{

            }
        }
        return mIcon
    }

    override fun onAddToDatabase(cv : ContentValues) {
        super.onAddToDatabase(cv)

        title?.let {
            cv.put(Favorites.TITLE, it.toString())
        }
        intent?.let {
            cv.put(Favorites.INTENT, it.toUri(0))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShortcutInfo) return false

        return title == other.title &&
                intent?.toUri(0) == other.intent?.toUri(0)
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (intent?.toUri(0)?.hashCode() ?: 0)
        return result
    }


}

