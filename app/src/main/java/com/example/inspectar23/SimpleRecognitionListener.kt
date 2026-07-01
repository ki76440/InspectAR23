package com.example.inspectar23

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer

class SimpleRecognitionListener(
    private val onResult: (List<String>) -> Unit,
    private val onErrorRestart: () -> Unit
) : RecognitionListener {

    private var lastPartial = ""
    private var lastPartialAt = 0L

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        if (lastPartial.isNotBlank()) {
            onResult(listOf(lastPartial))
            lastPartial = ""
        }
    }

    override fun onError(error: Int) {
        onErrorRestart()
    }

    override fun onResults(results: Bundle?) {
        val matches = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.filter { it.isNotBlank() }
            .orEmpty()

        if (matches.isNotEmpty()) {
            lastPartial = ""
            onResult(matches)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val useful = matches.filter {
            looksLikeFastNumericCommand(it) || looksLikeMileageCommandReady(it) || looksLikeBareNumericValue(it) || looksLikeInspectionCommand(it)
        }
        val signature = useful.joinToString("|").replace(" ", "")
        val now = System.currentTimeMillis()

        if (useful.isNotEmpty() && signature != lastPartial && now - lastPartialAt > 25L) {
            lastPartial = signature
            lastPartialAt = now
            onResult(useful)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}

private fun looksLikeInspectionCommand(text: String): Boolean {
    val c = text.replace(" ", "")
    val triggers = listOf(
        "타이어", "타야", "타이야", "타이어라고", "브레이크", "브레크", "브레이끼", "브레끼", "패드",
        "차대번호", "일련번호", "끝자리",
        "차량번호", "차량 번호", "차번호", "자동차번호",
        "초기화", "리셋", "동기화", "새로고침", "갱신", "싱크", "정지", "중지", "일시정지", "잠금", "화면잠금", "시작", "다시", "다시시작", "잠금해제",
        "차량불러와", "차량 불러와", "차량불러오기", "차량 불러오기", "차불러와", "불러와",
        "다음차", "다음차량", "이전차", "이전차량", "뒤로",
        "관능", "전조등", "제동등", "후미등",
        "방향지시등", "깜빡이", "후퇴등", "후진등", "번호등", "안개등", "차폭등",
        "경음기", "혼", "크락션", "빵", "빵빵이", "와이퍼", "워셔", "누유", "균열", "손상",
        "반사지", "반사판", "적재함", "짐칸", "개조", "변경", "임의변경",
        "하차벨", "하차확인장치", "판스프링", "스프링", "부트", "등속조인트",
        "프로펠라샤프트", "프로펠러샤프트", "플랙시블", "플렉시블", "조인트", "창유리", "창열이", "판스프리", "판스크린", "배기관", "소음기", "머플러", "배기누설", "배출가스"
    )
    return triggers.any { c.contains(it.replace(" ", "")) }
}

private fun looksLikeMileageCommandReady(text: String): Boolean {
    val c = text.replace(" ", "")
    val hasMileageTarget = c.contains("주행") || c.contains("키로") || c.contains("킬로") || c.contains("갱거리") || c.contains("쟁거리") || c.contains("행거리")
    if (!hasMileageTarget) return false

    val digits = Regex("\\d+").findAll(c).joinToString("") { it.value }
    // 주행거리 354 428 같은 경우 354만 먼저 들어오는 것을 막고,
    // 5~7자리 정도가 잡혔을 때만 부분결과로 즉시 반영한다.
    return digits.length >= 5
}

private fun looksLikeBareNumericValue(text: String): Boolean {
    val c = text.replace(" ", "")
    if (c.length > 12) return false
    return Regex("^([0-9]{1,6}|엑스|액스|엑쓰|곱하기|안보임|확인불가|일|이|삼|사|오|육|칠|팔|구|하나|둘|셋|넷|네|스물넷|이십사|이사|둘넷|오십|칠십오|구십|백|천|만|십)+$").matches(c)
}

private fun looksLikeFastNumericCommand(text: String): Boolean {
    val c = text.replace(" ", "")
    val hasTarget = c.contains("타이어") || c.contains("타야") || c.contains("타이야") ||
        c.contains("브레이크") || c.contains("브레크") || c.contains("브레끼") || c.contains("패드")
    if (!hasTarget) return false

    val hasNumberOrX = Regex("\\d|엑스|액스|엑쓰|곱하기|안보임|확인불가|일|이|삼|사|오|육|칠|팔|구|하나|둘|셋|넷|스물").containsMatchIn(c)
    return hasNumberOrX
}
