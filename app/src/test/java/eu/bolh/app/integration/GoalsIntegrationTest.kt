package eu.bolh.app.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.bolh.app.ui.goals.GoalsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Карточка цели на настоящей базе: бюджет считается по истории пополнений. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class GoalsIntegrationTest {

    private lateinit var app: TestApp

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = TestApp()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun goalCardShowsBudgetCollectedFromContributions() = runBlocking {
        val laptop = app.goal("Laptop")
        val plain = app.goal("Plain")
        app.finance.baseCurrency = "AMD"
        val jar = app.finance.createJar(laptop, 21_850_000L)
        app.finance.addContribution(jar, 5_000_000L)
        app.finance.addContribution(jar, 1_000_000L)

        val vm = GoalsViewModel(app.goals, app.habits, app.finance, app.container.settings)
        val state = withTimeout(10_000) { vm.state.first { it.loaded && it.goals.size == 2 } }

        val budget = state.goals.single { it.goal.id == laptop }.budget!!
        assertEquals(6_000_000L, budget.collectedMinor)
        assertEquals(21_850_000L, budget.targetMinor)
        assertEquals("AMD", budget.currencyCode)
        assertFalse(budget.isCollected)
        assertNull(state.goals.single { it.goal.id == plain }.budget)
    }

    @Test
    fun budgetIsCollectedWhenContributionsReachTarget() = runBlocking {
        val cushion = app.goal("Cushion")
        val jar = app.finance.createJar(cushion, 100_000L)
        app.finance.addContribution(jar, 100_000L)

        val vm = GoalsViewModel(app.goals, app.habits, app.finance, app.container.settings)
        val state = withTimeout(10_000) { vm.state.first { it.loaded && it.goals.isNotEmpty() } }

        assertTrue(state.goals.single().budget!!.isCollected)
    }

    @Test
    fun deletedBudgetDisappearsFromCard() = runBlocking {
        val goal = app.goal("Trip")
        val jar = app.finance.createJar(goal, 50_000L)
        app.finance.deleteJar(jar)

        val vm = GoalsViewModel(app.goals, app.habits, app.finance, app.container.settings)
        val state = withTimeout(10_000) { vm.state.first { it.loaded && it.goals.isNotEmpty() } }

        assertNull(state.goals.single().budget)
    }
}
