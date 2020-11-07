package org.appspot.apprtc.stars;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class MyService  extends Service {


    @Override

    public void onCreate() {
        super.onCreate();

    }




    @Override
    public IBinder onBind(Intent intent) {

        return null;

    }



    @Override

    public int onStartCommand(Intent intent, int flags, int startId) {


        return START_STICKY;

    }



    @Override

    public void onDestroy() {

        super.onDestroy();

    }

}