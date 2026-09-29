package com.areenax.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.areenax.app.core.theme.Type
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/*
 * DateTimePickerModal — port of src/components/shared/DateTimePickerModal.tsx
 * (SPEC/03 §14, used by HostCreationScreen).
 * Web modal: "Select Date & Time" header + month nav + calendar grid + time
 * section (HH:MM inputs 12h + AM/PM toggle) + Cancel / "Set Date & Time".
 * Native: Material3 DatePicker + TimePicker stacked in one dialog flow —
 * same entry point (readonly field), same footer labels, same ISO output.
 *
 * onChange emits a LOCAL datetime ISO ("2025-10-25T11:06:00") exactly like the
 * web's datetime-local value (the server parses it in server-local time).
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerModal(
    open: Boolean,
    value: String,
    onClose: () -> Unit,
    onChange: (iso: String) -> Unit,
) {
    if (!open) return

    val initial = remember(value) { value.takeIf { it.isNotBlank() }?.parseInitial() }
    var stage by remember { mutableStateOf(DateStage.DATE) }

    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.first?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
    )
    val timeState = rememberTimePickerState(
        initialHour = initial?.second?.hour ?: 12,
        initialMinute = initial?.second?.minute ?: 0,
        is24Hour = false,
    )

    when (stage) {
        DateStage.DATE -> DatePickerDialog(
            onDismissRequest = onClose,
            confirmButton = {
                TextButton(onClick = { stage = DateStage.TIME }) { Text("Next: Time") }
            },
            dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
        ) {
            DatePicker(
                state = dateState,
                title = {
                    Text(
                        "Select Date & Time",
                        style = Type.headlineMd,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                    )
                },
            )
        }

        DateStage.TIME -> DatePickerDialog(
            onDismissRequest = onClose,
            confirmButton = {
                Button(
                    onClick = {
                        val millis = dateState.selectedDateMillis
                        if (millis != null) {
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                            val time = LocalTime.of(timeState.hour, timeState.minute)
                            val iso = LocalDateTime.of(date, time).toString()
                            onChange(iso)
                        }
                        onClose()
                    },
                    shape = RoundedCornerShape(percent = 50),
                ) { Text("Set Date & Time") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { stage = DateStage.DATE }) { Text("Back") }
                    TextButton(onClick = onClose) { Text("Cancel") }
                }
            },
        ) {
            Surface(shape = RoundedCornerShape(24.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Time", style = Type.labelLg, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TimePicker(state = timeState)
                }
            }
        }
    }
}

private enum class DateStage { DATE, TIME }

private fun String.parseInitial(): Pair<LocalDate, LocalTime>? = try {
    val dt = if (contains('T')) {
        LocalDateTime.parse(this.take(19))
    } else {
        LocalDate.parse(this.take(10)).atTime(12, 0)
    }
    dt.toLocalDate() to dt.toLocalTime()
} catch (_: Exception) {
    null
}
