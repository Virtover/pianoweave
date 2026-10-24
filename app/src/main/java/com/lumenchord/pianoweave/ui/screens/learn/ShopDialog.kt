package com.lumenchord.pianoweave.ui.screens.learn

import com.lumenchord.pianoweave.billing.creditsLabel
import com.lumenchord.pianoweave.billing.owedMinutes
import com.lumenchord.pianoweave.ui.components.DialogHeader
import com.lumenchord.pianoweave.ui.theme.Moods
import com.lumenchord.pianoweave.ui.components.DialogSurface
import android.content.Context
import com.lumenchord.pianoweave.ui.theme.PillShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.android.billingclient.api.ProductDetails
import com.lumenchord.pianoweave.billing.GooglePlayBillingManager
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import com.lumenchord.pianoweave.util.findActivity
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

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

    val currentContext = LocalContext.current
    val activity = currentContext.findActivity() ?: context.findActivity()
    var productDetailsMap by remember { mutableStateOf<Map<String, ProductDetails>>(emptyMap()) }
    var isLoadingProducts by remember { mutableStateOf(true) }
    var isBillingConnected by remember { mutableStateOf(false) }
    var purchaseErrorMessage by remember { mutableStateOf<String?>(null) }
    // From Google confirming a purchase until the server has credited it
    var verifyingProductId by remember { mutableStateOf<String?>(null) }

    val billingManager = remember {
        GooglePlayBillingManager(
            context = context,
            onPurchaseCompleted = { token, productId ->
                coroutineScope.launch {
                    verifyingProductId = productId
                    val success = viewModel.verifyGooglePlayPurchase(context, productId, token)
                    verifyingProductId = null
                    if (success) {
                        viewModel.refreshUserBalance(context)
                    } else {
                        purchaseErrorMessage = "Your purchase is saved but couldn't be credited yet. It will be credited automatically as soon as the server confirms it."
                    }
                }
            },
            onPurchaseError = { errorMsg ->
                verifyingProductId = null
                purchaseErrorMessage = errorMsg
            }
        )
    }

    DisposableEffect(Unit) {
        billingManager.startConnection(
            onFailed = {
                isLoadingProducts = false
                purchaseErrorMessage = "Google Play Billing isn't available. Check that you're signed in to the Play Store."
            }
        ) {
            isBillingConnected = true
            // Credits any earlier purchase that was paid for but never reached the server
            billingManager.recoverUncreditedPurchases()
        }
        onDispose { billingManager.destroy() }
    }

    LaunchedEffect(isBillingConnected, viewModel.serverOffers) {
        if (!isBillingConnected) return@LaunchedEffect
        val productIds = viewModel.serverOffers.map { it.productId }
        if (productIds.isEmpty()) {
            isLoadingProducts = false
            return@LaunchedEffect
        }
        isLoadingProducts = true
        billingManager.queryProductDetails(productIds) { detailsList ->
            productDetailsMap = detailsList.associateBy { it.productId }
            isLoadingProducts = false
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DialogHeader(
                            title = "Credits Shop",
                            icon = Icons.Default.ShoppingCart,
                            onClose = onDismiss,
                            titleColor = ColorGold
                        )

                        // Compact Combined Balance & Renewal Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = ColorSlate.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, ColorGold)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MonetizationOn,
                                        contentDescription = null,
                                        tint = ColorGold,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "AVAILABLE BALANCE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ColorTextDim,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        BalanceLine(viewModel.userCredits)
                                    }
                                }

                                val owed = owedMinutes(viewModel.userCredits)
                                if (owed > 0) {
                                    HorizontalDivider(color = ColorSlate.copy(alpha = 0.4f))
                                    OwedNote(owed)
                                }

                                val grantSec = viewModel.freeMinutesSecondsUntilNextGrant
                                val fm = viewModel.freeMinutes
                                if (grantSec != null && viewModel.userCredits <= fm && fm > 0) {
                                    HorizontalDivider(color = ColorSlate.copy(alpha = 0.4f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = ColorGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        val infoText = if (grantSec <= 0) {
                                            "Free $fm credits renewal ready"
                                        } else {
                                            "Free $fm credits renewal in: ${formatRenewalTime(grantSec)}"
                                        }
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
                        Spacer(Modifier.height(4.dp))
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
                                val priceText = details?.let(::formatPrice) ?: "Buy"
                                val isVerifying = verifyingProductId == offer.productId
                                // Locked while a purchase is being credited, so nobody buys twice by accident
                                val canBuy = details != null && activity != null && verifyingProductId == null
                                val onAccent = if (appTheme.isLightAccent) Color.Black else Color.White

                                // The whole row buys the pack, not just its price
                                Surface(
                                    onClick = {
                                        if (activity != null && details != null) {
                                            billingManager.launchPurchaseFlow(activity, details)
                                        }
                                    },
                                    enabled = canBuy,
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

                                        Spacer(Modifier.width(12.dp))

                                        Box(
                                            modifier = Modifier
                                                .heightIn(min = 32.dp)
                                                .background(if (canBuy || isVerifying) ColorGold else ColorGold.copy(alpha = 0.3f), PillShape)
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isVerifying) {
                                                CircularProgressIndicator(color = onAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                            } else {
                                                Text(priceText, color = onAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (purchaseErrorMessage != null) {
        Dialog(onDismissRequest = { purchaseErrorMessage = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Purchase Failed",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = ColorGold
                            )
                            IconButton(
                                onClick = { purchaseErrorMessage = null },
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

                        Text(
                            text = purchaseErrorMessage ?: "An unknown error occurred during purchase.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 22.sp
                        )

                        Spacer(Modifier.height(4.dp))

                        Button(
                            onClick = { purchaseErrorMessage = null },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ColorGold,
                                contentColor = if (appTheme.isLightAccent) Color.Black else Color.White
                            )
                        ) {
                            Text(
                                text = "OK",
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

@Composable
private fun BalanceLine(credits: Int) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val creditsStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
    val rateStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
    val creditsText = creditsLabel(credits)
    val creditsColor = if (owedMinutes(credits) > 0) Moods.Watch.light else Color.White
    val rateText = "1 Min = 1 Credit"
    val rateColor = MaterialTheme.colorScheme.secondary
    val gap = 12.dp

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val creditsWidth = measurer.measure(creditsText, creditsStyle, maxLines = 1).size.width
        val rateWidth = measurer.measure(rateText, rateStyle, maxLines = 1).size.width
        val fitsOnOneLine =
            creditsWidth + rateWidth + with(density) { gap.toPx() } <= constraints.maxWidth

        if (fitsOnOneLine) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = creditsText,
                    style = creditsStyle,
                    color = creditsColor,
                    maxLines = 1
                )
                Text(
                    text = rateText,
                    style = rateStyle,
                    color = rateColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = creditsText,
                    style = creditsStyle,
                    color = creditsColor,
                    maxLines = 1
                )
                Text(
                    text = rateText,
                    style = rateStyle,
                    color = rateColor,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
/**
 * The pack's price as "16.99 PLN": the amount in the phone's number format, then the currency code.
 * Built here from Google Play's amount and currency rather than its own text, which can mix decimal
 * commas and dots between packs.
 */
private fun formatPrice(details: ProductDetails): String? {
    val offer = details.oneTimePurchaseOfferDetails ?: return null
    return try {
        val currency = Currency.getInstance(offer.priceCurrencyCode)
        val digits = currency.defaultFractionDigits.coerceAtLeast(0)
        val amount = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(offer.priceAmountMicros / 1_000_000.0)
        "$amount ${currency.currencyCode}"
    } catch (_: IllegalArgumentException) {
        offer.formattedPrice
    }
}


/**
 * Minutes owed after a refund of minutes already used. Said plainly and kindly: "Let no debt remain
 * outstanding, except the continuing debt to love one another" (Romans 13:8).
 */
@Composable
private fun OwedNote(owed: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = Moods.Watch.core,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "A refunded purchase had already been used, so $owed ${if (owed == 1) "credit is" else "credits are"} owed. " +
                "New credits cover that first.",
            style = MaterialTheme.typography.bodySmall,
            color = Moods.Watch.light
        )
    }
}
