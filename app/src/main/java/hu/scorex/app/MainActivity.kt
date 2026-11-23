package hu.scorex.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ScorexApp()
        }
    }
}

private val BackgroundColor = Color(0xFF080D18)
private val CardColor = Color(0xFF121C2D)
private val AccentColor = Color(0xFF35E0A1)
private val SecondaryColor = Color(0xFF8B9BB4)

@Composable
fun ScorexApp() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundColor)
                    .padding(16.dp)
            ) {
                ScorexHeader()

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "ÉLŐ MÉRKŐZÉSEK",
                    color = AccentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                MatchCard(
                    league = "Premier League",
                    homeTeam = "Manchester City",
                    awayTeam = "Liverpool",
                    homeScore = "–",
                    awayScore = "–",
                    matchTime = "Hamarosan"
                )

                Spacer(modifier = Modifier.height(12.dp))

                MatchCard(
                    league = "La Liga",
                    homeTeam = "Real Madrid",
                    awayTeam = "Barcelona",
                    homeScore = "–",
                    awayScore = "–",
                    matchTime = "Hamarosan"
                )

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = CardColor
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Text(
                            text = "SCOREX TIPPEK",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Hamarosan elérhető a saját tippfogadási szelvényed.",
                            color = SecondaryColor,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "SCOREX  •  FOOTBALL CENTER",
                    modifier = Modifier.fillMaxWidth(),
                    color = SecondaryColor,
                    fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ScorexHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SCORE",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    text = "X",
                    color = AccentColor,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Text(
                text = "ÉLD ÁT A FUTBALLT",
                color = SecondaryColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = AccentColor
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "⚽",
                modifier = Modifier.padding(12.dp),
                fontSize = 24.sp
            )
        }
    }
}

@Composable
fun MatchCard(
    league: String,
    homeTeam: String,
    awayTeam: String,
    homeScore: String,
    awayScore: String,
    matchTime: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = CardColor
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = league,
                    color = SecondaryColor,
                    fontSize = 12.sp
                )

                Text(
                    text = matchTime,
                    color = AccentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = homeTeam,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = awayTeam,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = homeScore,
                        color = AccentColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = awayScore,
                        color = AccentColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
