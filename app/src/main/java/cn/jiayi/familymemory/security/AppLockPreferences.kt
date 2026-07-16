package cn.jiayi.familymemory.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockPreferences @Inject constructor(@param:ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("app_lock", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = preferences.getBoolean("enabled", false)
        set(value) { preferences.edit().putBoolean("enabled", value).apply() }
}
