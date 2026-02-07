package com.example.target

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity
data class User(
  @PrimaryKey val uid: Int,
  @ColumnInfo(name = "first_name") val firstName: String?,
  @ColumnInfo(name = "last_name") val lastName: String?
)

@Dao
interface UserDao {
  @Query("SELECT * FROM user")
  fun getAll(): List<User>

  @Query("SELECT * FROM user WHERE uid IN (:userIds)")
  fun loadAllByIds(userIds: IntArray): List<User>

  @Query("SELECT * FROM user WHERE first_name LIKE :first AND " +
           "last_name LIKE :last LIMIT 1")
  fun findByName(first: String, last: String): User

  @Insert
  fun insertAll(users: Array<User>)

  @Delete
  fun delete(user: User)
}

@Database(entities = [User::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
  abstract fun userDao(): UserDao
}