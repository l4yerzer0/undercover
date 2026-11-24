package com.undercover.vpn.database

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.parcelize.Parcelize

@Entity(
    tableName = "profiles",
)
@TypeConverters(TypedProfile.Convertor::class)
@Parcelize
class Profile(
    @PrimaryKey(autoGenerate = true) var id: Long = 0L,
    var userOrder: Long = 0L,
    var name: String = "",
    @ColumnInfo(defaultValue = "NULL") var icon: String? = null,
    var typed: TypedProfile = TypedProfile(),
    @ColumnInfo(defaultValue = "0") var assignedProfileId: Long = 0L,
) : Parcelable {
    @androidx.room.Dao
    interface Dao {
        @Insert
        fun insert(profile: Profile): Long

        @Update
        fun update(profile: Profile): Int

        @Update
        fun update(profile: List<Profile>): Int

        @Delete
        fun delete(profile: Profile): Int

        @Delete
        fun delete(profile: List<Profile>): Int

        @Query("SELECT * FROM profiles WHERE id = :profileId")
        fun get(profileId: Long): Profile?

        @Query("select * from profiles order by userOrder asc")
        fun list(): List<Profile>

        @Query("SELECT * FROM profiles WHERE assignedProfileId = 0 OR assignedProfileId = :profileId ORDER BY userOrder ASC")
        fun listForProfile(profileId: Long): List<Profile>

        @Query("DELETE FROM profiles")
        fun clear()

        @Query("SELECT MAX(userOrder) + 1 FROM profiles")
        fun nextOrder(): Long?

        @Query("SELECT MAX(id) + 1 FROM profiles")
        fun nextFileID(): Long?
    }
}
