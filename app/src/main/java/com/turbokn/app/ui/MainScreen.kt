package com.turbokn.app.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.turbokn.app.core.DeviceMetrics
import com.turbokn.app.core.GameActions

@Composable
fun MainScreen(
    onOpenAccessibility: () -> Unit = {},
    vm: MainViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var pickerOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header()

        ShizukuCard(
            state = state,
            onRequest = vm::requestShizukuPermission,
            onRefresh = vm::refreshShizuku,
        )

        GameSelectorCard(
            recents = state.recents,
            selected = state.selected,
            onPick = vm::openGame,
            onBrowse = { pickerOpen = true },
        )

        ActivateButton(state = state, vm = vm)

        QuickActions(vm = vm)

        MetricsCard(metrics = state.metrics, active = state.active)

        SectionCard(title = "DESEMPENHO") {
            SettingLabel(
                title = "Escala de renderização",
                hint = "Abaixo de 1.0 reduz a resolução interna. É o que mais aumenta FPS.",
            )
            ChipRow(
                values = GameActions.DOWNSCALE_STEPS,
                selected = state.downscale,
                labelFor = { if (it == "disable") "OFF" else it },
                onSelect = vm::setDownscale,
            )

            Spacer(Modifier.height(16.dp))

            SettingLabel(
                title = "Limite de FPS",
                hint = "Estabiliza a taxa de quadros do jogo selecionado.",
            )
            ChipRow(
                values = GameActions.FPS_STEPS,
                selected = state.fps,
                labelFor = { if (it == 0) "OFF" else it.toString() },
                onSelect = vm::setFps,
            )
        }

        SectionCard(title = "INTERRUPÇÕES") {
            ToggleRow(
                title = "Não perturbe durante o jogo",
                hint = "Silencia notificações e chamadas enquanto o perfil está ativo.",
                checked = state.dnd,
                onChange = vm::setDnd,
            )
        }

        SectionCard(title = "TÉRMICA") {
            SettingLabel(
                title = "Override de throttling",
                hint = "Segura os clocks por mais tempo, mas o aparelho esquenta mais. " +
                    "OFF usa o comportamento padrão do sistema.",
            )
            ChipRow(
                values = GameActions.THERMAL_STEPS,
                selected = state.thermalStatus,
                labelFor = { if (it == 0) "OFF" else it.toString() },
                onSelect = vm::setThermalStatus,
            )
        }

        SectionCard(title = "AUTOMÁTICO") {
            ToggleRow(
                title = "Ativar ao entrar em jogos",
                hint = if (state.accessibilityEnabled) {
                    "Detecção ativa em segundo plano."
                } else {
                    "Precisa do serviço de acessibilidade habilitado."
                },
                checked = state.autoMode,
                onChange = { enabled ->
                    vm.setAutoMode(enabled)
                    if (enabled) {
                        requestNotificationPermission()
                        if (!state.accessibilityEnabled) onOpenAccessibility()
                    }
                },
            )
        }

        ActivityList(state.log)
    }

    if (pickerOpen) {
        AppPickerDialog(
            apps = state.apps.filter {
                query.isBlank() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            },
            query = query,
            onQueryChange = { query = it },
            onDismiss = { pickerOpen = false },
            onPick = {
                vm.select(it)
                pickerOpen = false
            },
        )
    }
}

@Composable
private fun Header() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "TURBOKN",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "刀   ·   snow   ·   雪",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun ActivateButton(state: UiState, vm: MainViewModel) {
    val active = state.active
    Button(
        onClick = { if (active) vm.deactivate() else vm.activate() },
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            contentColor = if (active) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
        ),
    ) {
        Text(
            text = if (active) "DESATIVAR" else "ATIVAR",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun QuickActions(vm: MainViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = vm::freeMemory,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text("LIBERAR MEMÓRIA", style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(
            onClick = vm::restoreDefaults,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text("RESTAURAR PADRÕES", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MetricsCard(metrics: DeviceMetrics, active: Boolean) {
    SectionCard(title = "MEDIÇÃO") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(
                label = "TEMPERATURA",
                value = metrics.temperatureCelsius?.let { "%.1f°C".format(it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "CPU",
                value = metrics.cpuClockMhz?.let { "$it MHz" } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(
                label = "FLUIDEZ",
                value = metrics.fps?.let { "$it%" } ?: "—",
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "THROTTLING",
                value = metrics.thermalStatus?.toString() ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
        if (!active) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Ative o perfil para acompanhar os valores em tempo real.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
