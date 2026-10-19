package com.telaffuz.pronunciation.ui

import com.telaffuz.pronunciation.domain.AudioRecorder
import com.telaffuz.pronunciation.domain.PronunciationScorer
import com.telaffuz.pronunciation.domain.SentenceProvider
import com.telaffuz.pronunciation.domain.UnknownWordException
import com.telaffuz.pronunciation.domain.model.ScoreResult
import com.telaffuz.pronunciation.ui.PronunciationUiState.Phase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PronunciationViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val recorder = FakeRecorder()
    private val sentences = SequenceSentences("FIRST", "SECOND", "THIRD", "FOURTH")
    private var scoreCalls = mutableListOf<Pair<String, Int>>()
    private var scorer = PronunciationScorer { text, samples ->
        scoreCalls += text to samples.size
        ScoreResult(text, humanTotal = 8, words = emptyList())
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = PronunciationViewModel(recorder, scorer, sentences, workDispatcher = dispatcher)

    private fun test(block: suspend TestScope.(PronunciationViewModel) -> Unit) =
        runTest(dispatcher) { block(viewModel()) }

    @Test
    fun `baslangicta ilk cumle ve bosta durumu gelir`() = test { vm ->
        val s = vm.state.value
        assertEquals("FIRST", s.targetText)
        assertEquals(Phase.Idle, s.phase)
        assertNull(s.result)
    }

    @Test
    fun `ilk dokunus kaydi baslatir`() = test { vm ->
        vm.onRecordToggle()

        assertEquals(Phase.Recording, vm.state.value.phase)
        assertEquals(1, recorder.starts)
    }

    @Test
    fun `durdurunca kuyruk suresi dolmadan kayit kesilmez`() = test { vm ->
        vm.onRecordToggle()
        vm.onRecordToggle()

        assertEquals(Phase.Finishing, vm.state.value.phase)
        advanceTimeBy(PronunciationViewModel.STOP_TAIL_MILLIS - 1)
        runCurrent()
        assertEquals(0, recorder.stops)

        advanceUntilIdle()
        assertEquals(1, recorder.stops)
    }

    @Test
    fun `basarili skor sonucu yazar ve yeni cumleye gecer`() = test { vm ->
        vm.onRecordToggle()
        vm.onRecordToggle()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(Phase.Done, s.phase)
        assertEquals("FIRST", s.result?.text)
        assertEquals(listOf("FIRST" to FakeRecorder.SAMPLES), scoreCalls)
        assertEquals("SECOND", s.targetText)
    }

    @Test
    fun `kullanicinin yazdigi metin skorlanir`() = test { vm ->
        vm.onTextChange("I LIKE THIS BOOK")
        vm.onRecordToggle()
        vm.onRecordToggle()
        advanceUntilIdle()

        assertEquals("I LIKE THIS BOOK", scoreCalls.single().first)
    }

    @Test
    fun `skor hatasi mesajiyla hata durumuna gecer ve cumle degismez`() = runTest(dispatcher) {
        scorer = PronunciationScorer { _, _ -> throw UnknownWordException(listOf("xyzzy")) }
        val vm = viewModel()
        vm.onRecordToggle()
        vm.onRecordToggle()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(Phase.Error, s.phase)
        assertEquals(true, s.errorMessage?.contains("xyzzy"))
        assertNull(s.result)
        assertEquals("FIRST", s.targetText)
    }

    @Test
    fun `islem surerken dokunus yok sayilir`() = test { vm ->
        vm.onRecordToggle()
        vm.onRecordToggle()
        vm.onRecordToggle()
        vm.onShuffle()
        advanceUntilIdle()

        assertEquals(1, recorder.starts)
        assertEquals(1, recorder.stops)
        assertEquals(1, scoreCalls.size)
    }

    @Test
    fun `kayit sirasinda karistirma yok sayilir`() = test { vm ->
        vm.onRecordToggle()
        vm.onShuffle()

        assertEquals("FIRST", vm.state.value.targetText)
        assertEquals(Phase.Recording, vm.state.value.phase)
    }

    @Test
    fun `karistirma sonucu ve hatayi temizler`() = test { vm ->
        vm.onRecordToggle()
        vm.onRecordToggle()
        advanceUntilIdle()
        vm.onShuffle()

        val s = vm.state.value
        assertEquals(Phase.Idle, s.phase)
        assertNull(s.result)
        assertEquals("THIRD", s.targetText)
    }

    @Test
    fun `yeni kayit onceki sonucu ve hatayi temizler`() = test { vm ->
        vm.onRecordToggle()
        vm.onRecordToggle()
        advanceUntilIdle()
        vm.onRecordToggle()

        assertNull(vm.state.value.result)
        assertNull(vm.state.value.errorMessage)
        assertEquals(Phase.Recording, vm.state.value.phase)
    }

    @Test
    fun `mikrofon acilamazsa hata gosterilir`() = test { vm ->
        recorder.failOnStart = IllegalStateException("Mikrofon meşgul")
        vm.onRecordToggle()

        assertEquals(Phase.Error, vm.state.value.phase)
        assertEquals("Mikrofon meşgul", vm.state.value.errorMessage)
    }

    @Test
    fun `ses seviyesi kaydedicinin akisi`() = test { vm ->
        assertSame(recorder.level, vm.audioLevel)
    }

    private class FakeRecorder : AudioRecorder {
        var starts = 0
        var stops = 0
        var failOnStart: Exception? = null
        override val level: StateFlow<Float> = MutableStateFlow(0f)

        override fun start() {
            failOnStart?.let { throw it }
            starts++
        }

        override fun stop(): FloatArray {
            stops++
            return FloatArray(SAMPLES)
        }

        companion object {
            const val SAMPLES = 1600
        }
    }

    /** Sırayla dönen, rastgelelik içermeyen cümle sağlayıcı. */
    private class SequenceSentences(vararg items: String) : SentenceProvider {
        private val queue = ArrayDeque(items.toList())
        override fun next(exclude: String?): String = queue.removeFirst()
    }
}
