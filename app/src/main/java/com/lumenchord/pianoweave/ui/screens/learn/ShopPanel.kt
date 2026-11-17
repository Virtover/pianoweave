package com.lumenchord.pianoweave.ui.screens.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.billing.PlanAction
import com.lumenchord.pianoweave.billing.SubscriptionState
import com.lumenchord.pianoweave.billing.creditsLabel
import com.lumenchord.pianoweave.billing.owedMinutes
import com.lumenchord.pianoweave.ui.components.DialogHeader
import com.lumenchord.pianoweave.ui.components.DialogSurface
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.Moods
import com.lumenchord.pianoweave.ui.theme.PillShape
import kotlinx.coroutines.launch

/** Test tags of the shop's parts. */
object ShopTags {
    const val PLANS = "shop_plans"
    const val PACKS = "shop_packs"
    const val EMPTY = "shop_empty"
    const val LOADING = "shop_loading"
    const val UNAVAILABLE = "shop_unavailable"
    const val MANAGE = "shop_manage"
}

/** Plans sit side by side from this width; narrower, they stack. */
private val SIDE_BY_SIDE_MIN_WIDTH = 520.dp

/**
 * The shop: the balance, then plans, then credit packs, then a quiet footer. Calm on purpose: no countdowns, no
 * badges shouting a deal, nothing that opens on its own. Sections without anything to offer are left out, and
 * with nothing at all there is one tidy note instead of empty headers.
 */
@Composable
fun ShopPanel(
    state: ShopUiState,
    onPlan: (ShopPlan) -> Unit,
    onPack: (ShopPack) -> Unit,
    onManage: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gold = LocalAppTheme.current.primaryColor
    val slate = MaterialTheme.colorScheme.tertiaryContainer
    DialogSurface(
        light = gold,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, slate)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DialogHeader(title = "Credits Shop", icon = Icons.Default.ShoppingCart, onClose = onClose, titleColor = gold)

            BalanceCard(state.balance)

            when {
                state.unavailableMessage != null -> QuietNote(
                    icon = Icons.Rounded.CloudOff,
                    title = "Purchases aren't available",
                    text = state.unavailableMessage,
                    modifier = Modifier.testTag(ShopTags.UNAVAILABLE)
                )
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp).testTag(ShopTags.LOADING),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = gold, modifier = Modifier.size(28.dp))
                }
                state.isEmpty -> QuietNote(
                    icon = Icons.Rounded.Inventory2,
                    title = "Nothing to buy right now",
                    text = "Plans and credit packs aren't on offer at the moment. Your credits are still yours to use.",
                    modifier = Modifier.testTag(ShopTags.EMPTY)
                )
                else -> {
                    if (state.plans.isNotEmpty()) PlansSection(state, onPlan)
                    if (state.packs.isNotEmpty()) PacksSection(state, onPack)
                }
            }

            ShopFooter(state, onManage)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(top = 6.dp).semantics { heading() }
    )
}

@Composable
private fun SectionNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.tertiary,
        lineHeight = 18.sp
    )
}

// --- Balance ---

@Composable
private fun BalanceCard(balance: ShopBalance) {
    val gold = LocalAppTheme.current.primaryColor
    val slate = MaterialTheme.colorScheme.tertiaryContainer
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = slate.copy(alpha = 0.3f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, gold)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = if (balance.unlimited || balance.developer) Icons.Rounded.AllInclusive else Icons.Default.MonetizationOn,
                    contentDescription = null,
                    tint = gold,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "YOUR BALANCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    val unlimited = balance.unlimited || balance.developer
                    BalanceLine(
                        text = if (unlimited) "Unlimited" else creditsLabel(balance.total),
                        owed = !unlimited && owedMinutes(balance.total) > 0,
                        // Credits aren't spent while unlimited, so their rate says nothing
                        showRate = !unlimited
                    )
                    BalanceDetails(balance)
                }
            }

            val owed = owedMinutes(balance.total)
            if (owed > 0) {
                HorizontalDivider(color = slate.copy(alpha = 0.4f))
                OwedNote(owed)
            }
            balance.legacyRenewal?.let { text ->
                HorizontalDivider(color = slate.copy(alpha = 0.4f))
                SmallInfoRow(Icons.Default.Info, text, tint = gold)
            }
        }
    }
}

