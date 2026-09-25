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

import java.util.concurrent.Executors;

public class ProfileViewModel extends AndroidViewModel {

    private final StudentDao studentDao;
    private final SettingsStore settings;

    public final LiveData<Student> student;
    public final MutableLiveData<Boolean> logoutDone = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> deleteDone = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> saveDone   = new MutableLiveData<>(false);

    public ProfileViewModel(@NonNull Application app) {
        super(app);
        studentDao = AppDatabase.getInstance(app).studentDao();
        settings   = new SettingsStore(app);
        long studentId = settings.getStudentId();
        student    = studentId > 0 ? studentDao.getStudentById(studentId) : studentDao.getStudent();
    }

    public boolean isDailyReminderEnabled()   { return settings.isDailyReminderEnabled(); }
    public boolean isExamWarningEnabled()     { return settings.isExamWarningEnabled(); }
    public boolean isBehindNotifEnabled()     { return settings.isBehindScheduleNotifEnabled(); }

    public void setDailyReminder(boolean v)   { settings.setDailyReminderEnabled(v); }
    public void setExamWarning(boolean v)     { settings.setExamWarningEnabled(v); }
    public void setBehindNotif(boolean v)     { settings.setBehindScheduleNotifEnabled(v); }
    public int getDailyStudyMinutes()         { return settings.getDailyStudyMinutes(); }
    public void setDailyStudyMinutes(int m)   { settings.setDailyStudyMinutes(m); }

    public void saveStudentProfile(Student s) {
        Executors.newSingleThreadExecutor().execute(() -> {
            studentDao.update(s);
            saveDone.postValue(true);
        });
    }

    /** Log out: keeps all database records safe, marks session logged out */
    public void logout() {
        settings.logout();
        logoutDone.setValue(true);
    }

    /** Delete active student profile and their associated records */
    public void deleteProfile(long studentId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            studentDao.deleteStudentById(studentId);
            settings.logout();
            deleteDone.postValue(true);
        });
    }
}
