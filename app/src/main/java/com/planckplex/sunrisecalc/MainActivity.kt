package com.planckplex.sunrisecalc

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.planckplex.sunrisecalc.ui.theme.SunriseCalcTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
// kotlin.math.pow is not used in this version, can be removed if not needed elsewhere

// Data class to hold the parsed event data
data class SunEventData(
    val date: String,
    val astronomicalDawn: String,
    val civilDawn: String,
    val nauticalDawn: String,
    val sunrise: String,
    val qth: String,
    val timezone: String
)

class MainActivity : ComponentActivity() {
    // Hold the QTH state that can be updated by GPS and observed by the Composable
    private var qthForComposable by mutableStateOf("")

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.i("Permission", "Location permission granted")
                fetchLocationAndSetQth()
            } else {
                Log.w("Permission", "Location permission denied")
                // Optionally, show a rationale or message to the user here
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SunriseCalcTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()) // Make the whole column scrollable
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SunTimesFetcherScreen(
                            initialQth = qthForComposable,
                            onRequestLocationPermission = {
                                requestLocationPermission()
                            }
                        )
                        Spacer(Modifier.height(32.dp))
                        EventInfoScreen()
                    }
                }
            }
        }
    }

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.i("Permission", "Location permission already granted")
                fetchLocationAndSetQth()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Log.i("Permission", "Showing rationale for location permission")
                // Here you might show a dialog explaining why you need the permission
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    internal fun fetchLocationAndSetQth() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        Log.i("Location", "Location fetched: Lat=${it.latitude}, Lon=${it.longitude}")
                        val qth = convertCoordsToQth(it.latitude, it.longitude, 6)
                        Log.i("QTHConversion", "Calculated QTH: $qth")
                        qthForComposable = qth // Update the Activity's state variable
                    } ?: run {
                        Log.w("Location", "Failed to get location (location is null)")
                        // Consider showing a toast or message to the user
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Location", "Error getting location", e)
                    // Consider showing a toast or message to the user
                }
        } else {
            Log.w("Location", "Location permission not granted at time of fetch.")
        }
    }

    private fun convertCoordsToQth(lat: Double, lon: Double, precision: Int = 6): String {
        var adjLat = lat + 90.0
        var adjLon = lon + 180.0
        val qth = StringBuilder()

        qth.append(('A' + (adjLon / 20).toInt()))
        qth.append(('A' + (adjLat / 10).toInt()))

        if (precision >= 4) {
            adjLon %= 20.0
            adjLat %= 10.0
            qth.append(('0' + (adjLon / 2).toInt()))
            qth.append(('0' + (adjLat / 1).toInt()))
        }

        if (precision >= 6) {
            adjLon %= 2.0
            adjLat %= 1.0
            qth.append(('a' + (adjLon / (2.0 / 24.0)).toInt()))
            qth.append(('a' + (adjLat / (1.0 / 24.0)).toInt()))
        }
        return qth.toString().uppercase(Locale.ROOT)
    }
}

