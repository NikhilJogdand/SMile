package com.fsck.k9.helper;

import android.content.Context;

import com.fsck.k9.activity.MessageReference;
import com.fsck.k9.mailstore.LocalMessage;

import java.util.LinkedList;
import java.util.List;

/**
 * A holder class for pending notification data
 *
 * This class holds all pieces of information for constructing
 * a notification with message preview.
 */
public class NotificationData {
    private int unreadBeforeNotification;
    private LinkedList<LocalMessage> messages;
    private LinkedList<MessageReference> droppedMessages;

    /**
     * Maximum number of messages to keep for the inbox-style overview.
     * As of Jellybean, phone notifications show a maximum of 5 lines, while tablet
     * notifications show 7 lines. To make sure no lines are silently dropped,
     * we default to 5 lines.
     */
    private final static int MAX_MESSAGES = 5;

    /**
     * Constructs a new data instance.
     *
     * @param unread Number of unread messages prior to instance construction
     */
    public NotificationData(int unread) {
        unreadBeforeNotification = unread;
        droppedMessages = new LinkedList<>();
        messages = new LinkedList<>();
    }

    /**
     * Adds a new message to the list of pending messages for this notification.
     *
     * The implementation will take care of keeping a meaningful amount of
     * messages in {@link #messages}.
     *
     * @param m The new message to add.
     */
    public void addMessage(LocalMessage m) {
        while (messages.size() >= MAX_MESSAGES) {
            LocalMessage dropped = messages.removeLast();
            droppedMessages.addFirst(dropped.makeMessageReference());
        }
        messages.addFirst(m);
    }

    /**
     * Remove a certain message from the message list.
     *
     * @param context A context.
     * @param ref Reference of the message to remove
     * @return true if message was found and removed, false otherwise
     */
    public boolean removeMatchingMessage(Context context, MessageReference ref) {
        for (MessageReference dropped : droppedMessages) {
            if (dropped.equals(ref)) {
                droppedMessages.remove(dropped);
                return true;
            }
        }

        for (LocalMessage message : messages) {
            if (message.makeMessageReference().equals(ref)) {
                if (messages.remove(message) && !droppedMessages.isEmpty()) {
                    LocalMessage restoredMessage = droppedMessages.getFirst().restoreToLocalMessage(context);
                    if (restoredMessage != null) {
                        messages.addLast(restoredMessage);
                        droppedMessages.removeFirst();
                    }
                }
                return true;
            }
        }

        return false;
    }

    /**
     * Adds a list of references for all pending messages for the notification to the supplied
     * List.
     */
    public void supplyAllMessageRefs(List<MessageReference> refs) {
        for (LocalMessage m : messages) {
            refs.add(m.makeMessageReference());
        }
        refs.addAll(droppedMessages);
    }

    /**
     * Gets the total number of messages the user is to be notified of.
     *
     * @return Amount of new messages the notification notifies for
     */
    public int getNewMessageCount() {
        return messages.size() + droppedMessages.size();
    }


    /** Number of unread messages before constructing the notification */
    public int getUnreadBeforeNotification() {
        return unreadBeforeNotification;
    }

    /**
     * List of messages that should be used for the inbox-style overview.
     * It's sorted from newest to oldest message.
     * Don't modify this list directly, but use {@link #addMessage(LocalMessage)} and
     * {@link #removeMatchingMessage(Context, MessageReference)} instead.
     */
    public LinkedList<LocalMessage> getMessages() {
        return messages;
    }

    /**
     * List of references for messages that the user is still to be notified of,
     * but which don't fit into the inbox style anymore. It's sorted from newest
     * to oldest message.
     */
    public LinkedList<MessageReference> getDroppedMessages() {
        return droppedMessages;
    }
}