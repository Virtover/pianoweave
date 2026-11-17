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
import androidx.compose.material.icons.rounded.Schedule
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
import com.lumenchord.pianoweave.billing.CurrentPlanAction
import com.lumenchord.pianoweave.billing.PlanAction
import com.lumenchord.pianoweave.billing.SubscriptionState
import com.lumenchord.pianoweave.billing.creditsLabel
import com.lumenchord.pianoweave.billing.owedMinutes
import com.lumenchord.pianoweave.ui.components.DialogHeader
import com.lumenchord.pianoweave.ui.components.DialogSurface
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.Moods
import com.lumenchord.pianoweave.ui.theme.PillShape
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import kotlinx.coroutines.launch

/** Test tags of the shop's parts. */
object ShopTags {
    const val PLANS = "shop_plans"
    const val PACKS = "shop_packs"
    const val EMPTY = "shop_empty"
    const val LOADING = "shop_loading"
    const val UNAVAILABLE = "shop_unavailable"
    const val MANAGE = "shop_manage"
    const val TABS = "shop_tabs"
}


/** Plans sit side by side from this width; narrower, they stack. */
private val SIDE_BY_SIDE_MIN_WIDTH = 520.dp

/** The shop's two lists; one is shown at a time. */
enum class ShopTab { Plans, Packs }

private val ShopTabSaver = listSaver<ShopTab?, String>(
    save = { tab -> tab?.let { listOf(it.name) } ?: emptyList() },
    restore = { saved ->
        saved.firstOrNull()?.let { name -> ShopTab.entries.firstOrNull { it.name == name } }
    }
)

/**
 * The shop: the balance, then one list at a time (plans or credit packs, switched by a segmented control when
 * both are offered), one quiet line about it, and a footer. Calm on purpose: no countdowns, no badges shouting a
 * deal, nothing that opens on its own. With nothing on offer there is one tidy note instead of empty lists.
 */
@Composable
fun ShopPanel(
    state: ShopUiState,
    onPlan: (ShopPlan) -> Unit,
    onPack: (ShopPack) -> Unit,
    onManage: () -> Unit,
    onClose: () -> Unit,
    selectedTab: ShopTab? = null,
    onSelectedTab: ((ShopTab) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val gold = LocalAppTheme.current.primaryColor
    val slate = MaterialTheme.colorScheme.tertiaryContainer
    // Chosen afresh on every opening: a subscriber most likely came for credits, anyone else for a plan
    // Saveable, so a rotation keeps the tab; the default applies only when the shop opens
    var chosenTab by rememberSaveable(stateSaver = ShopTabSaver) { mutableStateOf(state.defaultTab) }
    val tab = when {
        state.plans.isEmpty() -> ShopTab.Packs
        state.packs.isEmpty() -> ShopTab.Plans
        else -> selectedTab ?: chosenTab ?: state.defaultTab
    }
    val showsLists = state.unavailableMessage == null && !state.isLoading && !state.isEmpty
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
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    if (state.plans.isNotEmpty() && state.packs.isNotEmpty()) {
                        ShopTabs(selected = tab, onSelect = {
                            chosenTab = it
                            onSelectedTab?.invoke(it)
                        })
                    }
                    when (tab) {
                        ShopTab.Plans -> PlansList(state, onPlan)
                        ShopTab.Packs -> PacksList(state, onPack)
                    }
                }
            }

            ShopFooter(state, plansShown = showsLists && tab == ShopTab.Plans, onManage = onManage)
        }
    }
}

