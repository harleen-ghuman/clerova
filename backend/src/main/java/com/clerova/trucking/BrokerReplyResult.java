package com.clerova.trucking;

/** What the AI made of a broker's email on a load. */
public class BrokerReplyResult {

    /** False for thanks, FYIs and anything that doesn't need an answer. */
    public boolean needsReply;
    public ReplyKind kind;
    /** One short line for the dispatcher, like "Asks for an ETA at delivery". */
    public String summary;
    /** The reply, without a greeting name guess or sign-off. */
    public String draft;
}
