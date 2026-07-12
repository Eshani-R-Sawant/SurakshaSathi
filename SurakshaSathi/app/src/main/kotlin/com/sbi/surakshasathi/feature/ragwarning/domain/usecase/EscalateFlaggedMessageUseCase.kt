package com.sbi.surakshasathi.feature.ragwarning.domain.usecase

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.onSuccess
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagWarning
import com.sbi.surakshasathi.feature.ragwarning.domain.notifier.RagWarningDispatcher
import javax.inject.Inject

/**
 * Escalates a SUSPICIOUS/MALICIOUS [Message] to the RAG agent and surfaces
 * the result to the user (§4b). This is the handoff point between Flow 1
 * (classification) and Flow 1b (RAG warning) — called by
 * [com.sbi.surakshasathi.feature.messagescan.domain.usecase.ProcessIncomingMessageUseCase]
 * right after classification flags a message.
 *
 * Idempotent: [Message.ragEscalated] guards against double-escalation
 * (e.g. across process death / notification-listener restarts).
 */
class EscalateFlaggedMessageUseCase
    @Inject
    constructor(
        private val analyzeMessageWithRagUseCase: AnalyzeMessageWithRagUseCase,
        private val messageRepository: MessageRepository,
        private val ragWarningDispatcher: RagWarningDispatcher,
    ) {
        suspend operator fun invoke(message: Message): Result<RagWarning> {
            if (message.ragEscalated) {
                return Result.Error(com.sbi.surakshasathi.core.common.AppError.UnknownError("Already escalated"))
            }

            return analyzeMessageWithRagUseCase(message).onSuccess { warning ->
                messageRepository.saveRagWarning(message.id, warning.warning, warning.guideline)
                ragWarningDispatcher.notify(message, warning)
            }
        }
    }
