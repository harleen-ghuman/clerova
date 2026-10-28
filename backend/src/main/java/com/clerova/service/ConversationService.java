package com.clerova.service;

import com.clerova.domain.InboundMessage;
import com.clerova.domain.OutboundMessage;
import com.clerova.domain.WorkItem;
import com.clerova.domain.WorkItemSource;
import com.clerova.dto.ConversationEntryResponse;
import com.clerova.repository.InboundMessageRepository;
import com.clerova.repository.OutboundMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The email conversation behind a work item: received messages and the
 * replies sent from Clerova, oldest first.
 */
@Service
public class ConversationService {

    private static final int MAX_AI_CONTEXT_CHARS = 12_000;
    private static final int MAX_AI_MESSAGE_CHARS = 4_000;

    private final InboundMessageRepository inboundMessageRepository;
    private final OutboundMessageRepository outboundMessageRepository;

    public ConversationService(
            InboundMessageRepository inboundMessageRepository,
            OutboundMessageRepository outboundMessageRepository
    ) {
        this.inboundMessageRepository = inboundMessageRepository;
        this.outboundMessageRepository = outboundMessageRepository;
    }

    @Transactional(readOnly = true)
    public List<ConversationEntryResponse> getConversation(WorkItem workItem) {
        List<ConversationEntryResponse> entries = new ArrayList<>();

        for (InboundMessage message :
                inboundMessageRepository.findByWorkItemIdOrderByReceivedAtAsc(workItem.getId())) {
            entries.add(ConversationEntryResponse.from(message));
        }

        for (OutboundMessage message :
                outboundMessageRepository.findByWorkItemIdOrderBySentAtAsc(workItem.getId())) {
            entries.add(ConversationEntryResponse.from(message));
        }

        entries.sort(Comparator.comparing(ConversationEntryResponse::at));

        return entries;
    }

    /**
     * Conversation text for the AI when there is more than the original
     * message, or null. Keeps the most recent messages if it is long.
     */
    @Transactional(readOnly = true)
    public String conversationForAi(WorkItem workItem) {
        if (workItem.getSource() != WorkItemSource.EMAIL) {
            return null;
        }

        List<ConversationEntryResponse> entries = getConversation(workItem);

        if (entries.size() <= 1) {
            return null;
        }

        List<String> parts = new ArrayList<>();
        int length = 0;

        for (int i = entries.size() - 1; i >= 0; i--) {
            ConversationEntryResponse entry = entries.get(i);

            // One very long email mustn't crowd out the rest of the thread
            String text = entry.text() != null && entry.text().length() > MAX_AI_MESSAGE_CHARS
                    ? entry.text().substring(0, MAX_AI_MESSAGE_CHARS) + "\n[...]"
                    : entry.text();

            String part = (entry.direction() == ConversationEntryResponse.Direction.INBOUND
                    ? "Message from " + entry.from()
                    : "Reply sent to " + entry.to())
                    + " at " + entry.at() + ":\n" + text;

            if (length + part.length() > MAX_AI_CONTEXT_CHARS && !parts.isEmpty()) {
                break;
            }

            parts.add(0, part);
            length += part.length();
        }

        return String.join("\n\n---\n\n", parts);
    }
}
