package hu.scorex.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ApiDataScreen(
    onBack: () -> Unit
) {

    val rawResponse = StatPalApi.lastRawResponse
    val extraPath = StatPalApi.lastExtraEndpointPath
    val extraResponse = StatPalApi.lastExtraEndpointResponse

    val leagueCount = StatPalApi.lastLeagueCount
    val apiMatchCount = StatPalApi.lastApiMatchCount
    val parsedMatchCount = StatPalApi.lastParsedMatchCount
    val skippedMatchCount = StatPalApi.lastSkippedMatchCount

    val duplicateMatchCount = StatPalApi.lastDuplicateMatchCount
    val missingTeamCount = StatPalApi.lastMissingTeamCount
    val invalidMatchCount = StatPalApi.lastInvalidMatchCount
    val skippedDetails = StatPalApi.lastSkippedMatchDetails
    val dailyRequests = StatPalApi.lastDailyRequestCount

    var showFullJson by remember { mutableStateOf(false) }
    val previewLimit = 8000
    val isTruncated = !showFullJson && rawResponse.length > previewLimit
    val displayedResponse = if (isTruncated) {
        rawResponse.take(previewLimit) + "\n\n... (előnézet: ${rawResponse.length} karakterből az első $previewLimit látszik)"
    } else {
        rawResponse
    }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        OutlinedButton(onClick = onBack, shape = RoundedCornerShape(10.dp)) {
            Text(text = "← VISSZA", color = AccentColor)
        }

        Text(
            text = "STATPAL API ADATOK",
            color = AccentColor,
            fontSize = 19.sp,
            fontWeight = FontWeight.Black
        )

        Text(
            text = "Itt ellenőrizhetjük, hogy az API hány mérkőzést küldött, és a Scorex mely adatokat dolgozta fel vagy hagyta ki.",
            color = SecondaryColor,
            fontSize = 12.sp
        )

        GlassPanel(
            borderColor = AccentColor.copy(alpha = 0.28f),
            gradientColors = listOf(GlassTop, GlassSurface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "FELDOLGOZÁSI ELLENŐRZÉS",
                    color = AccentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                DiagnosticRow(label = "Napi API-kérésszám", value = dailyRequests, valueColor = CyanAccent)
                DiagnosticRow(label = "Bajnokságok", value = leagueCount.toString())
                DiagnosticRow(label = "API által küldött meccsek", value = apiMatchCount.toString())
                DiagnosticRow(label = "Feldolgozott meccsek", value = parsedMatchCount.toString(), valueColor = AccentColor)
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 6.dp))
                DiagnosticRow(label = "Összes kihagyott", value = skippedMatchCount.toString(), valueColor = WarningColor)
                DiagnosticRow(label = "Duplikált mérkőzések", value = duplicateMatchCount.toString(), valueColor = WarningColor)
                DiagnosticRow(label = "Hiányzó csapatnév", value = missingTeamCount.toString(), valueColor = WarningColor)
                DiagnosticRow(label = "Hibás mérkőzésadat", value = invalidMatchCount.toString(), valueColor = ErrorColor)
            }
        }

        if (extraResponse.isNotBlank()) {
            GlassPanel(
                borderColor = CyanAccent.copy(alpha = 0.30f),
                gradientColors = listOf(GlassSurface, GlassWell)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EXTRA V2 VÉGPONT: $extraPath",
                            color = CyanAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Scorex v2 válasz", extraResponse))
                                Toast.makeText(context, "v2 válasz másolva!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(9.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "⧉ MÁSOLÁS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = extraResponse.take(4000),
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        GlassPanel(
            borderColor = WarningColor.copy(alpha = 0.30f),
            gradientColors = listOf(GlassSurface, GlassWell)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "KIHAGYOTT MÉRKŐZÉSEK",
                        color = WarningColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (skippedDetails.isNotBlank() && skippedMatchCount > 0) {
                        FilledTonalButton(
                            onClick = {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Scorex kihagyott meccsek", skippedDetails)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Kihagyások vágólapra másolva!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(9.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "⧉ MÁSOLÁS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (skippedDetails.isBlank()) {
                        "Nincs részletes kihagyási adat. A mérkőzések feldolgozása sikeres lehetett."
                    } else {
                        skippedDetails
                    },
                    color = if (skippedDetails.isBlank()) SecondaryColor else Color.White,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NYERS API-VÁLASZ",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (rawResponse.length > previewLimit) {
                    FilledTonalButton(
                        onClick = { showFullJson = !showFullJson },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (showFullJson) "RÖVIDÍTETT" else "TELJES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                FilledTonalButton(
                    onClick = {
                        if (rawResponse.isNotBlank()) {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Scorex StatPal API válasz", rawResponse)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "API-adatok vágólapra másolva!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Nincs másolható API-adat.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = rawResponse.isNotBlank(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "⧉  MÁSOLÁS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        GlassPanel(
            borderColor = CyanAccent.copy(alpha = 0.24f),
            gradientColors = listOf(GlassSurface, GlassWell)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (rawResponse.isBlank()) "Még nincs eltárolt API-válasz." else displayedResponse,
                    color = Color.White,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentColor,
                contentColor = BackgroundColor
            )
        ) {
            Text("VISSZA A SCOREXHEZ")
        }
    }
}

@Composable
internal fun DiagnosticRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = SecondaryColor,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
