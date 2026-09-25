package com.example.daily_learn.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.daily_learn.data.local.AppDatabase;
import com.example.daily_learn.data.local.dao.StudentDao;
import com.example.daily_learn.data.local.entity.Student;
import com.example.daily_learn.data.prefs.SettingsStore;

import java.util.List;
import java.util.concurrent.Executors;

public class OnboardingViewModel extends AndroidViewModel {

    private final StudentDao studentDao;
    private final SettingsStore settings;

    public final LiveData<List<Student>> existingStudents;
    public final MutableLiveData<Boolean> profileSaved = new MutableLiveData<>(false);
    public final MutableLiveData<String> error = new MutableLiveData<>();

    public OnboardingViewModel(@NonNull Application app) {
        super(app);
        studentDao = AppDatabase.getInstance(app).studentDao();
        settings   = new SettingsStore(app);
        existingStudents = studentDao.getAllStudents();
    }

    public void loginAs(Student student) {
        if (student == null) return;
        settings.setStudentId(student.id);
        settings.setDailyStudyMinutes(student.dailyStudyMinutes);
        settings.setOnboardingDone(true);
        settings.setLoggedIn(true);
        profileSaved.postValue(true);
    }

    public void saveProfile(String name, String course, String semester, int dailyMinutes) {
        if (name == null || name.trim().isEmpty()) {
            error.setValue("Please enter your name");
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            Student student = new Student(name.trim(), course.trim(), semester.trim(), dailyMinutes);
            long id = studentDao.insert(student);

            settings.setStudentId(id);
            settings.setDailyStudyMinutes(dailyMinutes);
            settings.setOnboardingDone(true);
            settings.setLoggedIn(true);

            profileSaved.postValue(true);
        });
    }
}