/** "Plans | Credit packs", in the accent, quiet when not chosen. */
@Composable
private fun ShopTabs(selected: ShopTab, onSelect: (ShopTab) -> Unit) {
    val gold = LocalAppTheme.current.primaryColor
    val slate = MaterialTheme.colorScheme.tertiaryContainer
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = gold.copy(alpha = 0.16f),
        activeContentColor = gold,
        activeBorderColor = gold.copy(alpha = 0.6f),
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        inactiveBorderColor = slate
    )
    val tabs = listOf(ShopTab.Plans to "Plans", ShopTab.Packs to "Credit packs")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().testTag(ShopTags.TABS)) {
        tabs.forEachIndexed { index, (tab, label) ->
            SegmentedButton(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                colors = colors,
                icon = {}
            ) {
                Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/** The one quiet line under a list. */
@Composable
private fun ListNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.tertiary,
        lineHeight = 17.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
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
            val (long, short) = unlimitedBalanceLines(balance.planName, balance.planStatus, paid)
            if (short.isNotEmpty()) {
                // The renewal is on the plan's card; here it is added only where the line still fits on one row
                val style = MaterialTheme.typography.bodySmall
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                var room by remember { mutableIntStateOf(Int.MAX_VALUE) }
                val longFits = measurer.measure(long, style, maxLines = 1).size.width +
                    with(density) { 18.dp.roundToPx() } <= room
                ExplainedLine(
                    text = if (longFits) long else short,
                    singleLine = true,
                    onWidth = { room = it },
                    title = balance.planName ?: "Unlimited",
                    explanation = "Transcriptions don't use credits while your plan runs. " +
                        "Purchased credits never expire and wait for when you need them."
                )
            }
            if (balance.heavyUse) {
                // Never a refusal, only a slower pace: said plainly, without alarm
                SmallText("Busy day — your transcriptions run one at a time for now.", color = MaterialTheme.colorScheme.secondary)
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
private fun ExplainedLine(
    text: String,
    title: String,
    explanation: String,
    warn: Boolean = false,
    singleLine: Boolean = false,
    /** The width the line may take, in pixels, for a caller choosing between a long and a short text. */
    onWidth: ((Int) -> Unit)? = null
) {
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
                .then(if (onWidth != null) Modifier.fillMaxWidth().onSizeChanged { onWidth(it.width) } else Modifier)
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
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
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
private fun PlansList(state: ShopUiState, onPlan: (ShopPlan) -> Unit) {
    val locked = state.busyProductId != null
    Column(modifier = Modifier.testTag(ShopTags.PLANS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= SIDE_BY_SIDE_MIN_WIDTH && state.plans.size in 2..3) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    state.plans.forEach { plan ->
                        PlanCard(plan, state.busyProductId == plan.productId, locked, { onPlan(plan) }, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.plans.forEach { plan ->
                        PlanCard(plan, state.busyProductId == plan.productId, locked, { onPlan(plan) }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        state.switchNote?.let { ListNote(it) }
        ListNote(
            if (state.freeMinutes > 0) "Replaces the ${state.freeMinutes} free credits each period. Unused plan credits don't carry over."
            else "Unused plan credits don't carry over."
        )
    }
}

/**
 * What a plan's button says. The running plan's card opens Google Play's page for it: to manage or cancel it,
 * resubscribe once cancelled, or fix a failed payment.
 */
internal fun planButtonText(plan: ShopPlan): String? = when (plan.action) {
    PlanAction.Scheduled -> null
    PlanAction.Subscribe -> "Subscribe"
    PlanAction.Upgrade -> "Upgrade"
    PlanAction.Switch -> "Switch"
    PlanAction.Current -> when (plan.currentActionOrDefault) {
        CurrentPlanAction.Resubscribe -> "Resubscribe"
        CurrentPlanAction.FixPayment -> "Fix payment"
        CurrentPlanAction.Manage -> "Manage or cancel"
    }
}

/** The current plan's action, or what its state implies when not given. */
internal val ShopPlan.currentActionOrDefault: CurrentPlanAction
    get() = currentAction ?: when (state) {
        SubscriptionState.Canceled -> CurrentPlanAction.Resubscribe
        SubscriptionState.GracePeriod -> CurrentPlanAction.FixPayment
        else -> CurrentPlanAction.Manage
    }

/**
 * A plan: its name (and the current badge), what it gives, then its price per period, or for the current plan
 * how it stands, and its one action.
 */
@Composable
private fun PlanCard(
    plan: ShopPlan,
    busy: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gold = LocalAppTheme.current.primaryColor
    val slate = MaterialTheme.colorScheme.tertiaryContainer
    val isCurrent = plan.action == PlanAction.Current
    val isScheduled = plan.action == PlanAction.Scheduled
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) gold.copy(alpha = 0.08f) else slate.copy(alpha = 0.3f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(if (isCurrent) 1.5.dp else 1.dp, if (isCurrent) gold.copy(alpha = 0.8f) else slate)
    ) {
        // Full height side by side, so the bottom rows of neighbouring cards line up
        Column(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
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
                if (isCurrent) PlanChip("CURRENT")
                if (isScheduled) PlanChip("SCHEDULED", scheduled = true)
            }
            Text(
                text = plan.allowance,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )
            plan.note?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(Modifier.weight(1f))
            val status = if (isCurrent || isScheduled) plan.status else null
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (status != null) {
                    val warn = plan.state == SubscriptionState.GracePeriod
                    Row(
                        modifier = Modifier.weight(1f),
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
                } else {
                    Text(
                        text = plan.price.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Managing, resubscribing and fixing payment happen in Google Play, so they always work
                val enabled = !locked && (isCurrent || plan.canBuy)
                // A scheduled plan has nothing left to do: its badge says it all
                planButtonText(plan)?.let { text ->
                    ActionPill(
                        text = text,
                        enabled = enabled,
                        busy = busy,
                        // Managing a running plan is a quiet outlined action; buying and repairing are filled
                        outlined = isCurrent && plan.currentActionOrDefault == CurrentPlanAction.Manage,
                        onClick = onClick
                    )
                }
            }
            plan.hint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** "CURRENT" on the running plan; "SCHEDULED", quieter, on the plan a change moves to at the renewal. */
@Composable
private fun PlanChip(text: String, scheduled: Boolean = false) {
    val gold = LocalAppTheme.current.primaryColor
    val tint = if (scheduled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) else gold
    Row(
        modifier = Modifier
            .background(if (scheduled) MaterialTheme.colorScheme.tertiaryContainer else gold.copy(alpha = 0.15f), PillShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            if (scheduled) Icons.Rounded.Schedule else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp)
        )
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tint, maxLines = 1)
    }
}

/** The pill that acts on a plan; a spinner while its purchase is being confirmed. */
@Composable
private fun ActionPill(text: String, enabled: Boolean, busy: Boolean, outlined: Boolean, onClick: () -> Unit) {
    val gold = LocalAppTheme.current.primaryColor
    val onAccent = MaterialTheme.colorScheme.onSecondary
    val content = if (outlined) gold else onAccent
    Surface(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = PillShape,
        color = when {
            outlined -> Color.Transparent
            enabled || busy -> gold
            else -> gold.copy(alpha = 0.3f)
        },
        contentColor = content,
        border = if (outlined) BorderStroke(1.dp, gold.copy(alpha = 0.7f)) else null,
        modifier = Modifier.heightIn(min = 36.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else {
                Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

// --- Credit packs ---

@Composable
private fun PacksList(state: ShopUiState, onPack: (ShopPack) -> Unit) {
    val slate = MaterialTheme.colorScheme.tertiaryContainer
    val gold = LocalAppTheme.current.primaryColor
    val locked = state.busyProductId != null
    Column(modifier = Modifier.testTag(ShopTags.PACKS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "${pack.credits} Credits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
        ListNote("Purchased credits never expire; used after plan or free credits.")
    }
}

// --- Notes and footer ---

/** One calm note in place of the lists: nothing on offer, or Play can't sell. */
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

/**
 * Under the plans, for someone without one: that plans can be cancelled anytime. A subscriber manages the plan
 * from its card; only where that card isn't shown (the credit packs, or no list at all) is there a link instead.
 */
@Composable
private fun ShopFooter(state: ShopUiState, plansShown: Boolean, onManage: () -> Unit) {
    when {
        state.subscribedProductId != null && !plansShown -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = onManage, modifier = Modifier.testTag(ShopTags.MANAGE)) {
                Text("Manage in Google Play", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
            }
        }
        state.subscribedProductId == null && plansShown -> ListNote("Cancel anytime in Google Play.")
    }
}

/**
 * An unlimited plan's line under "Unlimited": the long one with the renewal ("Unlimited plan · Renews on … · 1830
 * purchased credits kept") for wide screens, and the short one ("Unlimited plan · 1830 credits kept") that fits a
 * phone. Empty when there's nothing to say.
 */
internal fun unlimitedBalanceLines(planName: String?, planStatus: String?, paid: Int?): Pair<String, String> {
    val plan = planName?.takeIf { it.isNotBlank() }?.let { "$it plan" }
    val kept = paid?.takeIf { it > 0 }
    val credits = kept?.let { if (it == 1) "credit" else "credits" }
    val long = listOfNotNull(plan, planStatus, kept?.let { "$it purchased $credits kept" }).joinToString(" · ")
    val short = listOfNotNull(plan, kept?.let { "$it $credits kept" }).joinToString(" · ")
    return long to short.ifEmpty { long }
}
