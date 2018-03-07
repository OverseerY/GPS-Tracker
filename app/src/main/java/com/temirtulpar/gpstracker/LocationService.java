package com.temirtulpar.gpstracker;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LocationService extends Service {
    private static final String TAG = "TESTGPS";

    public static final int NOTIFICATION_ID = 5454;

    private String mLatitude;
    private String mLongitude;
    private String mTime;
    private int mId;
    private String sTime;

    Ticket ticket;
    private String IMEI = "";

    private LocationManager locationManager = null;

    private static final int LOCATION_INTERVAL = 1000;
    private static final float LOCATION_DISTANCE = 1f;

    private class LocationListener implements android.location.LocationListener {
        Location mLastLocation;

        public LocationListener(String provider) {
            Log.e(TAG, "LocationListener " + provider);
            mLastLocation = new Location(provider);
        }

        @Override
        public void onLocationChanged(Location location) {
            Log.e(TAG, "onLocationChanged: " + location);
            mLastLocation.set(location);
            mLongitude = location.convert(location.getLongitude(), location.FORMAT_DEGREES);
            mLatitude = location.convert(location.getLatitude(), location.FORMAT_DEGREES);
            mTime = converteTime(location.getTime());
            mId = Integer.parseInt(getCurrentTimeStamp());
            //Toast.makeText(getApplicationContext(), mLatitude + "," + mLongitude, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.e(TAG, "onProviderDisabled: " + provider);
            Toast.makeText(getApplicationContext(), "Provider disabled: "+ provider.toString(), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.e(TAG, "onProviderEnabled: " + provider);
            Toast.makeText(getApplicationContext(), "Provider enabled: "+ provider.toString(), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.e(TAG, "onStatusChanged: " + provider);
        }
    }

    LocationListener[] mLocationListeners = new LocationListener[]{
            new LocationListener(LocationManager.GPS_PROVIDER),
            new LocationListener(LocationManager.NETWORK_PROVIDER)
    };

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG, "onStartCommand");
        super.onStartCommand(intent, flags, startId);
        Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show();
        sendLocation();
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        Log.e(TAG, "onCreate");
        initializeLocationManager();
        IMEI= getImei();
        try {
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mLocationListeners[1]);
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "Fail to request location update, ignore", ex);
            Toast.makeText(this, "Fail to request location update", Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "Network provider does not exist, " + ex.getMessage());
            Toast.makeText(this, "Network provider does not exist", Toast.LENGTH_SHORT);
        }
        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mLocationListeners[0]);
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "Fail to request location update, ignore", ex);
            Toast.makeText(this, "Fail to request location update", Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "Gps provider does not exist " + ex.getMessage());
            Toast.makeText(this, "Gps provider does not exist", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "onDestroy");
        Toast.makeText(this,"Service destroyed", Toast.LENGTH_SHORT).show();
        super.onDestroy();
        if (locationManager != null) {
            for (int i = 0; i < mLocationListeners.length; i++) {
                try {
                    locationManager.removeUpdates(mLocationListeners[i]);
                } catch (Exception ex) {
                    Log.i(TAG, "Fail to remove location listners, ignore", ex);
                }
            }
        }
    }

    private void initializeLocationManager() {
        Log.e(TAG, "initializeLocationManager");
        if (locationManager == null) {
            locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
            Toast.makeText(this, "Location Manager Initialized", Toast.LENGTH_SHORT).show();
        }
    }

    public String getImei() {
        String imei = null;
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(getApplicationContext().TELEPHONY_SERVICE);
            imei = telephonyManager.getDeviceId();
        }
        return imei;
    }

    private class HttpAsyncTask extends AsyncTask<String, Void, String> {
        String curLocation = "" + mTime + ";" + mLatitude +";" + mLongitude;
        String curImei = IMEI;
        String curId = "" + mId;
        @Override
        protected String doInBackground(String... urls) {
            ticket = new Ticket();
            ticket.setTicketId(curId);
            ticket.setDescription(curLocation);
            ticket.setImei(curImei);

            return POST(urls[0], ticket);
        }
    }

    public static String POST(String url, Ticket ticket) {
        InputStream inputStream = null;
        String result = "";

        try {
            HttpClient httpClient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(url);
            String json = "";

            JSONObject jsonObject = new JSONObject();
            jsonObject.accumulate("Id", ticket.getTicketId());
            jsonObject.accumulate("Name", ticket.getDescription());
            jsonObject.accumulate("IMEI", ticket.getImei());
            json = jsonObject.toString();

            StringEntity stringEntity = new StringEntity(json);
            httpPost.setEntity(stringEntity);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type","application/json");
            HttpResponse httpResponse = httpClient.execute(httpPost);
            inputStream = httpResponse.getEntity().getContent();

            if (inputStream != null)
                result = convertInputStreamToString(inputStream);
            else
                result = null;
        } catch (Exception e) {
            Log.d("Input stream:", e.getLocalizedMessage());
        }
        return result;
    }

    private static String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line = "";
        String result = "";

        while ((line = bufferedReader.readLine()) != null)
            result += line;

        inputStream.close();

        return result;
    }

    private void sendLocation() {
        final Handler handler = new Handler();

        handler.post(new Runnable() {
            @Override
            public void run() {
                handler.postDelayed(this, 20000);
                new HttpAsyncTask().execute("http://testapi312.azurewebsites.net/api/values/");
                showNotification();
                //Toast.makeText(getApplicationContext(), "Location has been sent", Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void showNotification() {
        sTime = getSendTimeStamp();

        Intent intent = new Intent(this, MainActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(intent);
        PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new Notification.Builder(getApplicationContext())
                .setSmallIcon(R.mipmap.ic_launch)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Location has been sent " + sTime )
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_MAX)
                .setContentIntent(pendingIntent)
                .build();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private String converteTime(long locTime) {
        DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        Date date = new Date(locTime);
        String formatted = format.format(date);
        return formatted;
    }

    public String getCurrentTimeStamp() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("ddMMHHmm");//dd/MM/yyyy
        Date now = new Date();
        String strDate = sdfDate.format(now);
        return strDate;
    }

    public String getSendTimeStamp() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");//dd/MM/yyyy
        Date now = new Date();
        String strDate = sdfDate.format(now);
        return strDate;
    }
}
