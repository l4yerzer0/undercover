package com.undercover.vpn.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Profile::class, RemoteServer::class, UserProfile::class],
    version = 4,
    exportSchema = true,
)
abstract class ProfileDatabase : RoomDatabase() {
    abstract fun profileDao(): Profile.Dao

    abstract fun remoteServerDao(): RemoteServer.Dao

    abstract fun userProfileDao(): UserProfile.Dao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // Add icon column to profiles table with default value null
                    database.execSQL("ALTER TABLE profiles ADD COLUMN icon TEXT DEFAULT NULL")
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        "CREATE TABLE IF NOT EXISTS `remote_servers` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`userOrder` INTEGER NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`url` TEXT NOT NULL, " +
                            "`secret` TEXT NOT NULL)",
                    )
                }
            }

        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE profiles ADD COLUMN assignedProfileId INTEGER NOT NULL DEFAULT 0")
                    database.execSQL(
                        "CREATE TABLE IF NOT EXISTS `user_profiles` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`userOrder` INTEGER NOT NULL, " +
                            "`primaryServerId` INTEGER NOT NULL, " +
                            "`primaryNodeTag` TEXT, " +
                            "`dualServerEnabled` INTEGER NOT NULL, " +
                            "`secondaryServerId` INTEGER NOT NULL, " +
                            "`secondaryNodeTag` TEXT, " +
                            "`routingDirectRuSites` INTEGER NOT NULL, " +
                            "`routingDirectRuApps` INTEGER NOT NULL, " +
                            "`routingByeDpiYouTube` INTEGER NOT NULL, " +
                            "`routingByeDpiDiscord` INTEGER NOT NULL, " +
                            "`directAppsJson` TEXT NOT NULL, " +
                            "`directDomainsJson` TEXT NOT NULL, " +
                            "`byeDpiAppsJson` TEXT NOT NULL, " +
                            "`byeDpiDomainsJson` TEXT NOT NULL, " +
                            "`secondaryAppsJson` TEXT NOT NULL, " +
                            "`secondaryDomainsJson` TEXT NOT NULL)",
                    )
                }
            }
    }
}
