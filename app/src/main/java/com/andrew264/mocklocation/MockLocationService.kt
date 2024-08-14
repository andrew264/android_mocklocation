package com.andrew264.mocklocation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


private const val CHANNEL_ID = "MockLocationServiceChannel"

class MockLocationService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private lateinit var locationManager: LocationManager


    private fun createNotification(lat: Double, lon: Double): Notification {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Mock Location Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mock Location Service")
            .setContentText("Lat: $lat | Lon: $lon")
            .setSmallIcon(R.drawable.ic_location)
            .setContentIntent(pendingIntent)
            .build()
    }


    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val lat = intent?.getDoubleExtra(MainActivity.EXTRA_LATITUDE, 0.0)
        val lon = intent?.getDoubleExtra(MainActivity.EXTRA_LONGITUDE, 0.0)
        startForeground(1, createNotification(lat ?: 0.0, lon ?: 0.0))

        intent?.let {
            val altitude = it.getDoubleExtra(MainActivity.EXTRA_ALTITUDE, 0.0)
            val refreshTime = it.getLongExtra(MainActivity.EXTRA_REFRESH_TIME, 2000L)
            if (lat != null && lon != null) {
                startMockLocationUpdates(lat, lon, altitude, refreshTime)
            } else {
                sendMockLocationErrorBroadcast()
            }
        }

        return START_STICKY
    }

    private fun startMockLocationUpdates(
        latitude: Double,
        longitude: Double,
        altitude: Double,
        refreshTime: Long
    ) {
        scope.launch {
            while (isActive) {
                try {
                    setMockLocation(latitude, longitude, altitude)
                    delay(refreshTime)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    sendMockLocationErrorBroadcast()
                    break
                }
            }
        }
    }

    private fun setMockLocation(latitude: Double, longitude: Double, altitude: Double) {
        locationManager.addTestProvider(
            LocationManager.GPS_PROVIDER,
            false, false, false, false, true,
            true, true, ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_FINE
        )
        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)

        val mockLocation = Location(LocationManager.GPS_PROVIDER).apply {
            this.latitude = latitude
            this.longitude = longitude
            this.altitude = altitude
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            accuracy = 1f
        }

        locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun sendMockLocationErrorBroadcast() {
        println("Sending mock location error broadcast")
        val intent = Intent("com.andrew264.mocklocation.MOCK_LOCATION_ERROR")
        sendBroadcast(intent)
    }
}