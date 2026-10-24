package com.lumenchord.pianoweave.ui.screens.learn

import com.lumenchord.pianoweave.billing.MIN_BALANCE_TO_TRANSCRIBE
import com.lumenchord.pianoweave.billing.owedMinutes
import com.lumenchord.pianoweave.ui.components.DialogHeader
import com.lumenchord.pianoweave.ui.components.DialogSurface
import androidx.compose.foundation.BorderStroke
import com.lumenchord.pianoweave.ui.theme.PillShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme

@Composable
internal fun NotEnoughCreditsDialog(
    /** Null when the cost isn't known yet (the server measures an upload after receiving it). */
    requiredCredits: Int?,
    availableCredits: Int,
    onBuyCredits: () -> Unit,
    onDismiss: () -> Unit
) {
    val appTheme = LocalAppTheme.current
    val ColorSurface = MaterialTheme.colorScheme.surface
    val ColorGold = appTheme.primaryColor
    val ColorSlate = MaterialTheme.colorScheme.tertiaryContainer

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            DialogSurface(
                light = ColorGold,
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = ColorSurface,
                contentColor = Color.White,
                border = BorderStroke(1.dp, ColorSlate)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DialogHeader(
                        title = "More Credits Needed",
                        icon = Icons.Default.MonetizationOn,
                        onClose = onDismiss,
                        titleColor = ColorGold
                    )

                    Text(
                        text = notEnoughCreditsText(requiredCredits, availableCredits),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 22.sp
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = PillShape,
                            border = BorderStroke(1.dp, ColorSlate),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = "Cancel",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = {
                                onDismiss()
                                onBuyCredits()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ColorGold,
                                contentColor = if (appTheme.isLightAccent) Color.Black else Color.White
                            )
                        ) {
                            Text(
                                text = "Buy credits",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/** What the transcription needs against what the user has, which can be minutes owed after a refund. */
internal fun notEnoughCreditsText(required: Int?, available: Int): String {
    val needs = if (required != null) "This transcription requires $required ${credits(required)} (1 credit = 1 minute)"
    else "A transcription needs at least $MIN_BALANCE_TO_TRANSCRIBE credit (1 credit = 1 minute)"
    val owed = owedMinutes(available)
    val has = if (owed > 0) {
        "but your balance is $owed ${credits(owed)} below zero: a refunded purchase had already been used. " +
            "New credits cover that first."
    } else {
        "but you only have $available ${credits(available)} remaining."
    }
    return "$needs, $has\n\nWould you like to buy more credits in the shop?"
}

private fun credits(n: Int) = if (n == 1) "credit" else "credits"