/** The line under the total: where the credits come from, explained on a tap. */
@Composable
private fun BalanceDetails(balance: ShopBalance) {
    val paid = balance.paidCredits
    when {
        balance.developer -> SmallText("Developer account: your transcriptions are free.")
        balance.unlimited -> {
            val left = balance.unlimitedMinutesLeft
            val cap = balance.unlimitedDailyMinutes
            val today = when {
                left != null && cap != null -> "$left of $cap minutes left today"
                left != null -> "$left minutes left today"
                else -> null
            }
            val saved = paid?.takeIf { it > 0 }?.let { "$it purchased ${if (it == 1) "credit" else "credits"} kept for later" }
            val line = listOfNotNull(today, saved).joinToString(" · ")
            if (line.isNotEmpty()) {
                ExplainedLine(
                    text = line,
                    title = balance.planName ?: "Unlimited",
                    explanation = "Transcriptions don't use credits while your plan runs" +
                        (cap?.let { ", up to $it minutes a day (fair use). " } ?: ". ") +
                        "Purchased credits never expire and wait for when you need them."
                )
            }
        }
        balance.periodCredits != null && paid != null && balance.planName != null -> ExplainedLine(
            text = "${balance.periodCredits} from ${balance.planName} · $paid purchased",
            title = "Two kinds of credits",
            explanation = "${balance.planName}: ${balance.planMinutes?.let { "$it credits" } ?: "credits"} each period, " +
                "used first, in place of free credits. Unused ones don't carry over.\n\n" +
                "Purchased: yours to keep, they never expire.",
            warn = paid < 0
        )
        balance.periodCredits != null && paid != null && balance.freeMinutes > 0 -> ExplainedLine(
            text = "${balance.periodCredits} free · $paid purchased",
            title = "Two kinds of credits",
            explanation = "Free: ${balance.freeMinutes} credits ${balance.freeEvery ?: "renewed regularly"}, used first. " +
                "Unused ones don't carry over. ${balance.renewal}\n\nPurchased: yours to keep, they never expire.",
            warn = paid < 0
        )
    }
}

@Composable
private fun SmallText(text: String, color: Color = MaterialTheme.colorScheme.tertiary) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 2.dp))
}

/**
 * A quiet line under the total; tapping it explains it in a tooltip, so the shop stays as simple as a single
 * balance until asked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExplainedLine(text: String, title: String, explanation: String, warn: Boolean = false) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val dim = MaterialTheme.colorScheme.tertiary

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = {
            RichTooltip(title = { Text(title) }) {
                Text(text = explanation, lineHeight = 18.sp)
            }
        },
        state = tooltipState
    ) {
        Row(
            modifier = Modifier
                .padding(top = 2.dp)
                // Not a pill: the line can wrap to two, and a pill would clip its corners
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClickLabel = "Explain credits", role = Role.Button) { scope.launch { tooltipState.show() } }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (warn) Moods.Watch.light else dim,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(imageVector = Icons.Outlined.Info, contentDescription = null, tint = dim, modifier = Modifier.size(14.dp))
        }
    }
}

/** The total, with the rate beside it when both fit on one line and under it when they don't. */
@Composable
private fun BalanceLine(text: String, owed: Boolean, showRate: Boolean = true) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val creditsStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
    val rateStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
    val creditsColor = if (owed) Moods.Watch.light else MaterialTheme.colorScheme.onSurface
    val rateText = "1 Min = 1 Credit"
    val rateColor = MaterialTheme.colorScheme.secondary
    val gap = 12.dp

    if (!showRate) {
        Text(text = text, style = creditsStyle, color = creditsColor)
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val creditsWidth = measurer.measure(text, creditsStyle, maxLines = 1).size.width
        val rateWidth = measurer.measure(rateText, rateStyle, maxLines = 1).size.width
        val fitsOnOneLine = creditsWidth + rateWidth + with(density) { gap.toPx() } <= constraints.maxWidth
        if (fitsOnOneLine) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = text, style = creditsStyle, color = creditsColor, maxLines = 1)
                Text(text = rateText, style = rateStyle, color = rateColor, maxLines = 1, softWrap = false)
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = text, style = creditsStyle, color = creditsColor)
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
 * Minutes owed after a refund of minutes already used. Said plainly and kindly: "Let no debt remain
 * outstanding, except the continuing debt to love one another" (Romans 13:8).
 */
@Composable
private fun OwedNote(owed: Int) {
    SmallInfoRow(
        icon = Icons.Default.Info,
        text = "A refunded purchase had already been used, so $owed ${if (owed == 1) "credit is" else "credits are"} owed. " +
            "New credits cover that first.",
        tint = Moods.Watch.core,
        textColor = Moods.Watch.light
    )
}

@Composable
private fun SmallInfoRow(icon: ImageVector, text: String, tint: Color, textColor: Color = MaterialTheme.colorScheme.tertiary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = textColor)
    }
}

// --- Plans ---

