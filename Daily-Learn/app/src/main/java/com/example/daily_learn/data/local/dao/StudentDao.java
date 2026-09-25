package com.example.daily_learn.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.daily_learn.data.local.entity.Student;

import java.util.List;

@Dao
public interface StudentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Student student);

    @Update
    void update(Student student);

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    LiveData<Student> getStudentById(long id);

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    Student getStudentByIdSync(long id);

    @Query("SELECT * FROM students ORDER BY id DESC")
    LiveData<List<Student>> getAllStudents();

    @Query("SELECT * FROM students ORDER BY id DESC")
    List<Student> getAllStudentsSync();

    @Query("SELECT * FROM students LIMIT 1")
    LiveData<Student> getStudent();

    @Query("SELECT * FROM students LIMIT 1")
    Student getStudentSync();

    @Query("DELETE FROM students WHERE id = :id")
    void deleteStudentById(long id);

    @Query("DELETE FROM students")
    void deleteAll();
}
