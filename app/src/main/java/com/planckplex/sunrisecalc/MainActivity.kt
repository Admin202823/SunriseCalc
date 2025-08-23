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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.planckplex.sunrisecalc.ui.theme.SunriseCalcTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

// --- Data Classes ---
data class MoonPhaseData(
    val emoji: String,
    val name: String,
    val percent: Double
)

data class MoonData(
    val moonrise: String?,
    val moonset: String?,
    val phase: MoonPhaseData?,
    val elevation: Double?,
    val highMoonElevation: Double?, // Nouveau
    val highMoonTime: String?,     // Nouveau
    val moonriseAzimuth: Double?, // Nouveau
    val moonsetAzimuth: Double?   // Nouveau
)

data class SunMoonEventData(
    val date: String,
    val astronomicalDawn: String,
    val civilDawn: String,
    val nauticalDawn: String,
    val sunrise: String,
    val moon: MoonData?,
    val qth: String,
    val timezone: String
)

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {
    private var qthForComposable by mutableStateOf("")
    private var fetchedSunMoonData by mutableStateOf<SunMoonEventData?>(null)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.i("Permission", "Location permission granted")
                fetchLocationAndSetQth()
            } else {
                Log.w("Permission", "Location permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SunriseCalcTheme {
                val pagerState = rememberPagerState(pageCount = { 2 })
                val isPagerSwipeEnabled by remember {
                    derivedStateOf { fetchedSunMoonData != null }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        userScrollEnabled = isPagerSwipeEnabled
                    ) { page ->
                        when (page) {
                            0 -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    SunTimesFetcherScreen(
                                        initialQth = qthForComposable,
                                        onRequestLocationPermission = { requestLocationPermission() },
                                        onDataFetched = { data ->
                                            fetchedSunMoonData = data
                                        }
                                    )
                                    Spacer(Modifier.height(32.dp))
                                    EventInfoScreen()
                                }
                            }
                            1 -> {
                                SecondPageContent(
                                    initialSunMoonData = fetchedSunMoonData
                                )
                            }
                        }
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
                fetchLocationAndSetQth()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
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
                        val qth = convertCoordsToQth(it.latitude, it.longitude, 6)
                        qthForComposable = qth
                        fetchedSunMoonData = null
                    } ?: Log.w("Location", "Failed to get location (location is null)")
                }
                .addOnFailureListener { e ->
                    Log.e("Location", "Error getting location", e)
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

data class PartialSunData(
    val date: String,
    val astronomicalDawn: String,
    val civilDawn: String,
    val nauticalDawn: String,
    val sunrise: String,
    val qth: String,
    val timezone: String
)

@Composable
fun SunTimesFetcherScreen(
    modifier: Modifier = Modifier,
    initialQth: String,
    onRequestLocationPermission: () -> Unit,
    onDataFetched: (SunMoonEventData) -> Unit
) {
    var qthInput by remember(initialQth) { mutableStateOf(initialQth) }
    var sunEventDisplayData by remember { mutableStateOf<SunMoonEventData?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val client = remember { OkHttpClient() }

    LaunchedEffect(initialQth) {
        if (qthInput != initialQth && initialQth.isNotBlank()) {
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
                onValueChange = { qthInput = it.uppercase(Locale.ROOT).trim() },
                label = { Text("QTH (ex: IM58KS)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onRequestLocationPermission) {
                Icon(Icons.Filled.GpsFixed, contentDescription = "Utiliser le GPS")
            }
        }

        Button(
            onClick = {
                if (qthInput.isNotBlank()) {
                    isLoading = true
                    errorMessage = null
                    sunEventDisplayData = null

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val sunDataDeferred = async {
                                val request = Request.Builder()
                                    .url("http://planckplex.site:2630/sun?qth=${qthInput.trim()}")
                                    .build()
                                client.newCall(request).execute().use { response ->
                                    if (!response.isSuccessful) throw Exception("Erreur données solaires: ${response.code}")
                                    val body = response.body?.string() ?: throw Exception("Réponse solaire vide")
                                    val jsonObj = JSONObject(body)
                                    val requiredSunKeys = listOf("date", "events", "qth", "timezone")
                                    if (!requiredSunKeys.all { jsonObj.has(it) && !jsonObj.isNull(it) }) {
                                        throw Exception("Réponse solaire malformée (clés principales manquantes)")
                                    }
                                    val eventsObj = jsonObj.getJSONObject("events")
                                    val requiredSunEvents = listOf("Astronomical Dawn", "Civil Dawn (Aurore)", "Nautical Dawn", "Sunrise")
                                    if (!requiredSunEvents.all { eventsObj.has(it) && !eventsObj.isNull(it) }) {
                                        throw Exception("Réponse solaire malformée (événements manquants)")
                                    }
                                    PartialSunData(
                                        date = jsonObj.getString("date"),
                                        astronomicalDawn = eventsObj.getString("Astronomical Dawn"),
                                        civilDawn = eventsObj.getString("Civil Dawn (Aurore)"),
                                        nauticalDawn = eventsObj.getString("Nautical Dawn"),
                                        sunrise = eventsObj.getString("Sunrise"),
                                        qth = jsonObj.getString("qth"),
                                        timezone = jsonObj.getString("timezone")
                                    )
                                }
                            }

                            val moonDataDeferred = async {
                                val request = Request.Builder()
                                    .url("http://planckplex.site:2630/moon?qth=${qthInput.trim()}")
                                    .build()
                                client.newCall(request).execute().use { response ->
                                    if (!response.isSuccessful) throw Exception("Erreur données lunaires: ${response.code}")
                                    val body = response.body?.string() ?: throw Exception("Réponse lunaire vide")
                                    val jsonObj = JSONObject(body)
                                    if (!jsonObj.has("moon") || jsonObj.isNull("moon")) {
                                        throw Exception("Réponse lunaire malformée (clé 'moon' manquante)")
                                    }
                                    val moonObj = jsonObj.getJSONObject("moon")
                                    var phaseData: MoonPhaseData? = null
                                    if (moonObj.has("phase") && !moonObj.isNull("phase")) {
                                        val phaseObj = moonObj.getJSONObject("phase")
                                        if (phaseObj.has("emoji") && !phaseObj.isNull("emoji") &&
                                            phaseObj.has("name") && !phaseObj.isNull("name") &&
                                            phaseObj.has("percent")
                                        ) {
                                            phaseData = MoonPhaseData(
                                                emoji = phaseObj.getString("emoji"),
                                                name = phaseObj.getString("name"),
                                                percent = phaseObj.optDouble("percent", 0.0)
                                            )
                                        } else {
                                            Log.w("SunTimesFetcher", "Moon phase data incomplete in /moon response.")
                                        }
                                    }
                                    MoonData(
                                        moonrise = if (moonObj.has("moonrise") && !moonObj.isNull("moonrise")) moonObj.getString("moonrise") else null,
                                        moonset = if (moonObj.has("moonset") && !moonObj.isNull("moonset")) moonObj.getString("moonset") else null,
                                        phase = phaseData,
                                        elevation = if (moonObj.has("elevation")) moonObj.optDouble("elevation") else null,
                                        highMoonElevation = if (moonObj.has("high_moon_elevation")) moonObj.optDouble("high_moon_elevation") else null,
                                        highMoonTime = if (moonObj.has("high_moon_time") && !moonObj.isNull("high_moon_time")) moonObj.getString("high_moon_time") else null,
                                        moonriseAzimuth = if (moonObj.has("moonrise_azimuth")) moonObj.optDouble("moonrise_azimuth") else null,
                                        moonsetAzimuth = if (moonObj.has("moonset_azimuth")) moonObj.optDouble("moonset_azimuth") else null
                                    )
                                }
                            }

                            val partialSunData = sunDataDeferred.await()
                            val parsedMoonData = moonDataDeferred.await()

                            val combinedData = SunMoonEventData(
                                date = partialSunData.date,
                                astronomicalDawn = partialSunData.astronomicalDawn,
                                civilDawn = partialSunData.civilDawn,
                                nauticalDawn = partialSunData.nauticalDawn,
                                sunrise = partialSunData.sunrise,
                                moon = parsedMoonData,
                                qth = partialSunData.qth,
                                timezone = partialSunData.timezone
                            )

                            withContext(Dispatchers.Main) {
                                sunEventDisplayData = combinedData
                                onDataFetched(combinedData)
                                isLoading = false
                            }

                        } catch (e: Exception) {
                            Log.e("SunTimesFetcher", "Error fetching combined data: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                errorMessage = "Erreur de récupération: ${e.localizedMessage}"
                                isLoading = false
                            }
                        }
                    }
                } else {
                    errorMessage = "Veuillez entrer un QTH Locator"
                }
            },
            enabled = !isLoading && qthInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Obtenir les heures")
            }
        }
        Spacer(Modifier.height(16.dp))
        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        sunEventDisplayData?.let { data ->
            SunEventDisplay(
                date = data.date,
                timezone = data.timezone,
                qth = data.qth,
                astronomicalDawn = data.astronomicalDawn,
                nauticalDawn = data.nauticalDawn,
                civilDawn = data.civilDawn,
                sunrise = data.sunrise
            )
        }
    }
}

