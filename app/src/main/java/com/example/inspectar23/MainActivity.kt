package com.example.inspectar23

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.inspectar23.ui.theme.InspectAR23Theme
import kotlin.math.abs
import kotlin.math.min
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val RokidGreen = Color(0xFF00FF66)
private val RokidDimGreen = Color(0xFF00A845)

class MainActivity : ComponentActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private val handler = Handler(Looper.getMainLooper())
    private var handleSpeech: ((List<String>) -> Unit)? = null
    private var isListening = false
    private lateinit var driveSyncManager: DriveSyncManager

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListeningDelayed()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        driveSyncManager = DriveSyncManager(
            activity = this,
            onVehicleReady = { result ->
                appVehicleState = appVehicleState.copy(
                    carNumber = result.vehicleData.carNumber,
                    vin = result.vehicleData.vin,
                    // 차량을 새로 불러올 때 검사 중 입력했던 값은 초기화한다.
                    mileage = "-",
                    tire = "-",
                    brake = "-",
                    sensory = "-",
                    voiceText = "-",
                    bedSize = result.vehicleData.bedSize,
                    tuning = result.vehicleData.tuning,
                    tuningCertifiedPart = result.vehicleData.tuningCertifiedPart,
                    currentIndex = result.index,
                    totalCount = result.total,
                    hasNext = result.hasNext
                )
            }
        )

        setContent {
            InspectAR23Theme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    InspectARApp(
                        onReady = { handler -> handleSpeech = handler },
                        onSync = { driveSyncManager.sync() },
                        onPreviousCar = { driveSyncManager.openPreviousPdf() },
                        onNextCar = { driveSyncManager.openNextPdf() }
                    )
                }
            }
        }

        requestNeededPermissions()
    }

    private fun requestNeededPermissions() {
        val needAudio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED

        if (needAudio) requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        else startListeningDelayed()
    }

    private fun startListeningDelayed() {
        handler.postDelayed({ startListening() }, 10)
    }

    private fun startListening() {
        if (isListening) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 120L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 80L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 50L)
        }

        speechRecognizer.setRecognitionListener(
            SimpleRecognitionListener(
                onResult = { candidates ->
                    isListening = false
                    handleSpeech?.invoke(candidates)
                    startListeningDelayed()
                },
                onErrorRestart = {
                    isListening = false
                    startListeningDelayed()
                }
            )
        )

        isListening = true
        speechRecognizer.startListening(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        driveSyncManager.handleActivityResult(requestCode, data)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        speechRecognizer.destroy()
    }
}

@androidx.compose.runtime.Stable
data class VehicleUiState(
    val carNumber: String = "-",
    val vin: String = "-",
    val mileage: String = "-",
    val tire: String = "-",
    val brake: String = "-",
    val sensory: String = "-",
    val bedSize: String = "",
    val tuning: String = "",
    val tuningCertifiedPart: String = "",
    val currentIndex: Int = 1,
    val totalCount: Int = 1,
    val hasNext: Boolean = false,
    val voiceText: String = "-",
    val isLocked: Boolean = false
)

var appVehicleState by mutableStateOf(VehicleUiState())

