package com.example.daily_learn.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPreferences wrapper for app-level settings.
 * Single source of truth for non-Room preferences.
 */
public class SettingsStore {

    private static final String PREFS_NAME = "daily_learn_prefs";

    // Keys
    private static final String KEY_ONBOARDING_DONE          = "onboarding_complete";
    private static final String KEY_LOGGED_IN                = "is_logged_in";
    private static final String KEY_DAILY_STUDY_MINUTES      = "daily_study_minutes";
    private static final String KEY_NOTIF_DAILY_REMINDER     = "notif_daily_reminder";
    private static final String KEY_NOTIF_EXAM_WARNING       = "notif_exam_warning";
    private static final String KEY_NOTIF_BEHIND_SCHEDULE    = "notif_behind_schedule";
    private static final String KEY_STUDENT_ID               = "student_id";

    private final SharedPreferences prefs;

    public SettingsStore(Context context) {
        this.prefs = context.getApplicationContext()
                            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ── Auth & Onboarding ──────────────────────────────────────────────────
    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_LOGGED_IN, false) && getStudentId() > 0;
    }

    public void setLoggedIn(boolean loggedIn) {
        prefs.edit().putBoolean(KEY_LOGGED_IN, loggedIn).apply();
    }

    public boolean isOnboardingDone() {
        return prefs.getBoolean(KEY_ONBOARDING_DONE, false);
    }

    public void setOnboardingDone(boolean done) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply();
    }

    public void logout() {
        prefs.edit()
             .putBoolean(KEY_LOGGED_IN, false)
             .putLong(KEY_STUDENT_ID, -1L)
             .apply();
    }

    // ── Student ID ──────────────────────────────────────────────────────────
    public long getStudentId() {
        return prefs.getLong(KEY_STUDENT_ID, -1L);
    }

    public void setStudentId(long id) {
        prefs.edit().putLong(KEY_STUDENT_ID, id).apply();
    }

    // ── Daily study minutes ─────────────────────────────────────────────────
    public int getDailyStudyMinutes() {
        return prefs.getInt(KEY_DAILY_STUDY_MINUTES, 120);
    }

    public void setDailyStudyMinutes(int minutes) {
        prefs.edit().putInt(KEY_DAILY_STUDY_MINUTES, minutes).apply();
    }

    // ── Notifications ───────────────────────────────────────────────────────
    public boolean isDailyReminderEnabled() {
        return prefs.getBoolean(KEY_NOTIF_DAILY_REMINDER, false);
    }

    public void setDailyReminderEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIF_DAILY_REMINDER, enabled).apply();
    }

    public boolean isExamWarningEnabled() {
        return prefs.getBoolean(KEY_NOTIF_EXAM_WARNING, false);
    }

    public void setExamWarningEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIF_EXAM_WARNING, enabled).apply();
    }

    public boolean isBehindScheduleNotifEnabled() {
        return prefs.getBoolean(KEY_NOTIF_BEHIND_SCHEDULE, false);
    }

    public void setBehindScheduleNotifEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIF_BEHIND_SCHEDULE, enabled).apply();
    }

    // ── Reset ───────────────────────────────────────────────────────────────
    public void clearAll() {
        prefs.edit().clear().apply();
    }
}