@Composable
fun SunEventDisplay(
    date: String,
    timezone: String,
    qth: String,
    astronomicalDawn: String,
    nauticalDawn: String,
    civilDawn: String,
    sunrise: String
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Résultats Solaires pour QTH: $qth", style = MaterialTheme.typography.titleMedium)
            Text("Date: $date ($timezone)", style = MaterialTheme.typography.bodyLarge)
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            Text("🌌 Aube Astronomique: $astronomicalDawn")
            Text("🌊 Aube Nautique: $nauticalDawn")
            Text("🌇 Aube Civile (Aurore): $civilDawn")
            Text("🌞 Lever du Soleil: $sunrise")
        }
    }
}

@Composable
fun EventInfoScreen(modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    Button(onClick = { showDialog = true }, modifier = modifier) {
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
        title = { Text("Informations sur les événements") },
        text = { Text(eventText, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text("OK") } }
    )
}

@Composable
fun SecondPageContent(
    initialSunMoonData: SunMoonEventData?
) {
    val currentDisplayData = initialSunMoonData
    val currentQth = currentDisplayData?.qth
    val currentMoonData = currentDisplayData?.moon

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Informations Lunaires ${currentQth?.let { "pour $it" } ?: ""}",
                style = MaterialTheme.typography.headlineMedium
            )

            if (currentMoonData != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Lever de Lune: ${currentMoonData.moonrise ?: "Non aujourd'hui"}", style = MaterialTheme.typography.bodyLarge)
                        currentMoonData.moonriseAzimuth?.let {
                            Text("Azimut Lever: ${String.format(Locale.US, "%.2f", it)}°", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("Coucher de Lune: ${currentMoonData.moonset ?: "Non aujourd'hui"}", style = MaterialTheme.typography.bodyLarge)
                        currentMoonData.moonsetAzimuth?.let {
                            Text("Azimut Coucher: ${String.format(Locale.US, "%.2f", it)}°", style = MaterialTheme.typography.bodyMedium)
                        }

                        currentMoonData.highMoonTime?.let { time ->
                            Text("Apogée Lunaire (Heure): $time", style = MaterialTheme.typography.bodyLarge)
                        }
                        currentMoonData.highMoonElevation?.let { elev ->
                            Text("Apogée Lunaire (Élévation): ${String.format(Locale.US, "%.2f", elev)}°", style = MaterialTheme.typography.bodyLarge)
                        }

                        currentMoonData.elevation?.let { elev ->
                            Text("Élévation Actuelle: ${String.format(Locale.US, "%.2f", elev)}°", style = MaterialTheme.typography.bodyLarge)
                        }

                        currentMoonData.phase?.let { phase ->
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                            Text("Phase Lunaire:", style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(phase.emoji, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 8.dp))
                                Column {
                                    Text(phase.name, style = MaterialTheme.typography.bodyLarge)
                                    Text("${String.format(Locale.US, "%.1f", phase.percent)}% visible", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        } ?: Text("Données de phase non disponibles.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (currentDisplayData != null) {
                Text(
                    "Données lunaires non disponibles pour $currentQth (ou erreur lors du fetch).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    "Veuillez d'abord obtenir les données sur la page principale.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}


// --- Previews ---
@OptIn(ExperimentalFoundationApi::class)
@Preview(showBackground = true, name = "Default Preview - Page 1 (Dual Fetch Extended)")
@Composable
fun DefaultPreviewPage1DualFetchExtended() {
    SunriseCalcTheme {
        val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            HorizontalPager(state = pagerState, modifier = Modifier.padding(innerPadding).fillMaxSize(), userScrollEnabled = true) { page ->
                when (page) {
                    0 -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        SunTimesFetcherScreen(initialQth = "IM58KS", onRequestLocationPermission = {}, onDataFetched = {})
                        Spacer(Modifier.height(32.dp))
                        EventInfoScreen()
                    }
                    1 -> SecondPageContent(
                        initialSunMoonData = SunMoonEventData(
                            date = "2025-07-16", astronomicalDawn = "04:32:07", civilDawn = "05:53:37",
                            nauticalDawn = "05:15:15", sunrise = "06:25:11",
                            moon = MoonData(
                                moonrise = "23:50:00", moonset = "11:09:00",
                                phase = MoonPhaseData(emoji = "🌖", name = "Gibbeuse décroissante", percent = 64.2),
                                elevation = -52.08,
                                highMoonElevation = 44.82, highMoonTime = "05:12:00",
                                moonriseAzimuth = 91.06, moonsetAzimuth = 264.52
                            ),
                            qth = "IM58KS", timezone = "UTC+1"
                        )
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Page 2 with Extended Combined Data Preview")
@Composable
fun PreviewPage2ExtendedCombined() {
    SunriseCalcTheme {
        SecondPageContent(
            initialSunMoonData = SunMoonEventData(
                date = "2025-07-16", astronomicalDawn = "04:32:07", civilDawn = "05:53:37",
                nauticalDawn = "05:15:15", sunrise = "06:25:11",
                moon = MoonData(
                    moonrise = "23:50:00", moonset = "11:09:00",
                    phase = MoonPhaseData(emoji = "🌖", name = "Gibbeuse décroissante", percent = 64.2),
                    elevation = -52.08,
                    highMoonElevation = 44.82, highMoonTime = "05:12:00",
                    moonriseAzimuth = 91.06, moonsetAzimuth = 264.52
                ),
                qth = "IM58KS", timezone = "UTC+1"
            )
        )
    }
}