@Composable
fun SunTimesFetcherScreen(
    modifier: Modifier = Modifier,
    initialQth: String,
    onRequestLocationPermission: () -> Unit
) {
    var qthInput by remember(initialQth) { mutableStateOf(initialQth) }
    var sunEventData by remember { mutableStateOf<SunEventData?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val client = remember { OkHttpClient() }
    // val context = LocalContext.current // Not strictly needed here anymore for permission check

    LaunchedEffect(initialQth) {
        if (qthInput != initialQth) {
            qthInput = initialQth
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Entrez votre QTH Locator:", style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth(0.9f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = qthInput,
                onValueChange = { qthInput = it },
                label = { Text("QTH (ex: IM58KS)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    onRequestLocationPermission()
                }
            ) {
                Icon(Icons.Filled.GpsFixed, contentDescription = "Utiliser le GPS")
            }
        }

        Button(
            onClick = {
                if (qthInput.isNotBlank()) {
                    isLoading = true
                    errorMessage = null
                    sunEventData = null

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val request = Request.Builder()
                                .url("http://planckplex.site:2630/sun_times?qth=${qthInput.trim()}")
                                .build()

                            client.newCall(request).execute().use { response ->
                                if (!response.isSuccessful) {
                                    withContext(Dispatchers.Main) {
                                        errorMessage = "Erreur serveur: ${response.code}"
                                        isLoading = false
                                    }
                                    return@launch
                                }

                                val responseBody = response.body?.string()
                                if (responseBody != null) {
                                    val jsonObj = JSONObject(responseBody)
                                    // Basic validation for key existence
                                    if (!jsonObj.has("events") || !jsonObj.has("date") ||
                                        !jsonObj.has("qth") || !jsonObj.has("timezone")) { // CORRECTED
                                        withContext(Dispatchers.Main) {
                                            errorMessage = "Réponse du serveur malformée (clés manquantes)"
                                            isLoading = false
                                        }
                                        return@launch
                                    }
                                    val eventsObj = jsonObj.getJSONObject("events")
                                    val requiredEvents = listOf("Astronomical Dawn", "Civil Dawn (Aurore)", "Nautical Dawn", "Sunrise")
                                    if (!requiredEvents.all { eventsObj.has(it) }) {
                                        withContext(Dispatchers.Main) {
                                            errorMessage = "Réponse du serveur malformée (événements manquants)"
                                            isLoading = false
                                        }
                                        return@launch
                                    }

                                    val fetchedData = SunEventData(
                                        date = jsonObj.getString("date"),
                                        astronomicalDawn = eventsObj.getString("Astronomical Dawn"),
                                        civilDawn = eventsObj.getString("Civil Dawn (Aurore)"),
                                        nauticalDawn = eventsObj.getString("Nautical Dawn"),
                                        sunrise = eventsObj.getString("Sunrise"),
                                        qth = jsonObj.getString("qth"),
                                        timezone = jsonObj.getString("timezone") // CORRECTED
                                    )
                                    withContext(Dispatchers.Main) {
                                        sunEventData = fetchedData
                                        isLoading = false
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        errorMessage = "Réponse vide du serveur"
                                        isLoading = false
                                    }
                                }
                            }
                        } catch (e: org.json.JSONException) {
                            Log.e("SunTimesFetcher", "Error parsing JSON response", e)
                            withContext(Dispatchers.Main) {
                                errorMessage = "Erreur de format de réponse du serveur."
                                isLoading = false
                            }
                        }
                        catch (e: Exception) {
                            Log.e("SunTimesFetcher", "Error fetching sun times", e)
                            withContext(Dispatchers.Main) {
                                errorMessage = "Erreur: ${e.message}"
                                isLoading = false
                            }
                        }
                    }
                } else {
                    errorMessage = "Veuillez entrer un QTH Locator"
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Obtenir les heures")
            }
        }

        Spacer(Modifier.height(16.dp))

        // No need for a separate general CircularProgressIndicator here
        // as the button itself shows loading state.
        // If you still want one:
        // if (isLoading && sunEventData == null) {
        //    CircularProgressIndicator()
        // }


        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        sunEventData?.let { data ->
            SunEventDisplay(data = data)
        }
    }
}

@Composable
fun SunEventDisplay(data: SunEventData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Résultats pour QTH: ${data.qth}", style = MaterialTheme.typography.titleMedium)
            Text("Date: ${data.date} (${data.timezone})", style = MaterialTheme.typography.bodyLarge)
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            Text("🌌 Aube Astronomique: ${data.astronomicalDawn}")
            Text("🌊 Aube Nautique: ${data.nauticalDawn}")
            Text("🌇 Aube Civile (Aurore): ${data.civilDawn}")
            Text("🌞 Lever du Soleil: ${data.sunrise}")
        }
    }
}

@Composable
fun EventInfoScreen(modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }

    Button(
        onClick = { showDialog = true },
        modifier = modifier
    ) {
        Text("Afficher les infos sur les événements")
    }

    if (showDialog) {
        EventInfoDialog(onDismissRequest = { showDialog = false })
    }
}

@Composable
fun EventInfoDialog(onDismissRequest: () -> Unit) {
    val eventText = """
        🌌  Astronomical Dawn (Aube astronomique) :
            Moment où le Soleil est à 18° sous l’horizon — le ciel commence à s’éclaircir, mais aucune lumière naturelle n’est encore visible.

        🌊  Nautical Dawn (Aube nautique) :
            Soleil à 12° sous l’horizon — les formes de l’horizon sont perceptibles, utile en navigation.

        🌇  Civil Dawn (Aube civile / Aurore) :
            Soleil à 6° sous l’horizon — lumière suffisante pour les activités extérieures sans éclairage artificiel.

        🌞  Sunrise (Lever du Soleil) :
            Le bord supérieur du Soleil apparaît à l’horizon.
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "Informations sur les événements")
        },
        text = {
            val scrollState = rememberScrollState()
            Text(
                text = eventText,
                modifier = Modifier.verticalScroll(scrollState)
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text("OK")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SunriseCalcTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SunTimesFetcherScreen(
                    initialQth = "IM58KS", // Example for preview
                    onRequestLocationPermission = {} // Dummy for preview
                )
                Spacer(Modifier.height(32.dp))
                EventInfoScreen()
            }
        }
    }
}
