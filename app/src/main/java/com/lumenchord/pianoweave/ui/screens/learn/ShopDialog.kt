package com.lumenchord.pianoweave.ui.screens.learn

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.android.billingclient.api.ProductDetails
import com.lumenchord.pianoweave.billing.GooglePlayBillingManager
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import kotlinx.coroutines.launch

@Composable
internal fun ShopDialog(
    viewModel: PianoWeaveViewModel,
    context: Context,
    onDismiss: () -> Unit
) {
    val appTheme = LocalAppTheme.current
    val coroutineScope = rememberCoroutineScope()
    val ColorSurface = MaterialTheme.colorScheme.surface
    val ColorGold = appTheme.primaryColor
    val ColorSlate = MaterialTheme.colorScheme.tertiaryContainer
    val ColorTextDim = MaterialTheme.colorScheme.tertiary

    val activity = context as? Activity
    var productDetailsMap by remember { mutableStateOf<Map<String, ProductDetails>>(emptyMap()) }
    var isLoadingProducts by remember { mutableStateOf(true) }

    val billingManager = remember {
        GooglePlayBillingManager(context) { token, productId ->
            coroutineScope.launch {
                val success = viewModel.verifyGooglePlayPurchase(context, productId, token)
                if (success) {
                    viewModel.refreshUserBalance(context)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        billingManager.startConnection {
            val productIds = viewModel.serverOffers.map { it.productId }
            if (productIds.isNotEmpty()) {
                billingManager.queryProductDetails(productIds) { detailsList ->
                    productDetailsMap = detailsList.associateBy { it.productId }
                    isLoadingProducts = false
                }
            } else {
                isLoadingProducts = false
            }
        }
        onDispose {
            billingManager.destroy()
        }
    }

    fun formatRenewalTime(seconds: Long?): String {
        if (seconds == null) return "Free credits active"
        if (seconds <= 0) return "Free credits ready"
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            days > 0 -> "$days day${if (days > 1) "s" else ""}, $hours hr${if (hours > 1) "s" else ""}"
            hours > 0 -> "$hours hr${if (hours > 1) "s" else ""}, $mins min${if (mins > 1) "s" else ""}"
            mins > 0 -> "$mins min${if (mins > 1) "s" else ""}, $secs sec"
            else -> "$secs second${if (secs != 1L) "s" else ""}"
        }
    }

    fun formatRetention(seconds: Long?): String {
        val s = seconds ?: 86400L
        val hours = s / 3600
        val mins = s / 60
        return when {
            hours >= 1 -> "$hours hour${if (hours > 1) "s" else ""}"
            mins >= 1 -> "$mins minute${if (mins > 1) "s" else ""}"
            else -> "$s seconds"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .heightIn(max = 620.dp),
                shape = RoundedCornerShape(20.dp),
                color = ColorSurface,
                contentColor = Color.White,
                border = BorderStroke(1.dp, ColorSlate)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = ColorGold,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Credits Shop",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ColorGold
                                )
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = ColorTextDim
                                )
                            }
                        }

                        HorizontalDivider(color = ColorSlate.copy(alpha = 0.6f))

                        // Balance Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = ColorSlate.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, ColorGold)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "AVAILABLE BALANCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ColorTextDim,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MonetizationOn,
                                        contentDescription = null,
                                        tint = ColorGold,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = "${viewModel.userCredits} Credits",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = "1 Credit = 1 Minute of Piano Transcription",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ColorGold,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Free minutes grant renewal info
                        val grantSec = viewModel.freeMinutesSecondsUntilNextGrant
                        if (grantSec != null) {
                            val infoText = if (grantSec <= 0) {
                                "Free credits grant is ready"
                            } else {
                                "Next free credits grant in: ${formatRenewalTime(grantSec)}"
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                border = BorderStroke(1.dp, ColorSlate)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = ColorGold,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Free Credits Renewal",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = infoText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ColorTextDim
                                        )
                                    }
                                }
                            }
                        }

                        // Offers / Credit packages list
                        Text(
                            text = "BUY MORE CREDITS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ColorTextDim
                        )

                        if (isLoadingProducts) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = ColorGold, modifier = Modifier.size(28.dp))
                            }
                        } else if (viewModel.serverOffers.isEmpty()) {
                            Text(
                                text = "No credit packages available at this time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ColorTextDim
                            )
                        } else {
                            viewModel.serverOffers.forEach { offer ->
                                val details = productDetailsMap[offer.productId]
                                val priceText = details?.oneTimePurchaseOfferDetails?.formattedPrice ?: "Buy"

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = ColorSlate.copy(alpha = 0.3f),
                                    border = BorderStroke(1.dp, ColorSlate)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${offer.transcriptionMinutes} Credits",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "${offer.transcriptionMinutes} minutes of AI transcription",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = ColorTextDim
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                if (activity != null && details != null) {
                                                    billingManager.launchPurchaseFlow(activity, details)
                                                }
                                            },
                                            enabled = details != null && activity != null,
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = ColorGold,
                                                contentColor = if (appTheme.isLightAccent) Color.Black else Color.White
                                            )
                                        ) {
                                            Text(priceText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Warning about file retention
//                        Surface(
//                            modifier = Modifier.fillMaxWidth(),
//                            shape = RoundedCornerShape(12.dp),
//                            color = Color.Black.copy(alpha = 0.3f),
//                            border = BorderStroke(1.dp, ColorSlate)
//                        ) {
//                            Row(
//                                modifier = Modifier.padding(12.dp),
//                                verticalAlignment = Alignment.Top,
//                                horizontalArrangement = Arrangement.spacedBy(8.dp)
//                            ) {
//                                Icon(
//                                    imageVector = Icons.Default.Info,
//                                    contentDescription = null,
//                                    tint = ColorTextDim,
//                                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
//                                )
//                                Text(
//                                    text = "Completed transcriptions are kept on the server for ${formatRetention(viewModel.cleanupIntervalSeconds)}. If you close the app and do not reconnect to the internet within ${formatRetention(viewModel.cleanupIntervalSeconds)}, the transcription will be removed and spent credits lost.",
//                                    style = MaterialTheme.typography.bodySmall,
//                                    color = ColorTextDim,
//                                    lineHeight = 16.sp
//                                )
//                            }
//                        }
                    }
                }
            }
        }
    }
}