@Composable
private fun PlansSection(state: ShopUiState, onPlan: (ShopPlan) -> Unit) {
    Column(modifier = Modifier.testTag(ShopTags.PLANS), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("PLANS")
        SectionNote(
            if (state.freeMinutes > 0) {
                "A plan's credits replace the ${state.freeMinutes} free credits each period. Unused plan credits don't carry over."
            } else {
                "A plan gives you credits each period. Unused plan credits don't carry over."
            }
        )
        val locked = state.busyProductId != null
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= SIDE_BY_SIDE_MIN_WIDTH && state.plans.size in 2..3) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    state.plans.forEach { plan ->
                        PlanCard(
                            plan = plan,
                            busy = state.busyProductId == plan.productId,
                            locked = locked,
                            onClick = { onPlan(plan) },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.plans.forEach { plan ->
                        PlanCard(
                            plan = plan,
                            busy = state.busyProductId == plan.productId,
                            locked = locked,
                            onClick = { onPlan(plan) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        state.switchNote?.let { SectionNote(it) }
    }
}

/** What a plan's button says and does; null shows no button (the current, running plan). */
private fun planButtonText(plan: ShopPlan): String? = when (plan.action) {
    PlanAction.Subscribe -> "Subscribe"
    PlanAction.Upgrade -> "Upgrade"
    PlanAction.Switch -> "Switch"
    PlanAction.Current -> when (plan.state) {
        SubscriptionState.Canceled -> "Resubscribe"
        SubscriptionState.GracePeriod -> "Fix payment"
        else -> null
    }
}

@Composable
private fun PlanCard(plan: ShopPlan, busy: Boolean, locked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val gold = LocalAppTheme.current.primaryColor
    val slate = MaterialTheme.colorScheme.tertiaryContainer
    val isCurrent = plan.action == PlanAction.Current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) gold.copy(alpha = 0.08f) else slate.copy(alpha = 0.3f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(if (isCurrent) 1.5.dp else 1.dp, if (isCurrent) gold.copy(alpha = 0.8f) else slate)
    ) {
        // Full height side by side, so the price rows of neighbouring cards line up
        Column(modifier = Modifier.fillMaxHeight().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (plan.isUnlimited) {
                    Icon(Icons.Rounded.AllInclusive, contentDescription = null, tint = gold, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (isCurrent) CurrentPlanChip()
            }
            Text(
                text = plan.allowance,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )
            plan.note?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            plan.status?.let { status ->
                val warn = plan.state == SubscriptionState.GracePeriod
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (warn) Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Moods.Watch.core, modifier = Modifier.size(16.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (warn) Moods.Watch.light else MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            val button = planButtonText(plan)
            if (plan.price != null || button != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = plan.price.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isCurrent) 0.7f else 0.95f),
                        modifier = Modifier.weight(1f)
                    )
                    if (button != null) {
                        // Fixing payment or resubscribing happens in Google Play, so those always work
                        val enabled = !locked && (plan.action == PlanAction.Current || plan.canBuy)
                        ActionPill(text = button, enabled = enabled, busy = busy, onClick = onClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentPlanChip() {
    val gold = LocalAppTheme.current.primaryColor
    Row(
        modifier = Modifier
            .background(gold.copy(alpha = 0.15f), PillShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = gold, modifier = Modifier.size(12.dp))
        Text("Current plan", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = gold, maxLines = 1)
    }
}

/** The accent pill that buys; a spinner while its purchase is being confirmed. */
@Composable
private fun ActionPill(text: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val appTheme = LocalAppTheme.current
    val gold = appTheme.primaryColor
    val onAccent = MaterialTheme.colorScheme.onSecondary
    Surface(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = PillShape,
        color = if (enabled || busy) gold else gold.copy(alpha = 0.3f),
        contentColor = onAccent,
        modifier = Modifier.heightIn(min = 36.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(color = onAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else {
                Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

// --- Credit packs ---

@Composable
private fun PacksSection(state: ShopUiState, onPack: (ShopPack) -> Unit) {
    val slate = MaterialTheme.colorScheme.tertiaryContainer
    val gold = LocalAppTheme.current.primaryColor
    val locked = state.busyProductId != null
    Column(modifier = Modifier.testTag(ShopTags.PACKS), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("CREDIT PACKS")
        SectionNote(
            if (state.plans.isNotEmpty()) "Purchased credits never expire. They're used after plan or free credits."
            else "Purchased credits never expire."
        )
        state.packs.forEach { pack ->
            val busy = state.busyProductId == pack.productId
            // Locked while a purchase is being credited, so nobody buys twice by accident
            val canBuy = pack.canBuy && !locked
            // The whole row buys the pack, not just its price
            Surface(
                onClick = { onPack(pack) },
                enabled = canBuy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = slate.copy(alpha = 0.3f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, slate)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${pack.credits} Credits",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${pack.credits} minutes of AI transcription",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .heightIn(min = 32.dp)
                            .background(if (canBuy || busy) gold else gold.copy(alpha = 0.3f), PillShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val onAccent = MaterialTheme.colorScheme.onSecondary
                        if (busy) {
                            CircularProgressIndicator(color = onAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Text(pack.price ?: "Buy", color = onAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

// --- Notes and footer ---

/** One calm note in place of the sections: nothing on offer, or Play can't sell. */
@Composable
private fun QuietNote(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(28.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun ShopFooter(state: ShopUiState, onManage: () -> Unit) {
    val subscribed = state.subscribedProductId != null
    val offersPlans = state.plans.isNotEmpty() && state.unavailableMessage == null && !state.isLoading
    if (!subscribed && !offersPlans) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (offersPlans) {
            Text(
                text = "Cancel anytime in Google Play.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center
            )
        }
        if (subscribed) {
            TextButton(onClick = onManage, modifier = Modifier.testTag(ShopTags.MANAGE)) {
                Text("Manage subscription", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
