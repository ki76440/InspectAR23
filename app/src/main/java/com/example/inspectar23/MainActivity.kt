package com.example.inspectar23

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.inspectar23.ui.theme.InspectAR23Theme

class MainActivity : ComponentActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private val handler = Handler(Looper.getMainLooper())
    private var updateText: ((String) -> Unit)? = null
    private var isListening = false

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startListeningDelayed()
            } else {
                updateText?.invoke("마이크 권한 필요")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        setContent {
            InspectAR23Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    InspectARApp { handler ->
                        updateText = handler
                    }
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

        if (needAudio) {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startListeningDelayed()
        }
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
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 180L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 120L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 200L)
        }

        speechRecognizer.setRecognitionListener(
            SimpleRecognitionListener(
                onResult = { text ->
                    isListening = false
                    updateText?.invoke(text)
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        speechRecognizer.destroy()
    }
}

@Composable
fun InspectARApp(onReady: ((String) -> Unit) -> Unit) {
    var carNumber by remember { mutableStateOf("-") }
    var vin by remember { mutableStateOf("-") }
    var mileage by remember { mutableStateOf("-") }
    var tire by remember { mutableStateOf("-") }
    var brake by remember { mutableStateOf("-") }
    var sensory by remember { mutableStateOf("-") }
    var voiceText by remember { mutableStateOf("듣는 중...") }
    var statusText by remember { mutableStateOf("V3.1 음성 체크리스트") }

    LaunchedEffect(Unit) {
        onReady { rawText ->
            val text = normalizeSpeech(rawText)
            voiceText = text

            when {
                text.contains("초기화") || text.contains("리셋") -> {
                    carNumber = "-"
                    vin = "-"
                    mileage = "-"
                    tire = "-"
                    brake = "-"
                    sensory = "-"
                    statusText = "초기화 완료"
                    voiceText = "초기화 완료"
                }

                text.contains("동기화") -> {
                    statusText = "PDF 동기화는 다음 단계에서 연결"
                    voiceText = "동기화 명령 확인"
                }

                text.contains("다음 차량") || text.contains("다음차량") -> {
                    statusText = "다음 차량은 PDF 목록 연결 후 작동"
                    voiceText = "다음 차량"
                }

                text.contains("이전 차량") || text.contains("이전차량") -> {
                    statusText = "이전 차량은 PDF 목록 연결 후 작동"
                    voiceText = "이전 차량"
                }

                text.replace(" ", "").contains("차량번호") ||
                        text.contains("번호판") ||
                        text.replace(" ", "").contains("차번호") -> {
                    val value = extractPlateFromVoice(text)
                    if (value.isNotEmpty()) {
                        carNumber = value
                        voiceText = "차량번호 입력: $value"
                    } else {
                        voiceText = "차량번호 인식 실패: $text"
                    }
                }

                text.contains("차대번호") || text.contains("일련번호") || text.contains("끝자리") -> {
                    val value = extractLooseDigits(text)
                    if (value.length >= 4) {
                        vin = value.takeLast(6)
                        voiceText = "차대번호 입력: ${value.takeLast(6)}"
                    }
                }

                text.contains("주행거리") ||
                    text.contains("키로수") ||
                    text.contains("키로") ||
                    text.contains("주행") -> {
                    val value = extractMileage(text).ifEmpty { extractLooseDigits(text) }
                    if (value.isNotEmpty()) {
                        mileage = value
                        voiceText = "주행거리 입력: $value"
                    }
                }

                text.contains("타이어") -> {
                    val command = text.substringAfter("타이어")
                    val (tireValues, brakeValues) = extractTireBrakeCommand(command)

                    if (tireValues.size >= 2) {
                        tire = "${tireValues[0]}, ${tireValues[1]}"
                    }

                    if (brakeValues.size >= 2) {
                        brake = "${formatBrake(brakeValues[0])}, ${formatBrake(brakeValues[1])}"
                    }

                    if (tireValues.size >= 2 || brakeValues.size >= 2) {
                        voiceText = "타이어/브레이크 입력 완료"
                    }
                }

                text.contains("브레이크") || text.contains("브레크") || text.contains("패드") -> {
                    val command = text
                        .substringAfter("브레이크", text)
                        .substringAfter("브레크", text)
                        .substringAfter("패드", text)

                    val values = extractBrakeCommand(command)
                    if (values.size >= 2) {
                        brake = "${formatBrake(values[0])}, ${formatBrake(values[1])}"
                        voiceText = "브레이크 입력 완료"
                    }
                }

                text.contains("관능") && (text.contains("양호") || text.contains("정상")) -> {
                    sensory = "-"
                    voiceText = "관능 정상"
                }

                else -> {
                    val sensoryResult = findSensoryItem(text)
                    if (sensoryResult != null) {
                        sensory = addSensory(sensory, sensoryResult)
                        voiceText = "관능 입력: $sensoryResult"
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(start = 28.dp, top = 38.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text("InspectAR23", fontSize = 30.sp, color = Color.White)

        Spacer(modifier = Modifier.height(24.dp))

        InfoRow("차량번호", carNumber)
        InfoRow("차대번호", vin)
        InfoRow("주행거리", mileage)
        InfoRow("타이어", tire)
        InfoRow("브레이크", brake)
        InfoRow("관능", sensory)

        Spacer(modifier = Modifier.height(12.dp))

        Text(statusText, fontSize = 15.sp, color = Color.LightGray)

        Spacer(modifier = Modifier.height(18.dp))

        Text("🎤 $voiceText", fontSize = 20.sp, color = Color.White)

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = {
                statusText = "PDF 동기화는 다음 단계에서 연결"
                voiceText = "동기화 버튼"
            }
        ) {
            Text("PDF 동기화")
        }
    }
}

@Composable
fun InfoRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            color = Color.White,
            modifier = Modifier.width(100.dp)
        )

        Text(
            text = value,
            fontSize = 22.sp,
            color = Color.White
        )
    }
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
        .replace("불량", "미점등")
        .replace("오른쪽", "우측")
        .replace("오른", "우측")
        .replace("우쪽", "우측")
        .replace("왼쪽", "좌측")
        .replace("왼", "좌측")
        .replace("좌쪽", "좌측")
        .replace("브레이크등", "제동등")
        .replace("브레끼등", "제동등")
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
        .replace("후미 등", "후미등")
        .replace("미 등", "미등")
        .replace("후퇴 등", "후퇴등")
        .replace("후진등", "후퇴등")
        .replace("백등", "후퇴등")
        .trim()
}

fun extractMileage(text: String): String {
    val normalized = text
        .replace("주행거리", "")
        .replace("주행", "")
        .replace("키로수", "")
        .replace("키로", "")
        .replace("킬로수", "")
        .replace("킬로", "")
        .replace(",", "")
        .replace(" ", "")

    return Regex("\\d{1,7}").find(normalized)?.value ?: ""
}

fun extractTireBrakeCommand(text: String): Pair<List<String>, List<String>> {
    val tokens = extractRawValues(text)
    val tireValues = mutableListOf<String>()
    val brakeValues = mutableListOf<String>()

    for (token in tokens) {
        when {
            token == "X" || token in listOf("25", "50", "75", "90") -> brakeValues.add(token)
            token.length == 2 && token.all { it.isDigit() } -> {
                token.forEach { digit ->
                    val value = digit.toString()
                    if (value.toIntOrNull() in 1..9 && tireValues.size < 2) tireValues.add(value)
                }
            }
            token.toIntOrNull() in 1..9 -> if (tireValues.size < 2) tireValues.add(token)
        }
    }

    return tireValues.take(2) to brakeValues.take(2)
}

fun extractBrakeCommand(text: String): List<String> {
    return extractRawValues(text)
        .filter { it == "X" || it in listOf("25", "50", "75", "90") }
        .take(2)
}

fun extractRawValues(text: String): List<String> {
    val normalized = text
        .replace("이십오", "25")
        .replace("오십", "50")
        .replace("칠십오", "75")
        .replace("구십", "90")
        .replace("스물다섯", "25")
        .replace("쉰", "50")
        .replace("일흔다섯", "75")
        .replace("아흔", "90")
        .replace("엑스", "X")
        .replace("액스", "X")
        .replace("안보임", "X")
        .replace("안 보임", "X")
        .replace("확인불가", "X")
        .replace("확인 불가", "X")
        .replace("오", "5")
        .replace("육", "6")
        .replace("칠", "7")
        .replace("팔", "8")
        .replace("구", "9")

    return Regex("X|x|25|50|75|90|\\d+")
        .findAll(normalized)
        .map { it.value.uppercase() }
        .toList()
}

fun extractLooseDigits(text: String): String {
    var normalized = text

    val replacements = listOf(
        "공" to "0", "영" to "0", "제로" to "0",
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

    replacements.forEach { (k, v) ->
        normalized = normalized.replace(k, v)
    }

    return Regex("\\d+").findAll(normalized).joinToString("") { it.value }
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

    // 예: 147더7326, 51조2581
    Regex("\\d{2,3}[가-힣]\\d{4}")
        .find(compact)
        ?.let { return normalizePlateHangul(it.value) }

    // 예: 147 더 7326 / 일사칠 더 칠삼이육
    val digits = extractLooseDigits(compact)
    val plateHangul = compact
        .map { it.toString() }
        .firstOrNull { isPlateHangul(it) }
        .orEmpty()

    if (digits.length >= 6 && plateHangul.isNotEmpty()) {
        val prefixLength = if (digits.length >= 7) 3 else 2
        val prefix = digits.take(prefixLength)
        val suffix = digits.drop(prefixLength).take(4)

        if ((prefix.length == 2 || prefix.length == 3) && suffix.length == 4) {
            return "$prefix$plateHangul$suffix"
        }
    }

    return ""
}

fun isPlateHangul(value: String): Boolean {
    return value in setOf(
        "가","나","다","라","마",
        "거","너","더","러","머","버","서","어","저",
        "고","노","도","로","모","보","소","오","조",
        "구","누","두","루","무","부","수","우","주",
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

fun formatBrake(value: String): String {
    return if (value.uppercase() == "X") "X" else "${value}%"
}

fun addSensory(current: String, item: String): String {
    return if (current == "-") item
    else if (current.contains(item)) current
    else "$current\n$item"
}

data class SensoryRule(
    val result: String,
    val keywords: List<String>
)

val sensoryRules = listOf(
    SensoryRule("터보누유", listOf("터보")),
    SensoryRule("원동기누유", listOf("원동기", "엔진")),
    SensoryRule("변속기누유", listOf("미션", "변속기")),
    SensoryRule("헤드누유", listOf("헤드")),
    SensoryRule("접속부누유", listOf("접속부", "접합부", "연결부")),
    SensoryRule("팬벨트균열", listOf("팬벨트", "팬밸트", "팬 벨트", "팬 밸트", "벨트균열", "밸트균열")),
    SensoryRule("창유리균열", listOf("창유리", "유리균열", "앞유리", "전면유리")),
    SensoryRule("오일팬누유", listOf("오일팬", "오일 팬")),

    SensoryRule("우측 전조등 미점등", listOf("우측 전조등", "우측전조등")),
    SensoryRule("좌측 전조등 미점등", listOf("좌측 전조등", "좌측전조등")),
    SensoryRule("전조등 미점등", listOf("전조등")),

    SensoryRule("우측 후미등 미점등", listOf("우측 후미등", "우측후미등", "우측 미등", "우측미등")),
    SensoryRule("좌측 후미등 미점등", listOf("좌측 후미등", "좌측후미등", "좌측 미등", "좌측미등")),
    SensoryRule("후미등 미점등", listOf("후미등", "미등")),

    SensoryRule("우측 제동등 미점등", listOf("우측 제동등", "우측제동등")),
    SensoryRule("좌측 제동등 미점등", listOf("좌측 제동등", "좌측제동등")),
    SensoryRule("제동등 미점등", listOf("제동등")),

    SensoryRule("우측 후퇴등 미점등", listOf("우측 후퇴등", "우측후퇴등")),
    SensoryRule("좌측 후퇴등 미점등", listOf("좌측 후퇴등", "좌측후퇴등")),
    SensoryRule("후퇴등 미점등", listOf("후퇴등")),

    SensoryRule("우측 번호등 미점등", listOf("우측 번호등", "우측번호등")),
    SensoryRule("좌측 번호등 미점등", listOf("좌측 번호등", "좌측번호등")),
    SensoryRule("번호등 미점등", listOf("번호등")),

    SensoryRule("우측 방향지시등 미점등", listOf("우측 방향지시등", "우측방향지시등", "우측 깜빡이", "우측깜빡이")),
    SensoryRule("좌측 방향지시등 미점등", listOf("좌측 방향지시등", "좌측방향지시등", "좌측 깜빡이", "좌측깜빡이")),
    SensoryRule("방향지시등 미점등", listOf("방향지시등", "깜빡이")),

    SensoryRule("안개등 미점등", listOf("안개등")),
    SensoryRule("경음기 불량", listOf("경음기", "혼", "크락션")),
    SensoryRule("워셔액 불량", listOf("워셔", "워셔액")),
    SensoryRule("와이퍼 불량", listOf("와이퍼"))
)

fun findSensoryItem(text: String): String? {
    val normalized = normalizeSpeech(text)
    return sensoryRules.firstOrNull { rule ->
        rule.keywords.any { keyword -> normalized.contains(keyword) }
    }?.result
}
