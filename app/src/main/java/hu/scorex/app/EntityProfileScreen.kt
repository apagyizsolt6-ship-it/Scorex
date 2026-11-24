package hu.scorex.app

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun EntityProfileScreen(
    initialKind: String,
    initialId: String,
    initialTitle: String,
    preferences: SharedPreferences,
    onBack: () -> Unit
) {
    val apiKey = remember(preferences) {
        preferences.getString("statpal_api_key", "").orEmpty()
    }

    var currentKind by remember(initialKind, initialId) { mutableStateOf(initialKind) }
    var currentId by remember(initialKind, initialId) { mutableStateOf(initialId) }
    var profile by remember(currentKind, currentId) { mutableStateOf<EntityProfileData?>(null) }
    var isLoading by remember(currentKind, currentId) { mutableStateOf(false) }
    var errorText by remember(currentKind, currentId) { mutableStateOf("") }

    LaunchedEffect(currentKind, currentId, apiKey) {
        if (apiKey.isBlank() || currentId.isBlank()) return@LaunchedEffect
        isLoading = true
        errorText = ""
        try {
            profile = withContext(Dispatchers.IO) {
                StatPalApi.fetchEntityProfile(apiKey, currentKind, currentId)
            }
        } catch (e: Exception) {
            errorText = e.message ?: "Nem sikerült betölteni a profilt."
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack, shape = RoundedCornerShape(10.dp)) {
                Text("← VISSZA", color = AccentColor, fontSize = 12.sp)
            }
            Text(
                text = profile?.kindLabel ?: "PROFIL",
                color = CyanAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
        }

        GlassPanel(
            borderColor = CyanAccent.copy(alpha = 0.40f),
            gradientColors = listOf(GlassTop, GlassSurface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = profile?.name ?: initialTitle,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                val sub = profile?.subtitle.orEmpty()
                if (sub.isNotBlank()) {
                    Text(text = sub, color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                if (isLoading) {
                    Text("Adatlap betöltése...", color = CyanAccent, fontSize = 12.sp)
                }
                if (errorText.isNotBlank()) {
                    Text(text = errorText, color = ErrorColor, fontSize = 12.sp)
                }
            }
        }

        profile?.let { data ->
            if (data.details.isNotEmpty()) {
                GlassPanel(
                    borderColor = AccentColor.copy(alpha = 0.28f),
                    gradientColors = listOf(GlassSurface, GlassWell)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("RÉSZLETES ADATOK", color = AccentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        data.details.forEach { (k, v) ->
                            DiagnosticRow(label = k, value = v)
                        }
                    }
                }
            }

            if (data.relatedItems.isNotEmpty()) {
                GlassPanel(
                    borderColor = GoldAccent.copy(alpha = 0.30f),
                    gradientColors = listOf(GlassSurface, GlassWell)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("KERET / KAPCSOLÓDÓ PROFILOK", color = GoldAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        data.relatedItems.forEach { (relKind, relId, relLabel) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(GlassSurface)
                                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(9.dp))
                                    .clickable {
                                        currentKind = relKind
                                        currentId = relId
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = relLabel, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(text = "Adatlap ›", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
