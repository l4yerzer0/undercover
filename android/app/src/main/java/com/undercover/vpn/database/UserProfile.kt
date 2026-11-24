package com.undercover.vpn.database

import android.os.Parcelable
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.parcelize.Parcelize
import org.json.JSONArray

@Entity(
    tableName = "user_profiles",
)
@Parcelize
data class UserProfile(
    @PrimaryKey(autoGenerate = true) var id: Long = 0L,
    var name: String = "Основной",
    var userOrder: Long = 0L,
    var primaryServerId: Long = -1L,
    var primaryNodeTag: String? = null,
    var dualServerEnabled: Boolean = false,
    var secondaryServerId: Long = -1L,
    var secondaryNodeTag: String? = null,
    // Per-profile routing settings
    var routingDirectRuSites: Boolean = true,
    var routingDirectRuApps: Boolean = true,
    var routingByeDpiYouTube: Boolean = false,
    var routingByeDpiDiscord: Boolean = false,
    var directAppsJson: String = "[]",
    var directDomainsJson: String = "[]",
    var byeDpiAppsJson: String = "[]",
    var byeDpiDomainsJson: String = "[]",
    var secondaryAppsJson: String = "[]",
    var secondaryDomainsJson: String = "[]",
) : Parcelable {

    var directApps: Set<String>
        get() = jsonToStringList(directAppsJson).toSet()
        set(value) { directAppsJson = stringListToJson(value) }

    var directDomains: List<String>
        get() = jsonToStringList(directDomainsJson)
        set(value) { directDomainsJson = stringListToJson(value) }

    var byeDpiApps: Set<String>
        get() = jsonToStringList(byeDpiAppsJson).toSet()
        set(value) { byeDpiAppsJson = stringListToJson(value) }

    var byeDpiDomains: List<String>
        get() = jsonToStringList(byeDpiDomainsJson)
        set(value) { byeDpiDomainsJson = stringListToJson(value) }

    var secondaryApps: Set<String>
        get() = jsonToStringList(secondaryAppsJson).toSet()
        set(value) { secondaryAppsJson = stringListToJson(value) }

    var secondaryDomains: List<String>
        get() = jsonToStringList(secondaryDomainsJson)
        set(value) { secondaryDomainsJson = stringListToJson(value) }

    companion object {
        fun jsonToStringList(json: String?): List<String> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                val list = ArrayList<String>(arr.length())
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i)
                    if (s.isNotBlank()) list.add(s)
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun stringListToJson(items: Collection<String>?): String {
            if (items.isNullOrEmpty()) return "[]"
            val arr = JSONArray()
            for (item in items) {
                if (item.isNotBlank()) arr.put(item)
            }
            return arr.toString()
        }
    }

    @androidx.room.Dao
    interface Dao {
        @Insert
        fun insert(profile: UserProfile): Long

        @Update
        fun update(profile: UserProfile): Int

        @Update
        fun update(profiles: List<UserProfile>): Int

        @Delete
        fun delete(profile: UserProfile): Int

        @Query("SELECT * FROM user_profiles WHERE id = :profileId")
        fun get(profileId: Long): UserProfile?

        @Query("SELECT * FROM user_profiles ORDER BY userOrder ASC, id ASC")
        fun list(): List<UserProfile>

        @Query("DELETE FROM user_profiles")
        fun clear()

        @Query("SELECT MAX(userOrder) + 1 FROM user_profiles")
        fun nextOrder(): Long?

        @Query("SELECT count(*) FROM user_profiles")
        fun count(): Int
    }
}
