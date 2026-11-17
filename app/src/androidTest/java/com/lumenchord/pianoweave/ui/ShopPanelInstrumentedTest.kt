package com.lumenchord.pianoweave.ui

import androidx.compose.foundation.layout.Box
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
        action = action, isUnlimited = true, note = "Fair use: up to 300 minutes of transcription a day", canBuy = canBuy
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
    fun onlyPlans() {
        show(ShopUiState(balance = freeBalance, plans = listOf(pro(), unlimited()), freeMinutes = 30))
        rule.onNodeWithTag(ShopTags.PLANS).assertExists()
        rule.onNodeWithTag(ShopTags.PACKS).assertDoesNotExist()
        rule.onNodeWithText("replace the 30 free credits", substring = true).assertExists()
        rule.onNodeWithText("Fair use: up to 300 minutes of transcription a day").assertExists()
        rule.onNodeWithText("Cancel anytime in Google Play.").performScrollTo().assertExists()
        rule.onNodeWithTag(ShopTags.MANAGE).assertDoesNotExist()
        rule.onNodeWithText("10 free · 24 purchased").assertExists()
    }

    @Test
    fun onlyPacks() {
        show(ShopUiState(balance = freeBalance, packs = listOf(pack), freeMinutes = 30))
        rule.onNodeWithTag(ShopTags.PACKS).assertExists()
        rule.onNodeWithTag(ShopTags.PLANS).assertDoesNotExist()
        rule.onNodeWithText("Cancel anytime in Google Play.").assertDoesNotExist()
        rule.onNodeWithText("16.99 PLN").performClick()
        assertEquals(listOf("credits_100"), packsChosen)
    }

    @Test
    fun plansAndPacks() {
        show(ShopUiState(balance = freeBalance, plans = listOf(pro(), unlimited()), packs = listOf(pack), freeMinutes = 30))
        rule.onNodeWithTag(ShopTags.PLANS).assertExists()
        rule.onNodeWithTag(ShopTags.PACKS).assertExists()
        rule.onNodeWithText("19.99 PLN / month").assertExists()
        rule.onNodeWithText("They're used after plan or free credits.", substring = true).assertExists()
        rule.onAllSubscribe().first().performScrollTo().performClick()
        assertEquals(listOf("sub_pro"), plansChosen)
    }

    @Test
    fun nothingOnOffer() {
        show(ShopUiState(balance = freeBalance, freeMinutes = 30))
        rule.onNodeWithTag(ShopTags.EMPTY).assertExists()
        rule.onNodeWithText("PLANS").assertDoesNotExist()
        rule.onNodeWithText("CREDIT PACKS").assertDoesNotExist()
        rule.onNodeWithText("Cancel anytime in Google Play.").assertDoesNotExist()
    }

    @Test
    fun loadingAndUnavailable() {
        show(ShopUiState(balance = ShopBalance(total = null), isLoading = true))
        rule.onNodeWithTag(ShopTags.LOADING).assertExists()
        rule.onNodeWithTag(ShopTags.EMPTY).assertDoesNotExist()
        rule.onNodeWithText("— Credits").assertExists()
    }

    @Test
    fun billingUnavailable() {
        show(ShopUiState(balance = freeBalance, plans = listOf(pro()), unavailableMessage = "Google Play Billing isn't available."))
        rule.onNodeWithTag(ShopTags.UNAVAILABLE).assertExists()
        rule.onNodeWithTag(ShopTags.PLANS).assertDoesNotExist()
    }

    @Test
    fun currentUnlimitedPlan() {
        show(
            ShopUiState(
                balance = ShopBalance(
                    total = 24, paidCredits = 24, planName = "Unlimited", unlimited = true,
                    unlimitedMinutesLeft = 250, unlimitedDailyMinutes = 300
                ),
                plans = listOf(
                    pro(PlanAction.Switch),
                    unlimited(PlanAction.Current).copy(status = "Renews on Dec 17, 2026", state = SubscriptionState.Active)
                ),
                packs = listOf(pack),
                freeMinutes = 30,
                subscribedProductId = "sub_unlimited"
            )
        )
        // The balance and the plan's name
        rule.onAllNodesWithText("Unlimited").assertCountEquals(2)
        rule.onNodeWithText("250 of 300 minutes left today · 24 purchased credits kept for later").assertExists()
        rule.onNodeWithText("Current plan").assertExists()
        rule.onNodeWithText("Renews on Dec 17, 2026").assertExists()
        rule.onNodeWithText("Switch").assertIsEnabled()
        rule.onNodeWithTag(ShopTags.MANAGE).performScrollTo().performClick()
        assertEquals(1, manageOpened)
    }

    @Test
    fun canceledPlan() {
        show(
            ShopUiState(
                balance = ShopBalance(total = 60, periodCredits = 60, paidCredits = 0, planName = "Pro", planMinutes = 100),
                plans = listOf(
                    pro(PlanAction.Current).copy(status = "Ends on Dec 17, 2026", state = SubscriptionState.Canceled),
                    unlimited(PlanAction.Upgrade)
                ),
                subscribedProductId = "sub_pro"
            )
        )
        rule.onNodeWithText("Ends on Dec 17, 2026").assertExists()
        rule.onNodeWithText("60 from Pro · 0 purchased").assertExists()
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
        rule.onNodeWithText("16.99 PLN").performScrollTo().assertExists()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllSubscribe() =
        onAllNodes(androidx.compose.ui.test.hasText("Subscribe")).let { nodes ->
            List(nodes.fetchSemanticsNodes().size) { nodes[it] }
        }
}
