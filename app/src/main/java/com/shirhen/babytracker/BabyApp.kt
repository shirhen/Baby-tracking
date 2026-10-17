package com.shirhen.babytracker

import android.app.Application
import com.shirhen.babytracker.data.Repo
import com.shirhen.babytracker.sync.FamilySync
import com.shirhen.babytracker.sync.SyncWorker

class BabyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        FamilySync.start()
        SyncWorker.schedule(this)
    }
}
