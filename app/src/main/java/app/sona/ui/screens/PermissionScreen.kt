package app.sona.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sona.ui.theme.Accent
import app.sona.ui.theme.BgBase
import app.sona.ui.theme.OnAccent
import app.sona.ui.theme.TextMuted
import app.sona.ui.theme.TextPrimary
import app.sona.ui.theme.TextSecondary

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(BgBase).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SONA", color = TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            Spacer(Modifier.size(6.dp))
            Box(Modifier.size(9.dp).clip(CircleShape).background(Accent))
        }
        Spacer(Modifier.height(10.dp))
        Text("LOCAL · OFFLINE · YOURS", color = TextMuted, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(28.dp))
        Text(
            "SONA plays the music already on your phone.",
            color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No streaming. No accounts. No ads. No tracking.",
            color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        Text(
            "Allow access",
            color = OnAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(Accent)
                .clickable { onGrant() }
                .padding(horizontal = 40.dp, vertical = 14.dp),
        )
    }
}