@Composable
fun InspectARApp(
    onReady: ((List<String>) -> Unit) -> Unit,
    onSync: () -> Unit,
    onPreviousCar: () -> Unit,
    onNextCar: () -> Unit
) {
    var state by remember { mutableStateOf(appVehicleState) }
    var mileagePendingDigits by remember { mutableStateOf("") }
    var mileageModeUntil by remember { mutableStateOf(0L) }
    var tireModeUntil by remember { mutableStateOf(0L) }
    var brakeModeUntil by remember { mutableStateOf(0L) }
    val context = LocalContext.current

    LaunchedEffect(appVehicleState) {
        state = appVehicleState
    }

    fun updateState(transform: (VehicleUiState) -> VehicleUiState) {
        appVehicleState = transform(appVehicleState)
        state = appVehicleState
    }

    fun clearInspectionInputs() {
        mileagePendingDigits = ""
        mileageModeUntil = 0L
        tireModeUntil = 0L
        brakeModeUntil = 0L
        updateState {
            it.copy(
                mileage = "-",
                tire = "-",
                brake = "-",
                sensory = "-",
                voiceText = "-"
            )
        }
    }

    fun goPreviousCar() {
        clearInspectionInputs()
        onPreviousCar()
    }

    fun goNextCar() {
        if (appVehicleState.hasNext) {
            clearInspectionInputs()
            onNextCar()
        }
    }

    fun refreshVehicleList() {
        clearInspectionInputs()
        onSync()
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val gestureModifier = Modifier.pointerInput(state.hasNext, isLandscape, state.isLocked) {
        var dragX = 0f
        var dragY = 0f
        detectDragGestures(
            onDragEnd = {
                if (!appVehicleState.isLocked) {
                    when {
                        abs(dragY) > abs(dragX) && dragY > 120f -> refreshVehicleList()
                        abs(dragX) > 120f && dragX < 0f -> goNextCar()
                        abs(dragX) > 120f && dragX > 0f -> goPreviousCar()
                    }
                }
                dragX = 0f
                dragY = 0f
            },
            onDragCancel = {
                dragX = 0f
                dragY = 0f
            },
            onDrag = { change, dragAmount ->
                change.consume()
                dragX += dragAmount.x
                dragY += dragAmount.y
            }
        )
    }

    LaunchedEffect(Unit) {
        onReady { candidates ->
            val allTexts = candidates
                .flatMap { splitPossibleCommands(it) }
                .map { normalizeSpeech(it) }
                .filter { it.isNotBlank() }

            if (allTexts.isEmpty()) return@onReady
            val rawVoiceText = candidates.firstOrNull()?.trim().orEmpty().ifBlank { "-" }
            updateState { it.copy(voiceText = rawVoiceText) }
            val merged = allTexts.joinToString(" ")
            val compact = compactSpeech(merged)
            val keywordCompact = keywordSpeech(merged)

            val now = System.currentTimeMillis()
            val hasMileageKeyword = keywordCompact.contains("주행거리") || keywordCompact.contains("주행") || keywordCompact.contains("키로수") || keywordCompact.contains("키로") || keywordCompact.contains("킬로") || keywordCompact.contains("갱거리") || keywordCompact.contains("쟁거리") || keywordCompact.contains("행거리")
            val hasTireKeyword = keywordCompact.contains("타이어") || keywordCompact.contains("타야") || keywordCompact.contains("타이야")
            val hasBrakeKeyword = keywordCompact.contains("브레이크") || keywordCompact.contains("브레크") || keywordCompact.contains("브레이끼") || keywordCompact.contains("브레끼") || keywordCompact.contains("패드")

            val isLockCommand = isAnyCommand(compact, listOf("정지", "중지", "일시정지", "잠금", "화면잠금"))
            val isUnlockCommand = isAnyCommand(compact, listOf("시작", "다시", "다시시작", "잠금해제", "화면잠금해제"))

            if (appVehicleState.isLocked) {
                if (isUnlockCommand) {
                    updateState { it.copy(isLocked = false, voiceText = "잠금해제") }
                }
                return@onReady
            }

            when {
                isLockCommand -> {
                    updateState { it.copy(isLocked = true, voiceText = "🔒 잠금") }
                    mileagePendingDigits = ""
                    mileageModeUntil = 0L
                    tireModeUntil = 0L
                    brakeModeUntil = 0L
                }

                isAnyCommand(compact, listOf("초기화", "리셋", "초기화해", "다지워")) -> {
                    updateState {
                        it.copy(
                            carNumber = "-",
                            vin = "-",
                            mileage = "-",
                            tire = "-",
                            brake = "-",
                            sensory = "-",
                            bedSize = "",
                            tuning = "",
                            tuningCertifiedPart = "",
                            voiceText = "초기화"
                        )
                    }
                    mileagePendingDigits = ""
                    mileageModeUntil = 0L
                    tireModeUntil = 0L
                    brakeModeUntil = 0L
                }

                isAnyCommand(
                    compact,
                    listOf(
                        "동기화",
                        "새로고침",
                        "갱신",
                        "싱크",
                        "PDF동기화",
                        "차량불러와",
                        "차량 불러와",
                        "차량불러오기",
                        "차량 불러오기",
                        "불러와"
                    )
                ) -> refreshVehicleList()

                isAnyCommand(
                    compact,
                    listOf("다음", "다음차", "다음차량", "다음 차량", "넘겨", "다음으로")
                ) -> goNextCar()

                isAnyCommand(
                    compact,
                    listOf("이전", "이전차", "이전차량", "이전 차량", "앞차", "뒤로")
                ) -> goPreviousCar()

                keywordCompact.contains("차량번호") || keywordCompact.contains("차번호") || keywordCompact.contains("자동차번호") -> {
                    allTexts.firstNotNullOfOrNull { extractPlateLast4FromVoice(it).ifEmpty { null } }?.let { value ->
                        updateState { it.copy(carNumber = value) }
                    }
                }

                keywordCompact.contains("차대번호") || keywordCompact.contains("일련번호") || keywordCompact.contains("끝자리") || keywordCompact.contains("VIN", ignoreCase = true) -> {
                    bestVinDigitsFromCandidates(allTexts)?.let { value ->
                        updateState { it.copy(vin = value) }
                    }
                }

                hasMileageKeyword || now < mileageModeUntil -> {
                    if (hasMileageKeyword) {
                        mileagePendingDigits = ""
                        mileageModeUntil = now + 4500L
                    }

                    val value = bestMileageFromCandidates(allTexts)
                    val digits = value.orEmpty().filter { it.isDigit() }

                    if (digits.isNotBlank()) {
                        // 주행거리는 부분인식 + 최종인식이 반복해서 들어오므로
                        // 기존 값에 이어붙이면 56381 -> 563811 같은 중복이 생긴다.
                        // 따라서 항상 방금 인식된 숫자로 덮어쓴다.
                        mileagePendingDigits = digits.takeLast(7)
                        updateState { it.copy(mileage = formatMileageValue(mileagePendingDigits)) }

                        // 4자리 이상이면 실제 주행거리 입력으로 보고 모드를 종료한다.
                        // 1~3자리만 들어온 경우에는 이어서 말할 수 있도록 잠깐 유지한다.
                        mileageModeUntil = if (mileagePendingDigits.length >= 4) 0L else now + 4500L
                    }
                }

                hasTireKeyword || now < tireModeUntil -> {
                    if (hasTireKeyword) tireModeUntil = now + 4500L
                    val command = merged
                        .replace("타이어라고", " ")
                        .replace("타이어", " ")
                        .replace("타야", " ")
                        .replace("타이야", " ")
                    val (tireValues, brakeValues) = extractTireBrakeCommand(command)
                    if (tireValues.size >= 2) {
                        updateState { it.copy(tire = "${tireValues[0]}, ${tireValues[1]}") }
                        // 음성 부분결과가 먼저 "타이어 45"까지만 들어오고,
                        // 뒤이어 "XX"가 들어오는 경우가 많다. 그래서 타이어만 잡혔다고
                        // 입력모드를 바로 끄지 않고 잠시 유지한다.
                        if (brakeValues.size < 2) tireModeUntil = now + 4500L
                    }
                    if (brakeValues.size >= 2) {
                        updateState { it.copy(brake = "${formatBrake(brakeValues[0])}, ${formatBrake(brakeValues[1])}") }
                        tireModeUntil = 0L
                    }
                }

                hasBrakeKeyword || now < brakeModeUntil -> {
                    if (hasBrakeKeyword) brakeModeUntil = now + 3500L
                    val values = extractBrakeCommand(merged)
                    if (values.size >= 2) {
                        updateState { it.copy(brake = "${formatBrake(values[0])}, ${formatBrake(values[1])}") }
                        brakeModeUntil = 0L
                    }
                }

                isBareMileageCandidate(allTexts) -> {
                    val digits = bestBareMileageFromCandidates(allTexts)
                    if (!digits.isNullOrBlank()) {
                        mileagePendingDigits = digits.takeLast(7)
                        updateState { it.copy(mileage = formatMileageValue(mileagePendingDigits)) }
                    }
                }

                keywordCompact.contains("관능정상") || keywordCompact.contains("관능양호") || keywordCompact.contains("관능이상없") -> {
                    updateState { it.copy(sensory = "-") }
                }

                else -> {
                    val sensoryResults = findSensoryItems(allTexts)
                    if (sensoryResults.isNotEmpty()) {
                        updateState { current ->
                            current.copy(sensory = sensoryResults.fold(current.sensory) { acc, item -> addSensory(acc, item) })
                        }
                    } else {
                        saveAliasCandidate(context, rawVoiceText)
                    }
                }
            }

            val mixedSensory = findSensoryItems(allTexts)
            if (mixedSensory.isNotEmpty()) {
                updateState { current ->
                    current.copy(sensory = mixedSensory.fold(current.sensory) { acc, item -> addSensory(acc, item) })
                }
            }
        }
    }

    if (isLandscape) {
        LandscapeHudScreen(state = state, gestureModifier = gestureModifier, onUnlock = { updateState { it.copy(isLocked = false, voiceText = "잠금해제") } })
    } else {
        PortraitHudScreen(state = state, gestureModifier = gestureModifier, onUnlock = { updateState { it.copy(isLocked = false, voiceText = "잠금해제") } })
    }

}

@Composable
fun LockStatusRow(isLocked: Boolean, onUnlock: () -> Unit) {
    if (!isLocked) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Text(
            text = "🔒 잠금",
            fontSize = 14.sp,
            color = Color(0xFFFFD54F),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .border(BorderStroke(1.dp, Color(0xFFFFD54F)), RoundedCornerShape(6.dp))
                .clickable { onUnlock() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

fun sensoryLineCount(sensory: String): Int {
    if (sensory.isBlank() || sensory == "-") return 1
    return sensory.lines().filter { it.isNotBlank() }.size
}

fun sensoryFontSize(sensory: String, landscape: Boolean): Int {
    val count = sensoryLineCount(sensory)
    return if (landscape) {
        when {
            count <= 4 -> 17
            count <= 7 -> 15
            count <= 10 -> 13
            else -> 11
        }
    } else {
        when {
            count <= 3 -> 21
            count <= 6 -> 18
            count <= 9 -> 16
            else -> 14
        }
    }
}

fun sensoryLineHeight(sensory: String, landscape: Boolean): Int {
    return sensoryFontSize(sensory, landscape) + if (landscape) 4 else 6
}

@Composable
fun PortraitHudScreen(state: VehicleUiState, gestureModifier: Modifier, onUnlock: () -> Unit) {
    Column(
        modifier = gestureModifier
            .fillMaxSize()
            .background(Color.Black)
            .navigationBarsPadding()
            .padding(start = 26.dp, top = 30.dp, end = 22.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        LockStatusRow(isLocked = state.isLocked, onUnlock = onUnlock)
        InfoRow("차량번호", state.carNumber)
        InfoRow("차대번호", state.vin)
        InfoRow("주행거리", state.mileage)

        Spacer(modifier = Modifier.height(14.dp))

        InfoRow("타이어", state.tire)
        InfoRow("브레이크", state.brake)
        InfoRow("관능", state.sensory, valueFontSize = sensoryFontSize(state.sensory, false).sp, lineHeight = sensoryLineHeight(state.sensory, false).sp)

        val extraRows = buildExtraRows(state.bedSize, state.tuning, state.tuningCertifiedPart)
        if (extraRows.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "추가내역",
                fontSize = 20.sp,
                color = RokidGreen,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            extraRows.forEach { (title, value) ->
                Text(
                    text = title,
                    fontSize = 17.sp,
                    color = RokidGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = value,
                    fontSize = 19.sp,
                    color = RokidGreen,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "인식 : ${state.voiceText}",
            fontSize = 15.sp,
            color = RokidDimGreen,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "차량 ${state.currentIndex} / ${state.totalCount}",
            fontSize = 18.sp,
            color = RokidGreen,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun LandscapeHudScreen(state: VehicleUiState, gestureModifier: Modifier, onUnlock: () -> Unit) {
    val extraRows = buildExtraRows(state.bedSize, state.tuning, state.tuningCertifiedPart)
    val tireBrake = buildTireBrakeLine(state.tire, state.brake)

    Column(
        modifier = gestureModifier
            .fillMaxSize()
            .background(Color.Black)
            .navigationBarsPadding()
            .padding(start = 22.dp, top = 14.dp, end = 22.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        LockStatusRow(isLocked = state.isLocked, onUnlock = onUnlock)
        if (extraRows.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1.15f)) {
                    LandscapeInfoRow("차량번호", state.carNumber)
                    LandscapeInfoRow("차대번호", state.vin)
                    LandscapeInfoRow("주행거리", state.mileage)
                    LandscapeInfoRow("타이어,브레이크", tireBrake)
                }

                Column(
                    modifier = Modifier
                        .weight(0.85f)
                        .fillMaxHeight()
                        .border(BorderStroke(1.dp, RokidDimGreen), RoundedCornerShape(4.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "추가내역",
                        fontSize = 17.sp,
                        color = RokidGreen,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    extraRows.forEach { (title, value) ->
                        Text(
                            text = "$title : $value",
                            fontSize = 15.sp,
                            color = RokidGreen,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
            ) {
                LandscapeInfoRow("차량번호", state.carNumber)
                LandscapeInfoRow("차대번호", state.vin)
                LandscapeInfoRow("주행거리", state.mileage)
                LandscapeInfoRow("타이어,브레이크", tireBrake)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "관능",
            fontSize = 17.sp,
            color = RokidGreen,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = state.sensory,
            fontSize = sensoryFontSize(state.sensory, true).sp,
            lineHeight = sensoryLineHeight(state.sensory, true).sp,
            color = RokidGreen,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(BorderStroke(1.dp, RokidDimGreen), RoundedCornerShape(4.dp))
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "인식 : ${state.voiceText}",
                fontSize = 14.sp,
                color = RokidDimGreen,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "차량 ${state.currentIndex} / ${state.totalCount}",
                fontSize = 16.sp,
                color = RokidGreen,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun LandscapeInfoRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$title :",
            fontSize = 16.sp,
            color = RokidGreen,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(150.dp)
        )
        Text(
            text = value,
            fontSize = 18.sp,
            color = RokidGreen,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun buildTireBrakeLine(tire: String, brake: String): String {
    val tireText = if (tire == "-" || tire.isBlank()) "-" else tire.replace(",", "/").replace(" ", "")
    val brakeText = if (brake == "-" || brake.isBlank()) "-" else brake.replace(",", "/").replace(" ", "")
    return "$tireText   $brakeText"
}

@Composable
fun InfoRow(
    title: String,
    value: String,
    valueFontSize: androidx.compose.ui.unit.TextUnit = 21.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 28.sp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$title :",
            fontSize = 18.sp,
            color = RokidGreen,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(112.dp)
        )
        Text(
            text = value,
            fontSize = valueFontSize,
            color = RokidGreen,
            fontFamily = FontFamily.Monospace,
            lineHeight = lineHeight
        )
    }
}

@Composable
fun HudButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(52.dp)
            .border(BorderStroke(1.dp, if (enabled) RokidGreen else RokidDimGreen), RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black,
            contentColor = RokidGreen,
            disabledContainerColor = Color.Black,
            disabledContentColor = RokidDimGreen
        )
    ) {
        Text(text = text, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
    }
}

fun buildExtraRows(bedSize: String, tuning: String, tuningCertifiedPart: String): List<Pair<String, String>> {
    val rows = mutableListOf<Pair<String, String>>()
    if (bedSize.isNotBlank()) rows += "하대" to bedSize
    if (tuning.isNotBlank()) rows += "튜닝" to tuning
    if (tuningCertifiedPart.isNotBlank()) rows += "튜닝인증부품" to tuningCertifiedPart
    return rows
}

fun saveAliasCandidate(context: android.content.Context, rawText: String) {
    val compact = rawText.trim()
    if (compact.isBlank() || compact == "-") return
    val c = compactSpeech(compact)
    val suspicious = listOf("균열", "금열", "균렬", "누유", "부식", "손상", "미점등", "불량", "안나와", "안돼", "절손", "하차", "반사", "배기관", "소음기", "머플러")
        .any { c.contains(compactSpeech(it)) }
    if (!suspicious) return

    try {
        val dir = File(context.getExternalFilesDir(null), "InspectAR/voice_logs")
        if (!dir.exists()) dir.mkdirs()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        val file = File(dir, "alias_candidates_${date}.txt")
        file.appendText("[$time] $compact\n")
    } catch (_: Exception) {
    }
}

fun splitPossibleCommands(text: String): List<String> {
    return text
        .replace("그리고", " ")
        .replace("하고", " ")
        .replace("랑", " ")
        .replace("에다가", " ")
        .split("\n", ".", ",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .ifEmpty { listOf(text) }
}

fun normalizeSpeech(text: String): String {
    return text
        .replace(",", " ")
        .replace(".", " ")
        .replace("  ", " ")
        .replace("오유", "누유")
        .replace("노유", "누유")
        .replace("누  유", "누유")
        .replace("안켜짐", "미점등")
        .replace("안 켜짐", "미점등")
        .replace("안켜", "미점등")
        .replace("안 켜", "미점등")
        .replace("안들어옴", "미점등")
        .replace("안 들어옴", "미점등")
        .replace("불 안들어옴", "미점등")
        .replace("불 안 들어옴", "미점등")
        .replace("안나와", "미점등")
        .replace("안 나와", "미점등")
        .replace("안나옴", "미점등")
        .replace("안 나옴", "미점등")
        .replace("안보여", "미점등")
        .replace("안 보여", "미점등")
        .replace("나갔", "미점등")
        .replace("불량", "미점등")
        .replace("오른쪽", "우측")
        .replace("오른", "우측")
        .replace("우쪽", "우측")
        .replace("왼쪽", "좌측")
        .replace("왼", "좌측")
        .replace("좌쪽", "좌측")
        .replace("브레이크등", "제동등")
        .replace("브레끼등", "제동등")
        .replace("브레이끼등", "제동등")
        .replace("정지등", "제동등")
        .replace("스톱등", "제동등")
        .replace("스탑등", "제동등")
        .replace("헤드라이트", "전조등")
        .replace("헤드램프", "전조등")
        .replace("해드라이트", "전조등")
        .replace("전조 등", "전조등")
        .replace("전주 등", "전조등")
        .replace("라이트", "전조등")
        .replace("번호판등", "번호등")
        .replace("번호 등", "번호등")
        .replace("번호도", "번호등")
        .replace("번호 도", "번호등")
        .replace("판스크린", "판스프링")
        .replace("판스프리", "판스프링")
        .replace("판스필링", "판스프링")
        .replace("창열이", "창유리")
        .replace("창렬이", "창유리")
        .replace("갱거리", "주행거리")
        .replace("쟁거리", "주행거리")
        .replace("행거리", "주행거리")
        .replace("후미 등", "후미등")
        .replace("미 등", "미등")
        .replace("후퇴 등", "후퇴등")
        .replace("후진등", "후퇴등")
        .replace("백등", "후퇴등")
        .replace("반사질", "반사지")
        .replace("반사시", "반사지")
        .replace("반사제", "반사지")
        .replace("반사판", "반사지")
        .replace("후방", "후부")
        .replace("뒤", "후부")
        .replace("짐칸", "적재함")
        .replace("적재 함", "적재함")
        .trim()
}

fun compactSpeech(text: String): String {
    // 글자 단위 조사 제거를 하지 않는다.
    // 기존에는 "이"를 전부 지워서 "타이어"가 "타어"가 되어 타이어 명령이 안 잡혔다.
    return keywordSpeech(text)
}

fun keywordSpeech(text: String): String {
    return normalizeSpeech(text)
        .replace(" ", "")
        .replace("-", "")
        .replace("_", "")
        .replace("/", "")
        .replace(".", "")
        .replace(",", "")
        .replace("요", "")
}

fun isAnyCommand(compact: String, commands: List<String>): Boolean {
    return commands.any { compact.contains(compactSpeech(it)) }
}

fun extractMileage(text: String): String {
    val withoutLabel = text
        .replace("전주행거리", "")
        .replace("전 주행거리", "")
        .replace("주행거리", "")
        .replace("갱거리", "")
        .replace("쟁거리", "")
        .replace("행거리", "")
        .replace("주행", "")
        .replace("키로수", "")
        .replace("키로", "")
        .replace("킬로수", "")
        .replace("킬로미터", "")
        .replace("킬로", "")
        .replace(",", " ")

    val normalized = normalizeKoreanNumbersForMileage(withoutLabel)
    val groups = Regex("\\d+").findAll(normalized).map { it.value }.toList()
    if (groups.isEmpty()) return ""

    // 예: “주행거리 354 428” -> [354, 428] -> 354428
    // 예: “주행거리 444 437”처럼 같은 숫자가 겹쳐 들어오면 -> 44437
    val joined = joinMileageGroupsWithOverlap(groups)
    return joined.takeIf { it.length in 3..7 }.orEmpty()
}

fun joinMileageGroupsWithOverlap(groups: List<String>): String {
    if (groups.isEmpty()) return ""
    var result = groups.first()
    for (next in groups.drop(1)) {
        val maxOverlap = min(result.length, next.length)
        var overlap = 0
        for (size in maxOverlap downTo 1) {
            if (result.takeLast(size) == next.take(size)) {
                overlap = size
                break
            }
        }
        result += next.drop(overlap)
    }
    return result
}

fun bestMileageFromCandidates(texts: List<String>): String? {
    val values = texts.mapNotNull { text ->
        val value = extractMileage(text).ifEmpty { extractLooseDigits(text) }
        value.filter { it.isDigit() }.takeIf { it.isNotBlank() }
    }.distinct()

    if (values.isEmpty()) return null

    // 58271 / 582711처럼 끝 숫자가 한 번 더 붙은 후보가 같이 있으면 짧은 정상 후보를 우선한다.
    val deduped = values.filterNot { candidate ->
        values.any { base ->
            candidate.length == base.length + 1 &&
                candidate.startsWith(base) &&
                candidate.last() == base.last()
        }
    }.ifEmpty { values }

    // 주행거리는 보통 5~7자리이므로, 가능한 후보 중 가장 긴 정상 숫자를 선택한다.
    return deduped
        .filter { it.length in 4..7 }
        .maxByOrNull { it.length }
        ?: deduped.maxByOrNull { it.length }
}

fun bestVinDigitsFromCandidates(texts: List<String>): String? {
    val values = texts.mapNotNull { text ->
        extractLooseDigits(text)
            .filter { it.isDigit() }
            .takeIf { it.length >= 4 }
    }.distinct()

    if (values.isEmpty()) return null

    // 차대번호 끝자리/차량번호 뒷자리처럼 4자리만 말한 경우,
    // 부분인식에서 4442와 44442가 같이 들어오면 4442를 우선한다.
    val deduped = values.filterNot { candidate ->
        values.any { base ->
            candidate.length == base.length + 1 &&
                candidate.startsWith(base) &&
                candidate.last() == base.last()
        }
    }.ifEmpty { values }

    deduped.firstOrNull { it.length == 4 }?.let { return it }

    // 6자리 끝자리를 말한 경우는 6자리 우선.
    deduped.firstOrNull { it.length == 6 }?.let { return it }

    return deduped
        .filter { it.length in 4..6 }
        .minByOrNull { it.length }
        ?: deduped.first().takeLast(6)
}

fun isBareMileageCandidate(texts: List<String>): Boolean {
    val values = texts.map { extractLooseDigits(it).filter { ch -> ch.isDigit() } }.filter { it.isNotBlank() }
    if (values.isEmpty()) return false

    // "3337"처럼 음성엔진이 앞의 "주행거리"를 누락하고 숫자만 넘기는 경우를 보정한다.
    // 타이어/브레이크 모드에서는 이미 위쪽 분기에서 먼저 처리되므로 여기까지 내려온 숫자는 주행거리로 본다.
    return values.any { it.length in 4..7 }
}

fun bestBareMileageFromCandidates(texts: List<String>): String? {
    val values = texts.mapNotNull {
        extractLooseDigits(it).filter { ch -> ch.isDigit() }.takeIf { digits -> digits.length in 4..7 }
    }.distinct()

    if (values.isEmpty()) return null

    val deduped = values.filterNot { candidate ->
        values.any { base ->
            candidate.length == base.length + 1 &&
                candidate.startsWith(base) &&
                candidate.last() == base.last()
        }
    }.ifEmpty { values }

    return deduped.maxByOrNull { it.length }
}

fun normalizeKoreanNumbersForMileage(text: String): String {
    return extractLooseDigits(text)
}

fun formatMileageValue(value: String): String {
    val digits = value.replace(",", "").filter { it.isDigit() }
    return if (digits.isBlank()) "-" else "% ,d".replace(" ", "").format(digits.toLong()) + " km"
}

fun extractTireBrakeCommand(text: String): Pair<List<String>, List<String>> {
    val tokens = extractRawValues(text)
    val tireValues = mutableListOf<String>()
    val brakeValues = mutableListOf<String>()

    fun addTireDigit(digit: Char) {
        val value = digit.toString()
        if (value.toIntOrNull() in 1..9 && tireValues.size < 2) tireValues.add(value)
    }

    fun addBrake(value: String) {
        val normalized = normalizeBrakeCandidate(value)
        if (normalized.isNotBlank() && brakeValues.size < 2) {
            brakeValues.add(normalized)
        }
    }

    // 타이어 명령은 토큰 순서대로 해석한다.
    // 예: 타이어 562550     -> 타이어 5,6 / 브레이크 25%,50%
    // 예: 타이어 48 2550    -> 타이어 4,8 / 브레이크 25%,50%
    // 예: 타이어 88XX       -> 타이어 8,8 / 브레이크 X,X
    // 예: 타이어 사 팔 이오 오공 -> 타이어 4,8 / 브레이크 25%,50%
    for (token in tokens) {
        when {
            token.equals("X", ignoreCase = true) -> addBrake("X")
            token in listOf("25", "50", "75", "90") && tireValues.size >= 2 -> addBrake(token)
            token.all { it.isDigit() } -> {
                var buffer = ""
                var i = 0
                while (i < token.length) {
                    if (tireValues.size < 2 && token[i] in '1'..'9') {
                        addTireDigit(token[i])
                        i++
                    } else {
                        val remain = token.substring(i)

                        when {
                            remain.startsWith("51075") -> {
                                addBrake("50")
                                addBrake("75")
                                i += 5
                            }
                            remain.startsWith("5510") -> {
                                addBrake("50")
                                addBrake("50")
                                i += 4
                            }
                            remain.startsWith("575") -> {
                                addBrake("50")
                                addBrake("75")
                                i += 3
                            }
                            i + 1 < token.length -> {
                                addBrake(token.substring(i, i + 2))
                                i += 2
                            }
                            else -> i++
                        }
                    }
                }
            }
        }
    }

    if (tireValues.size >= 2 && brakeValues.size == 1) {
        brakeValues += brakeValues.first()
    }
    return tireValues.take(2) to brakeValues.take(2)
}

fun extractBrakeCommand(text: String): List<String> {
    val tokens = extractRawValues(text)
    val result = mutableListOf<String>()

    fun addBrake(value: String) {
        val normalized = normalizeBrakeCandidate(value)
        if (normalized.isNotBlank() && result.size < 2) {
            result.add(normalized)
        }
    }

    for (token in tokens) {
        when {
            token.equals("X", ignoreCase = true) -> addBrake("X")
            token in listOf("25", "50", "75", "90") -> addBrake(token)
            token.all { it.isDigit() } && token.length >= 2 -> {
                // 브레이크 2550 / 패드 25 50 / 브레이크 이오 오공 모두 25%, 50%로 처리
                var i = 0
                while (i + 1 < token.length && result.size < 2) {
                    addBrake(token.substring(i, i + 2))
                    i += 2
                }
            }
        }
    }

    return result.take(2)
}

fun extractRawValues(text: String): List<String> {
    val compact = text.replace(" ", "")
    val normalized = compact
        .replace("스물다섯", "25")
        .replace("스물넷", "24")
        .replace("스물셋", "23")
        .replace("스물둘", "22")
        .replace("스물하나", "21")
        .replace("이십오", "25")
        .replace("오십", "50")
        .replace("칠십오", "75")
        .replace("구십", "90")
        .replace("이십사", "24")
        .replace("이십삼", "23")
        .replace("이십이", "22")
        .replace("이십일", "21")
        .replace("이오", "25")
        .replace("오공", "50")
        .replace("칠오", "75")
        .replace("구공", "90")
        .replace("둘넷", "24")
        .replace("둘셋", "23")
        .replace("둘둘", "22")
        .replace("둘하나", "21")
        .replace("이사", "24")
        .replace("이삼", "23")
        .replace("이이", "22")
        .replace("이일", "21")
        .replace("쉰", "50")
        .replace("일흔다섯", "75")
        .replace("아흔", "90")
        .replace("더블엑스", "XX")
        .replace("엑스엑스", "XX")
        .replace("엑엑", "XX")
        .replace("엑스", "X")
        .replace("엑", "X")
        .replace("액스", "X")
        .replace("엑쓰", "X")
        .replace("곱하기", "X")
        .replace("곱", "X")
        .replace("안보임", "X")
        .replace("안 보임", "X")
        .replace("확인불가", "X")
        .replace("확인 불가", "X")
        .replace("공", "0")
        .replace("영", "0")
        .replace("제로", "0")
        .replace("하나", "1")
        .replace("한", "1")
        .replace("일", "1")
        .replace("둘", "2")
        .replace("두", "2")
        .replace("이", "2")
        .replace("셋", "3")
        .replace("세", "3")
        .replace("삼", "3")
        .replace("넷", "4")
        .replace("네", "4")
        .replace("사", "4")
        .replace("다섯", "5")
        .replace("오", "5")
        .replace("여섯", "6")
        .replace("육", "6")
        .replace("일곱", "7")
        .replace("칠", "7")
        .replace("여덟", "8")
        .replace("팔", "8")
        .replace("아홉", "9")
        .replace("구", "9")
        // 숫자를 한 글자씩 읽는 경우도 숫자로 변환한다.
        // 예: 일이삼사오육칠팔구십 -> 12345678910

    return Regex("X|x|25|50|75|90|\\d+")
        .findAll(normalized)
        .map { it.value.uppercase() }
        .toList()
}

fun extractLooseDigits(text: String): String {
    val tokens = Regex("[0-9]+|[가-힣]+").findAll(text).map { it.value }.toList()
    val result = StringBuilder()

    for (token in tokens) {
        if (token.all { it.isDigit() }) {
            result.append(token)
            continue
        }

        if (!containsKoreanNumberWord(token)) continue

        if (token.any { it in listOf('십', '백', '천', '만') }) {
            result.append(koreanNumberTokenToInt(token).toString())
        } else {
            result.append(koreanDigitSequence(token))
        }
    }
    return result.toString()
}

fun containsKoreanNumberWord(token: String): Boolean {
    val words = listOf("공", "영", "제로", "하나", "한", "일", "둘", "두", "이", "셋", "세", "삼", "넷", "네", "사", "다섯", "오", "여섯", "육", "일곱", "칠", "여덟", "팔", "아홉", "구", "십", "백", "천", "만")
    return words.any { token.contains(it) }
}

fun koreanDigitSequence(token: String): String {
    var s = token
    val replacements = listOf(
        "제로" to "0", "공" to "0", "영" to "0",
        "하나" to "1", "한" to "1", "일" to "1",
        "둘" to "2", "두" to "2", "이" to "2",
        "셋" to "3", "세" to "3", "삼" to "3",
        "넷" to "4", "네" to "4", "사" to "4",
        "다섯" to "5", "오" to "5",
        "여섯" to "6", "육" to "6",
        "일곱" to "7", "칠" to "7",
        "여덟" to "8", "팔" to "8",
        "아홉" to "9", "구" to "9"
    )
    replacements.forEach { (k, v) -> s = s.replace(k, v) }
    return s.filter { it.isDigit() }
}

fun koreanNumberTokenToInt(token: String): Int {
    val digitMap = mapOf(
        '일' to 1, '이' to 2, '삼' to 3, '사' to 4, '오' to 5,
        '육' to 6, '칠' to 7, '팔' to 8, '구' to 9,
        '한' to 1, '두' to 2, '세' to 3, '네' to 4
    )
    var total = 0
    var section = 0
    var current = 0

    for (ch in token) {
        when (ch) {
            in digitMap.keys -> current = digitMap[ch] ?: 0
            '십' -> { section += (if (current == 0) 1 else current) * 10; current = 0 }
            '백' -> { section += (if (current == 0) 1 else current) * 100; current = 0 }
            '천' -> { section += (if (current == 0) 1 else current) * 1000; current = 0 }
            '만' -> {
                section += current
                total += (if (section == 0) 1 else section) * 10000
                section = 0
                current = 0
            }
        }
    }
    return total + section + current
}

fun extractPlateLast4FromVoice(text: String): String {
    val digits = extractLooseDigits(text)
    return digits.takeLast(4).takeIf { it.length == 4 }.orEmpty()
}

fun extractPlateFromVoice(text: String): String {
    val compact = text
        .replace("차량번호", "")
        .replace("차량 번호", "")
        .replace("번호판", "")
        .replace("자동차번호", "")
        .replace("자동차 번호", "")
        .replace("차 번호", "")
        .replace("차번호", "")
        .replace(" ", "")
        .replace("-", "")

    Regex("\\d{2,3}[가-힣]\\d{4}").find(compact)?.let { return normalizePlateHangul(it.value) }

    val digits = extractLooseDigits(compact)
    val plateHangul = compact.map { it.toString() }.firstOrNull { isPlateHangul(it) }.orEmpty()

    if (digits.length >= 6 && plateHangul.isNotEmpty()) {
        val prefixLength = if (digits.length >= 7) 3 else 2
        val prefix = digits.take(prefixLength)
        val suffix = digits.drop(prefixLength).take(4)
        if ((prefix.length == 2 || prefix.length == 3) && suffix.length == 4) return "$prefix$plateHangul$suffix"
    }
    return ""
}

fun isPlateHangul(value: String): Boolean {
    return value in setOf(
        "가","나","다","라","마", "거","너","더","러","머","버","서","어","저",
        "고","노","도","로","모","보","소","오","조", "구","누","두","루","무","부","수","우","주",
        "바","사","아","자","배","허","하","호"
    )
}

fun normalizePlateHangul(value: String): String {
    return value
        .replace("니", "너")
        .replace("므", "머")
        .replace("으", "어")
        .replace("즈", "저")
        .replace("브", "버")
        .replace("스", "서")
        .replace("드", "더")
        .replace("르", "러")
}

fun normalizeBrakeCandidate(value: String): String {
    val normalized = value.uppercase()
    if (normalized == "X") return "X"
    if (normalized in listOf("25", "50", "75", "90")) return normalized

    val n = normalized.toIntOrNull() ?: return ""
    return when {
        n == 10 -> "50"
        n <= 37 -> "25"
        n <= 62 -> "50"
        n <= 82 -> "75"
        else -> "90"
    }
}

fun formatBrake(value: String): String {
    return if (value.uppercase() == "X") "X" else "${value}%"
}

fun addSensory(current: String, item: String): String {
    return if (current == "-") item
    else if (current.lines().contains(item)) current
    else "$current\n$item"
}

data class SensoryRule(val result: String, val keywords: List<String>)

val sensoryRules = listOf(
    SensoryRule("터보누유 (4437)", listOf("터보누유", "터보누유 (4437)", "터보 누유", "터보 오일", "터보 샘", "터보새", "터보 기름")),
    SensoryRule("원동기누유 (4431)", listOf("원동기누유", "원동기누유 (4431)", "엔진누유", "엔진 오일 샘", "엔진 기름", "엔진새", "원동기 오일")),
    SensoryRule("변속기누유 (4523)", listOf("변속기누유", "변속기누유 (4523)", "미션누유", "미션 오일", "미션 샘", "변속기 오일", "밋션누유")),
    SensoryRule("헤드누유 (4436)", listOf("헤드누유", "헤드누유 (4436)", "실린더헤드", "헤드 오일", "헤드 샘")),
    SensoryRule("접속부누유 (4528)", listOf("접속부누유", "접속부누유 (4528)", "접합부누유", "연결부누유", "호스 누유", "파이프 누유")),
    SensoryRule("오일팬누유 (4434)", listOf("오일팬누유", "오일팬누유 (4434)", "오일 팬 누유", "오일팬 샘", "오일 팬 샘")),
    SensoryRule("팬벨트균열 (4424)", listOf("팬벨트균열", "팬벨트균열 (4424)", "팬밸트균열", "팬 벨트 균열", "벨트균열", "밸트균열", "벨트 갈라짐", "벨트 터짐")),
    SensoryRule("창유리균열 (5724)", listOf("창유리균열", "창유리균열 (5724)", "유리균열", "앞유리균열", "창렬이","창열이", "전면유리균열", "유리 금감", "유리 금 갔", "유리 깨짐")),

    SensoryRule("우측 전조등 미점등 (5915)", listOf("우측전조등", "우측 전조등", "오른쪽전조등", "우측 라이트", "우측 헤드라이트")),
    SensoryRule("좌측 전조등 미점등 (5915)", listOf("좌측전조등", "좌측 전조등", "왼쪽전조등", "좌측 라이트", "좌측 헤드라이트")),
    SensoryRule("전조등 미점등 (5915)", listOf("전조등", "헤드라이트", "헤드램프", "라이트")),

    SensoryRule("우측 후미등 미점등 (5936)", listOf("우측후미등", "우측 후미등", "우측미등", "오른쪽 후미등", "오른쪽 미등")),
    SensoryRule("좌측 후미등 미점등 (5936)", listOf("좌측후미등", "좌측 후미등", "좌측미등", "왼쪽 후미등", "왼쪽 미등")),
    SensoryRule("후미등 미점등 (5936)", listOf("후미등", "미등", "테일램프", "테일 램프")),

    SensoryRule("우측 제동등 미점등 (5943)", listOf("우측제동등", "우측 제동등", "오른쪽 제동등", "우측 브레이크등", "오른쪽 브레이크등")),
    SensoryRule("좌측 제동등 미점등 (5943)", listOf("좌측제동등", "좌측 제동등", "왼쪽 제동등", "좌측 브레이크등", "왼쪽 브레이크등")),
    SensoryRule("제동등 미점등 (5943)", listOf("제동등", "브레이크등", "정지등", "스톱등", "스탑등")),

    SensoryRule("우측 후퇴등 미점등 (5939)", listOf("우측후퇴등", "우측 후퇴등", "오른쪽 후퇴등", "우측 후진등", "오른쪽 후진등", "우측 백등")),
    SensoryRule("좌측 후퇴등 미점등 (5939)", listOf("좌측후퇴등", "좌측 후퇴등", "왼쪽 후퇴등", "좌측 후진등", "왼쪽 후진등", "좌측 백등")),
    SensoryRule("후퇴등 미점등 (5939)", listOf("후퇴등", "후진등", "백등")),

    SensoryRule("우측 번호등 미점등 (5923)", listOf("우측번호등", "우측 번호등", "오른쪽 번호등", "우측 번호판등")),
    SensoryRule("좌측 번호등 미점등 (5923)", listOf("좌측번호등", "좌측 번호등", "왼쪽 번호등", "좌측 번호판등")),
    SensoryRule("번호등 미점등 (5923)", listOf("번호등", "번호판등", "번호도", "번호 도")),

    SensoryRule("우측 방향지시등 미점등 (5953)", listOf("우측방향지시등", "우측 방향지시등", "우측깜빡이", "오른쪽 깜빡이", "우측 지시등")),
    SensoryRule("좌측 방향지시등 미점등 (5953)", listOf("좌측방향지시등", "좌측 방향지시등", "좌측깜빡이", "왼쪽 깜빡이", "좌측 지시등")),
    SensoryRule("방향지시등 미점등 (5953)", listOf("방향지시등", "깜빡이", "지시등")),

    SensoryRule("우측 안개등 미점등 (5957)", listOf("우측안개등", "우측 안개등", "오른쪽 안개등", "우측 포그램프", "오른쪽 포그 램프")),
    SensoryRule("좌측 안개등 미점등 (5957)", listOf("좌측안개등", "좌측 안개등", "왼쪽 안개등", "좌측 포그램프", "왼쪽 포그 램프")),
    SensoryRule("안개등 미점등 (5957)", listOf("안개등", "포그램프", "포그 램프")),

    SensoryRule("우측 차폭등 미점등 (5933)", listOf("우측차폭등", "우측 차폭등", "오른쪽 차폭등", "우측 폭등")),
    SensoryRule("좌측 차폭등 미점등 (5933)", listOf("좌측차폭등", "좌측 차폭등", "왼쪽 차폭등", "좌측 폭등")),
    SensoryRule("차폭등 미점등 (5933)", listOf("차폭등", "폭등")),
    SensoryRule("워셔액 불량 (6132)", listOf("워셔", "워셔액", "워셔액 안나옴", "분사 안됨")),
    SensoryRule("와이퍼 불량 (6122)", listOf("와이퍼", "와이퍼 찢어짐", "와이퍼 불량", "와이퍼 불량 (6122)", "닦임 불량")),

    SensoryRule("후부반사지손상 (5983)", listOf("후부반사지손상", "후부반사지손상 (5983)", "후부 반사지 손상", "후부반사지 파손", "후부반사지 깨짐", "반사지손상", "반사지 파손", "반사지 깨짐", "후부 반사판 깨짐", "뒤 반사지 깨짐", "후방반사지손상")),
    SensoryRule("후부반사지설치상태 (5924)", listOf("후부반사지설치상태", "후부반사지설치상태 (5924)", "후부 반사지 설치상태", "후부반사지 없음", "반사지 없음", "반사지 설치 불량", "반사지 탈락", "후부반사지 탈락", "뒤 반사지 없음", "후방반사지 없음")),
    SensoryRule("적재함임의변경 (5631)", listOf("적재함임의변경", "적재함임의변경 (5631)", "적재함 임의 변경", "적재함 변경", "적재함 개조", "적재함 구조변경", "짐칸 개조", "짐칸 변경", "적재함 바뀜", "적재함 바꿈"))
)

fun findSensoryItems(texts: List<String>): List<String> {
    val found = linkedSetOf<String>()
    found.addAll(findSpecialCodedSensoryItems(texts))
    val normalizedTexts = texts.map { normalizeSpeech(it) }
    val compactTexts = normalizedTexts.map { compactSpeech(it) }

    for (rule in sensoryRules) {
        val matched = rule.keywords.any { keyword ->
            val k = compactSpeech(keyword)
            compactTexts.any { text ->
                if (hasDirectionConflict(text, k)) {
                    false
                } else if (isDirectionalKeyword(k)) {
                    // 좌측/우측이 들어간 등화류는 fuzzy match를 쓰면
                    // "좌측전조등"이 "우측전조등"과 1글자 차이라 둘 다 잡힌다.
                    // 그래서 방향성 키워드는 정확히 포함될 때만 인정한다.
                    text.contains(k)
                } else if (isStrictSensoryKeyword(k)) {
                    text.contains(k)
                } else {
                    text.contains(k) || isFuzzyMatch(text, k)
                }
            }
        }
        if (matched) found += rule.result
    }

    // 좌/우가 붙은 등화류가 잡혔으면 일반 항목은 빼서 중복 표시를 막는다.
    // 예: "왼쪽 라이트 안 나와" -> "좌측 전조등 미점등 (5915)"만 표시.
    suppressGenericLampDuplicates(found, "전조등 미점등 (5915)", listOf("좌측 전조등 미점등 (5915)", "우측 전조등 미점등 (5915)"))
    suppressGenericLampDuplicates(found, "후미등 미점등 (5936)", listOf("좌측 후미등 미점등 (5936)", "우측 후미등 미점등 (5936)"))
    suppressGenericLampDuplicates(found, "제동등 미점등 (5943)", listOf("좌측 제동등 미점등 (5943)", "우측 제동등 미점등 (5943)"))
    suppressGenericLampDuplicates(found, "후퇴등 미점등 (5939)", listOf("좌측 후퇴등 미점등 (5939)", "우측 후퇴등 미점등 (5939)"))
    suppressGenericLampDuplicates(found, "번호등 미점등 (5923)", listOf("좌측 번호등 미점등 (5923)", "우측 번호등 미점등 (5923)"))
    suppressGenericLampDuplicates(found, "방향지시등 미점등 (5953)", listOf("좌측 방향지시등 미점등 (5953)", "우측 방향지시등 미점등 (5953)"))
    suppressGenericLampDuplicates(found, "안개등 미점등 (5957)", listOf("좌측 안개등 미점등 (5957)", "우측 안개등 미점등 (5957)"))
    suppressGenericLampDuplicates(found, "차폭등 미점등 (5933)", listOf("좌측 차폭등 미점등 (5933)", "우측 차폭등 미점등 (5933)"))

    return found.toList()
}



fun findSpecialCodedSensoryItems(texts: List<String>): List<String> {
    val found = linkedSetOf<String>()
    val compactTexts = texts.map { compactSpeech(it) }

    fun containsAny(text: String, keywords: List<String>) = keywords.any { text.contains(compactSpeech(it)) }

    for (text in compactTexts) {
        // 어린이 하차확인장치: 현장에서는 하차벨이라고 자주 부름
        if (containsAny(text, listOf("하차벨", "어린이하차벨", "하차확인장치", "어린이하차확인장치"))) {
            found += "어린이 하차확인장치 미설치/설치상태 불량 (6041)"
        }

        // 추진축/플렉시블 조인트: 사용자가 현장에서 부르는 표현을 공식 코드로 연결
        if (containsAny(text, listOf("프로펠라샤프트", "프로펠러샤프트", "추진축", "플랙시블조인트", "플렉시블조인트", "플랙시블", "플렉시블"))) {
            found += "추진축등 연결부(조인트) 체결상태 (4541)"
        }

        // 경음기 작동불: 반드시 경음기/혼/크락션/빵빵이 계열 단어와 불량 표현이 함께 있을 때만 추가한다.
        // 예: "번호등 안 나와"가 "경음기 작동불"로 같이 잡히는 오인식을 막기 위함.
        val hasHornWord = containsAny(text, listOf("경음기", "혼", "크락션", "클락션", "빵", "빵빵이"))
        val hasHornFail = containsAny(text, listOf("작동불", "불량", "안돼", "안되", "안나와", "안나옴", "미점등", "소리안남", "소리미점등"))
        if (text.contains("경음기작동불") || (hasHornWord && hasHornFail)) {
            found += "경음기 작동불 (7013)"
        }

        // 1~4번 등속조인트 부트 손상
        if (containsAny(text, listOf("부트손상", "부트찢", "부트터짐", "조인트부트", "등속조인트부트", "등속부트"))) {
            val numberMap = listOf(
                listOf("1번", "일번", "첫번째", "첫번") to "1번",
                listOf("2번", "이번", "두번째", "둘째") to "2번",
                listOf("3번", "삼번", "세번째", "셋째") to "3번",
                listOf("4번", "사번", "네번째", "넷째") to "4번"
            )
            val number = numberMap.firstOrNull { (keys, _) -> containsAny(text, keys) }?.second
            if (number != null) found += "$number 등속조인트 부트 손상 (452H)"
            else found += "등속조인트 부트 손상 (452H)"
        }

        // 배기관/소음기 부식: 코드표 기준으로 부식 코드와 5816을 함께 추가
        if (containsAny(text, listOf("배기관부식", "배기관손상", "배기관구멍", "배기관녹", "배기관썩"))) {
            found += "배기관 부식(손상) (5812)"
            found += "최종배출구 이전 배출가스 유출 (5816)"
        }
        if (containsAny(text, listOf("소음기부식", "소음기손상", "머플러부식", "머플러손상", "소음기녹", "머플러녹"))) {
            found += "소음기 부식(손상) (5822)"
            found += "최종배출구 이전 배출가스 유출 (5816)"
        }
        if (containsAny(text, listOf("배기누설", "배출가스유출", "배기가스유출", "배기샘", "배기가스샘"))) {
            found += "최종배출구 이전 배출가스 유출 (5816)"
        }

        // 판스프링 절손/균열: 전좌/전우/후좌/후우 위치를 붙여 표시
        if (containsAny(text, listOf("판스프링", "판스프리", "판스크린", "판스필링", "스프링")) && containsAny(text, listOf("절손", "균열", "금감", "갈라짐", "갈라진", "부러짐", "부러진", "금열", "균렬"))) {
            val pos = when {
                containsAny(text, listOf("전좌", "앞좌", "앞왼쪽", "전방좌측", "앞쪽좌측")) -> "전좌"
                containsAny(text, listOf("전우", "앞우", "앞오른쪽", "전방우측", "앞쪽우측")) -> "전우"
                containsAny(text, listOf("후좌", "뒤좌", "뒤왼쪽", "후방좌측", "뒤쪽좌측")) -> "후좌"
                containsAny(text, listOf("후우", "뒤우", "뒤오른쪽", "후방우측", "뒤쪽우측")) -> "후우"
                else -> ""
            }
            val damage = if (containsAny(text, listOf("균열", "금감", "갈라짐", "갈라진"))) "균열" else "절손"
            found += listOf(pos, "판스프링", damage).filter { it.isNotBlank() }.joinToString(" ") + " (5013)"
        }
    }

    return found.toList()
}

fun isStrictSensoryKeyword(keyword: String): Boolean {
    return keyword.contains("창유리") ||
        keyword.contains("유리") ||
        keyword.contains("판스프링") ||
        keyword.contains("판스프리") ||
        keyword.contains("판스크린") ||
        keyword.contains("균열")
}

fun isDirectionalKeyword(keyword: String): Boolean {
    return keyword.contains("좌측") || keyword.contains("우측") ||
        keyword.contains("왼쪽") || keyword.contains("오른쪽") ||
        keyword.contains("왼") || keyword.contains("오른")
}

fun hasDirectionConflict(text: String, keyword: String): Boolean {
    val textLeft = text.contains("좌측") || text.contains("왼쪽") || text.contains("왼")
    val textRight = text.contains("우측") || text.contains("오른쪽") || text.contains("오른")
    val keyLeft = keyword.contains("좌측") || keyword.contains("왼쪽") || keyword.contains("왼")
    val keyRight = keyword.contains("우측") || keyword.contains("오른쪽") || keyword.contains("오른")

    return (textLeft && keyRight) || (textRight && keyLeft)
}

fun suppressGenericLampDuplicates(found: LinkedHashSet<String>, generic: String, specifics: List<String>) {
    if (specifics.any { found.contains(it) }) found.remove(generic)
}

fun isFuzzyMatch(text: String, keyword: String): Boolean {
    if (keyword.length < 4) return false
    if (text.contains(keyword)) return true
    val maxDistance = if (keyword.length <= 6) 1 else 2
    val windows = mutableListOf<String>()
    val len = keyword.length
    for (size in (len - 1)..(len + 1)) {
        if (size <= 0 || text.length < size) continue
        for (i in 0..(text.length - size)) windows += text.substring(i, i + size)
    }
    return windows.any { levenshtein(it, keyword) <= maxDistance }
}

fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val prev = IntArray(b.length + 1) { it }
    val curr = IntArray(b.length + 1)
    for (i in a.indices) {
        curr[0] = i + 1
        for (j in b.indices) {
            val cost = if (a[i] == b[j]) 0 else 1
            curr[j + 1] = min(min(curr[j] + 1, prev[j + 1] + 1), prev[j] + cost)
        }
        for (j in prev.indices) prev[j] = curr[j]
    }
    return prev[b.length]
}
