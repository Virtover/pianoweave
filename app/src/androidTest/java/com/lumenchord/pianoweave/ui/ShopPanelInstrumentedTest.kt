package com.lumenchord.pianoweave.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.hasText
import com.lumenchord.pianoweave.ui.screens.learn.ShopMessage
import com.lumenchord.pianoweave.ui.screens.learn.ShopMessageSaver
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lumenchord.pianoweave.billing.PlanAction
import com.lumenchord.pianoweave.billing.SubscriptionState
import com.lumenchord.pianoweave.ui.screens.learn.ShopBalance
import com.lumenchord.pianoweave.ui.screens.learn.ShopPack
import com.lumenchord.pianoweave.ui.screens.learn.ShopPanel
import com.lumenchord.pianoweave.ui.screens.learn.ShopPlan
import com.lumenchord.pianoweave.ui.screens.learn.ShopTags
import com.lumenchord.pianoweave.ui.screens.learn.ShopUiState
import com.lumenchord.pianoweave.ui.theme.PianoWeaveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shop's states drawn from plain data, without Play Billing. */
@RunWith(AndroidJUnit4::class)
class ShopPanelInstrumentedTest {

    @get:Rule
    val rule = createComposeRule()

    private val freeBalance = ShopBalance(total = 34, periodCredits = 10, paidCredits = 24, freeMinutes = 30, freeEvery = "every 30 days")

    private fun pro(action: PlanAction = PlanAction.Subscribe, canBuy: Boolean = true) = ShopPlan(
        productId = "sub_pro", name = "Pro", allowance = "100 Credits a month", price = "19.99 PLN / month",
        action = action, canBuy = canBuy
    )

    private fun unlimited(action: PlanAction = PlanAction.Subscribe, canBuy: Boolean = true) = ShopPlan(
        productId = "sub_unlimited", name = "Unlimited", allowance = "Unlimited transcriptions", price = "49.99 PLN / month",
        action = action, isUnlimited = true, canBuy = canBuy
    )

    private val pack = ShopPack("credits_100", 100, "16.99 PLN", canBuy = true)

    private var plansChosen = mutableListOf<String>()
    private var packsChosen = mutableListOf<String>()
    private var manageOpened = 0

