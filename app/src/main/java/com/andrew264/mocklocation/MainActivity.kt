package com.andrew264.mocklocation

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val REQUEST_CODE_LOCATION = 101
private const val REQUEST_CODE_NOTIFICATION = 112

class MainActivity : AppCompatActivity() {
    private lateinit var mapUrlInput: EditText
    private lateinit var extractButton: Button
    private lateinit var latitudeInput: EditText
    private lateinit var longitudeInput: EditText
    private lateinit var altitudeInput: EditText
    private lateinit var refreshTimeInput: EditText
    private lateinit var startMockServiceButton: Button
    private lateinit var stopMockServiceButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapUrlInput = findViewById(R.id.mapUrlInput)
        extractButton = findViewById(R.id.extractButton)
        latitudeInput = findViewById(R.id.latitudeInput)
        longitudeInput = findViewById(R.id.longitudeInput)
        altitudeInput = findViewById(R.id.altitudeInput)
        refreshTimeInput = findViewById(R.id.refreshTimeInput)
        startMockServiceButton = findViewById(R.id.startMockServiceButton)
        stopMockServiceButton = findViewById(R.id.stopMockServiceButton)

        extractButton.setOnClickListener { extractCoordinates() }
        startMockServiceButton.setOnClickListener { startMockLocationService() }
        stopMockServiceButton.setOnClickListener { stopMockLocationService() }

        registerReceiver(
            mockLocationErrorReceiver,
            IntentFilter("com.andrew264.mocklocation.MOCK_LOCATION_ERROR"), RECEIVER_EXPORTED
        )

        requestPermission()
    }

    private val mockLocationErrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            showMockLocationError()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mockLocationErrorReceiver)
    }

    private fun requestPermission() {
        val locationPermissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        )
        val notificationPermissions = listOf(
            Manifest.permission.POST_NOTIFICATIONS
        )

        if (!hasPermissions(locationPermissions)) {
            ActivityCompat.requestPermissions(
                this,
                locationPermissions.toTypedArray(),
                REQUEST_CODE_LOCATION
            )
        }

        if (!hasPermissions(notificationPermissions)) {
            ActivityCompat.requestPermissions(
                this,
                notificationPermissions.toTypedArray(),
                REQUEST_CODE_NOTIFICATION
            )
        }
    }

    private fun hasPermissions(permissions: List<String>): Boolean {
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    private fun extractCoordinates() {
        val url = mapUrlInput.text.toString().trim()

        when {
            url.startsWith("https://www.google.com/maps/place/") -> extractFromGoogleMapsUrl(url)
            """-?\d+(\.\d+)?,\s*-?\d+(\.\d+)?""".toRegex()
                .containsMatchIn(url) -> extractFromCsvFormat(url)

            else -> mapUrlInput.error = "Invalid URL or coordinate format"
        }
    }

    private fun extractFromGoogleMapsUrl(url: String) {
        val regex = "@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+),(-?\\d+\\.?\\d*)z?".toRegex()
        val matchResult = regex.find(url)

        if (matchResult != null) {
            val (latitude, longitude, altitude) = matchResult.destructured
            setCoordinates(latitude, longitude, altitude.takeIf { it.isNotEmpty() } ?: "0.0")
            showSuccessToast()
        } else {
            mapUrlInput.error = "Invalid Google Maps URL"
        }
    }

    private fun extractFromCsvFormat(csv: String) {
        try {
            val coordinates = csv.split(",").map { it.trim() }
            val latitude = coordinates[0]
            val longitude = coordinates[1]
            val altitude = coordinates.getOrNull(2)?.let {
                it.split("z")[0].takeIf { it.isNotEmpty() }
            } ?: "0.0"

            setCoordinates(latitude, longitude, altitude)
            showSuccessToast()
        } catch (e: Exception) {
            mapUrlInput.error = "Invalid CSV format"
        }
    }

    private fun setCoordinates(latitude: String, longitude: String, altitude: String) {
        latitudeInput.setText(latitude)
        longitudeInput.setText(longitude)
        altitudeInput.setText(altitude)
    }

    private fun showSuccessToast() {
        Toast.makeText(this, "Coordinates extracted", Toast.LENGTH_SHORT).show()
    }

    private fun startMockLocationService() {
        val intent = Intent(this, MockLocationService::class.java).apply {
            putExtra(EXTRA_LATITUDE, latitudeInput.text.toString().toDoubleOrNull() ?: 0.0)
            putExtra(EXTRA_LONGITUDE, longitudeInput.text.toString().toDoubleOrNull() ?: 0.0)
            putExtra(EXTRA_ALTITUDE, altitudeInput.text.toString().toDoubleOrNull() ?: 0.0)
            putExtra(
                EXTRA_REFRESH_TIME,
                refreshTimeInput.text.toString().toLongOrNull() ?: 2000L
            )
        }
        startService(intent)
        Toast.makeText(this, "Mock location service started", Toast.LENGTH_SHORT).show()


    }

    private fun stopMockLocationService() {
        val intent = Intent(this, MockLocationService::class.java)
        Toast.makeText(this, "Mock location service stopped", Toast.LENGTH_SHORT).show()
        stopService(intent)
    }

    private fun showMockLocationError() {
        AlertDialog.Builder(this)
            .setTitle("Mock Location Error")
            .setMessage("Please set this app as the mock location app in Developer Options.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_ALTITUDE = "extra_altitude"
        const val EXTRA_REFRESH_TIME = "extra_refresh_time"
    }
}