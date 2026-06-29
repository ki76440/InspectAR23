package com.example.inspectar23

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer

class SimpleRecognitionListener(
    private val onResult: (String) -> Unit,
    private val onErrorRestart: () -> Unit
) : RecognitionListener {

    private var lastPartial = ""

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        if (lastPartial.isNotBlank()) {
            onResult(lastPartial)
            lastPartial = ""
        }
    }

    override fun onError(error: Int) {
        onErrorRestart()
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull().orEmpty()
        if (text.isNotBlank()) {
            lastPartial = ""
            onResult(text)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull().orEmpty()
        if (text.isNotBlank() && text != lastPartial) {
            lastPartial = text

            if (
                text.contains("타이어") ||
                text.contains("브레이크") ||
                text.contains("브레크") ||
                text.contains("패드") ||
                text.contains("주행") ||
                text.contains("키로") ||
                text.contains("차대번호") ||
                text.contains("일련번호") ||
                text.contains("차량번호") ||
                text.contains("번호판") ||
                text.contains("초기화") ||
                text.contains("리셋") ||
                text.contains("동기화") ||
                text.contains("다음 차량") ||
                text.contains("다음차량") ||
                text.contains("이전 차량") ||
                text.contains("이전차량")
            ) {
                onResult(text)
            }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