    private fun show(state: ShopUiState, fontScale: Float? = null, widthDp: Int? = null) {
        rule.setContent {
            PianoWeaveTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
                    Box(if (widthDp != null) Modifier.width(widthDp.dp) else Modifier) {
                        ShopPanel(
                            state = state,
                            onPlan = { plansChosen += it.productId },
                            onPack = { packsChosen += it.productId },
                            onManage = { manageOpened++ },
                            onClose = {}
                        )
                    }
                }
            }
        }
    }

    @Test
    fun onlyPlansHasNoSwitch() {
        show(ShopUiState(balance = freeBalance, plans = listOf(pro(), unlimited()), freeMinutes = 30))
        rule.onNodeWithTag(ShopTags.TABS).assertDoesNotExist()
        rule.onNodeWithTag(ShopTags.PLANS).assertExists()
        rule.onNodeWithTag(ShopTags.PACKS).assertDoesNotExist()
        rule.onNodeWithText("Replaces the 30 free credits each period. Unused plan credits don't carry over.").assertExists()
        rule.onNodeWithText("Fair use", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Cancel anytime in Google Play.").performScrollTo().assertExists()
        rule.onNodeWithTag(ShopTags.MANAGE).assertDoesNotExist()
        rule.onNodeWithText("10 free · 24 purchased").assertExists()
    }

    @Test
    fun withoutFreeCreditsThePlanLineSaysOnlyWhatMatters() {
        show(ShopUiState(balance = freeBalance, plans = listOf(pro())))
        rule.onNodeWithText("Unused plan credits don't carry over.").assertExists()
        rule.onNodeWithText("free credits", substring = true).assertDoesNotExist()
    }

    @Test
    fun onlyPacksHasNoSwitch() {
        show(ShopUiState(balance = freeBalance, packs = listOf(pack), freeMinutes = 30))
        rule.onNodeWithTag(ShopTags.TABS).assertDoesNotExist()
        rule.onNodeWithTag(ShopTags.PACKS).assertExists()
        rule.onNodeWithTag(ShopTags.PLANS).assertDoesNotExist()
        rule.onNodeWithText("Purchased credits never expire; used after plan or free credits.").assertExists()
        rule.onNodeWithText("Cancel anytime in Google Play.").assertDoesNotExist()
        rule.onNodeWithText("16.99 PLN").performClick()
        assertEquals(listOf("credits_100"), packsChosen)
    }

    @Test
    fun bothOpenOnPlansAndSwitchToPacks() {
        show(ShopUiState(balance = freeBalance, plans = listOf(pro(), unlimited()), packs = listOf(pack), freeMinutes = 30))
        rule.onNodeWithTag(ShopTags.TABS).assertExists()
        // One list at a time, plans first for someone without a plan
        rule.onNodeWithTag(ShopTags.PLANS).assertExists()
        rule.onNodeWithTag(ShopTags.PACKS).assertDoesNotExist()
        rule.onNodeWithText("19.99 PLN / month").assertExists()
        rule.onAllSubscribe().first().performScrollTo().performClick()
        assertEquals(listOf("sub_pro"), plansChosen)

        rule.onNodeWithText("Credit packs").performClick()
        rule.onNodeWithTag(ShopTags.PACKS).assertExists()
        rule.onNodeWithTag(ShopTags.PLANS).assertDoesNotExist()
        rule.onNodeWithText("Cancel anytime in Google Play.").assertDoesNotExist()
        rule.onNodeWithText("16.99 PLN").performClick()
        assertEquals(listOf("credits_100"), packsChosen)

        rule.onNodeWithText("Plans").performClick()
        rule.onNodeWithTag(ShopTags.PLANS).assertExists()
    }

    @Test
    fun nothingOnOffer() {
        show(ShopUiState(balance = freeBalance, freeMinutes = 30))
        rule.onNodeWithTag(ShopTags.EMPTY).assertExists()
        rule.onNodeWithTag(ShopTags.TABS).assertDoesNotExist()
        rule.onNodeWithTag(ShopTags.PLANS).assertDoesNotExist()
        rule.onNodeWithTag(ShopTags.PACKS).assertDoesNotExist()
        rule.onNodeWithText("Cancel anytime in Google Play.").assertDoesNotExist()
    }

    @Test
    fun loading() {
        show(ShopUiState(balance = ShopBalance(total = null), plans = listOf(pro()), packs = listOf(pack), isLoading = true))
        rule.onNodeWithTag(ShopTags.LOADING).assertExists()
        rule.onNodeWithTag(ShopTags.TABS).assertDoesNotExist()
        rule.onNodeWithText("— Credits").assertExists()
    }

    @Test
    fun billingUnavailable() {
        show(ShopUiState(balance = freeBalance, plans = listOf(pro()), packs = listOf(pack), unavailableMessage = "Google Play Billing isn't available."))
        rule.onNodeWithTag(ShopTags.UNAVAILABLE).assertExists()
        rule.onNodeWithTag(ShopTags.TABS).assertDoesNotExist()
        rule.onNodeWithTag(ShopTags.PLANS).assertDoesNotExist()
    }

    private fun unlimitedSubscriber() = ShopUiState(
        balance = ShopBalance(
            total = 24, paidCredits = 24, planName = "Unlimited", unlimited = true,
            planStatus = "Renews on Dec 17, 2026"
        ),
        plans = listOf(
            pro(PlanAction.Switch),
            unlimited(PlanAction.Current).copy(status = "Renews on Dec 17, 2026", state = SubscriptionState.Active)
        ),
        packs = listOf(pack),
        freeMinutes = 30,
        subscribedProductId = "sub_unlimited"
    )

    @Test
    fun subscriberOpensOnPacksWithAManageLink() {
        show(unlimitedSubscriber())
        rule.onNodeWithTag(ShopTags.TABS).assertExists()
        rule.onNodeWithTag(ShopTags.PACKS).assertExists()
        rule.onNodeWithTag(ShopTags.PLANS).assertDoesNotExist()
        // Long with the renewal where it fits on one row, short otherwise; never wrapped
        rule.onNode(
            hasText("Unlimited plan · Renews on Dec 17, 2026 · 24 purchased credits kept") or hasText("Unlimited plan · 24 credits kept")
        ).assertExists()
        rule.onNodeWithText("minutes left", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Busy day", substring = true).assertDoesNotExist()
        // The plan's card isn't shown here, so the footer links to Google Play
        rule.onNodeWithTag(ShopTags.MANAGE).performScrollTo().performClick()
        assertEquals(1, manageOpened)
    }

    @Test
    fun currentPlanIsManagedOrCancelledFromItsCard() {
        show(unlimitedSubscriber())
        rule.onNodeWithText("Plans").performClick()
        // The balance and the plan's name
        rule.onAllNodesWithText("Unlimited").assertCountEquals(2)
        rule.onNodeWithText("CURRENT").assertExists()
        rule.onNodeWithText("Renews on Dec 17, 2026").assertExists()
        rule.onNodeWithText("Switch").assertIsEnabled()
        // On the card itself, so the footer doesn't repeat it
        rule.onNodeWithTag(ShopTags.MANAGE).assertDoesNotExist()
        rule.onNodeWithText("Manage or cancel").assertIsEnabled().performClick()
        assertEquals(listOf("sub_unlimited"), plansChosen)
        rule.onNodeWithText("Cancel anytime in Google Play.").assertDoesNotExist()
    }

    @Test
    fun busyDayOfAnUnlimitedPlan() {
        val state = unlimitedSubscriber()
        show(state.copy(balance = state.balance.copy(heavyUse = true)))
        rule.onNodeWithText("Busy day — your transcriptions run one at a time for now.").assertExists()
        rule.onAllNodesWithText("Unlimited").assertCountEquals(1)
    }

    @Test
    fun scheduledMoveToALowerPlan() {
        show(
            ShopUiState(
                balance = ShopBalance(total = 0, planName = "Unlimited", unlimited = true, planStatus = "Switches to Pro today at 21:36"),
                plans = listOf(
                    pro(PlanAction.Scheduled).copy(status = "Starts today at 21:36"),
                    unlimited(PlanAction.Current).copy(status = "Switches to Pro today at 21:36", state = SubscriptionState.Active),
                    ShopPlan("sub_max", "Max", "Unlimited transcriptions", "99.99 PLN / month", PlanAction.Upgrade, canBuy = true)
                ),
                packs = listOf(pack),
                subscribedProductId = "sub_unlimited"
            )
        )
        rule.onNodeWithText("Plans").performClick()
        rule.onNodeWithText("Switches to Pro today at 21:36").assertExists()
        rule.onNodeWithText("Manage or cancel").assertIsEnabled()
        rule.onNodeWithText("SCHEDULED").assertExists()
        rule.onNodeWithText("Starts today at 21:36").assertExists()
        rule.onNodeWithText("Switch").assertDoesNotExist()
        // A higher plan can still be chosen
        rule.onNodeWithText("Upgrade").performScrollTo().assertIsEnabled()
    }

    @Test
    fun aPhoneGetsTheShortUnlimitedLine() {
        show(unlimitedSubscriber(), widthDp = 360)
        rule.onNodeWithText("Unlimited plan · 24 credits kept").assertExists()
    }

    @Test
    fun chosenTabSurvivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent { PianoWeaveTheme { ShopPanel(unlimitedSubscriber(), {}, {}, {}, {}) } }
        // A subscriber opens on the packs, then picks the plans
        rule.onNodeWithTag(ShopTags.PACKS).assertExists()
        rule.onNodeWithText("Plans").performClick()
        tester.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag(ShopTags.PLANS).assertExists()
        rule.onNodeWithTag(ShopTags.PACKS).assertDoesNotExist()
    }

    @Test
    fun aDismissedMessageStaysDismissedThroughRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            PianoWeaveTheme {
                var message by rememberSaveable(stateSaver = ShopMessageSaver) { mutableStateOf(null) }
                Column {
                    Text("Show", Modifier.clickable { message = ShopMessage("Almost there", "Kept for the server.") })
                    message?.let { Text(it.title, Modifier.clickable { message = null }) }
                }
            }
        }
        rule.onNodeWithText("Show").performClick()
        // Not dismissed yet: it stays through a rotation
        tester.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Almost there").assertExists().performClick()
        // Dismissed: it never comes back
        tester.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Almost there").assertDoesNotExist()
    }

    @Test
    fun canceledPlan() {
        show(
            ShopUiState(
                balance = ShopBalance(total = 60, periodCredits = 60, paidCredits = 0, planName = "Pro", planMinutes = 100),
                plans = listOf(
                    pro(PlanAction.Current).copy(status = "Ends today at 21:14", state = SubscriptionState.Canceled),
                    unlimited(PlanAction.Upgrade)
                ),
                subscribedProductId = "sub_pro"
            )
        )
        rule.onNodeWithText("Ends today at 21:14").assertExists()
        rule.onNodeWithText("60 from Pro · 0 purchased").assertExists()
        rule.onNodeWithText("Manage or cancel").assertDoesNotExist()
        rule.onNodeWithText("Resubscribe").performClick()
        rule.onNodeWithText("Upgrade").assertIsEnabled()
        assertEquals(listOf("sub_pro"), plansChosen)
    }

    @Test
    fun gracePeriod() {
        show(
            ShopUiState(
                balance = ShopBalance(total = 60, periodCredits = 60, paidCredits = 0, planName = "Pro", planMinutes = 100),
                plans = listOf(
                    pro(PlanAction.Current).copy(status = "Payment issue — update it in Google Play", state = SubscriptionState.GracePeriod),
                    unlimited(PlanAction.Upgrade, canBuy = false)
                ),
                subscribedProductId = "sub_pro",
                switchNote = "Your plan was bought with another Google Play account. To change it, use Google Play on that account."
            )
        )
        rule.onNodeWithText("Payment issue — update it in Google Play").assertExists()
        rule.onNodeWithText("Fix payment").assertIsEnabled()
        rule.onNodeWithText("Upgrade").assertIsNotEnabled()
        rule.onNodeWithText("another Google Play account", substring = true).assertExists()
    }

    @Test
    fun developerAccount() {
        show(ShopUiState(balance = ShopBalance(total = 0, developer = true), packs = listOf(pack)))
        rule.onNodeWithText("Developer account: your transcriptions are free.").assertExists()
        rule.onNodeWithText("Unlimited").assertExists()
    }

    @Test
    fun busyPurchaseLocksTheRest() {
        show(ShopUiState(balance = freeBalance, plans = listOf(pro()), packs = listOf(pack), busyProductId = "credits_100"))
        rule.onNodeWithText("Subscribe").assertIsNotEnabled()
    }

    @Test
    fun narrowPhoneWithLargeFont() {
        show(
            ShopUiState(balance = freeBalance, plans = listOf(pro(), unlimited()), packs = listOf(pack), freeMinutes = 30),
            fontScale = 2f,
            widthDp = 320
        )
        rule.onNodeWithText("19.99 PLN / month").performScrollTo().assertExists()
        rule.onAllSubscribe().first().performScrollTo().assertIsEnabled()
        rule.onNodeWithText("Credit packs").performScrollTo().performClick()
        rule.onNodeWithText("16.99 PLN").performScrollTo().assertExists()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllSubscribe() =
        onAllNodes(androidx.compose.ui.test.hasText("Subscribe")).let { nodes ->
            List(nodes.fetchSemanticsNodes().size) { nodes[it] }
        }
}
