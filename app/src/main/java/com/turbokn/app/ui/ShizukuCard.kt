package com.turbokn.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Shows whether the Shizuku service is running and whether TurboKn is actually
 * connected to it, which are two different questions the user cares about.
 */
@Composable
internal fun ShizukuCard(
    state: UiState,
    onRequest: () -> Unit,
    onRefresh: () -> Unit,
) {
    val serviceOn = state.shizuku != ShizukuState.OFFLINE
    val usingShizuku = state.shizuku == ShizukuState.READY

    val (title, detail, tone) = when (state.shizuku) {
        ShizukuState.READY -> Triple(
            "Shizuku conectado",
            "O TurboKn está aplicando os comandos através do Shizuku.",
            MaterialTheme.colorScheme.primary,
        )
        ShizukuState.CONNECTING -> Triple(
            "Conectando ao Shizuku",
            "Serviço encontrado. Estabelecendo a conexão privilegiada…",
            MaterialTheme.colorScheme.tertiary,
        )
        ShizukuState.PERMISSION_REQUIRED -> Triple(
            "Permissão pendente",
            "O Shizuku está ligado, mas ainda não autorizou o TurboKn.",
            MaterialTheme.colorScheme.tertiary,
        )
        ShizukuState.OFFLINE -> Triple(
            "Shizuku desligado",
            "Abra o app Shizuku e inicie o serviço para o TurboKn funcionar.",
            MaterialTheme.colorScheme.error,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(tone),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = tone,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(
                    label = "SERVIÇO",
                    value = if (serviceOn) "LIGADO" else "DESLIGADO",
                    on = serviceOn,
                    modifier = Modifier.weight(1f),
                )
                Pill(
                    label = "USO PELO APP",
                    value = if (usingShizuku) "ATIVO" else "INATIVO",
                    on = usingShizuku,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.shizuku != ShizukuState.READY) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onRequest,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text("CONCEDER PERMISSÃO", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onRefresh) {
                        Text("VERIFICAR", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
