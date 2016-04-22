package com.devtrigger.grails.icu;

import com.ibm.icu.text.MessageFormat;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * ICU4j MessageFormat aware {@link org.springframework.context.support.MessageSourceSupport} drop-in
 * @see com.ibm.icu.text.MessageFormat
 */
public abstract class ICUMessageSourceSupport {

    private static final MessageFormat INVALID_MESSAGE_FORMAT = new MessageFormat("");

    /** Logger available to subclasses */
    protected final Log logger = LogFactory.getLog(getClass());

    private boolean alwaysUseMessageFormat = false;

    /**
     * Cache to hold already generated MessageFormats per message.
     * Used for passed-in default messages. MessageFormats for resolved
     * codes are cached on a specific basis in subclasses.
     */
    private final Map<String, Map<Locale, MessageFormat>> messageFormatsPerMessage =
            new HashMap<String, Map<Locale, MessageFormat>>();


    /**
     * Set whether to always apply the MessageFormat rules, parsing even
     * messages without arguments.
     * <p>Default is "false": Messages without arguments are by default
     * returned as-is, without parsing them through MessageFormat.
     * Set this to "true" to enforce MessageFormat for all messages,
     * expecting all message texts to be written with MessageFormat escaping.
     * <p>For example, MessageFormat expects a single quote to be escaped
     * as "''". If your message texts are all written with such escaping,
     * even when not defining argument placeholders, you need to set this
     * flag to "true". Else, only message texts with actual arguments
     * are supposed to be written with MessageFormat escaping.
     * @see java.text.MessageFormat
     */
    public void setAlwaysUseMessageFormat(boolean alwaysUseMessageFormat) {
        this.alwaysUseMessageFormat = alwaysUseMessageFormat;
    }

    /**
     * Return whether to always apply the MessageFormat rules, parsing even
     * messages without arguments.
     */
    protected boolean isAlwaysUseMessageFormat() {
        return this.alwaysUseMessageFormat;
    }


    /**
     * Render the given default message String. The default message is
     * passed in as specified by the caller and can be rendered into
     * a fully formatted default message shown to the user.
     * <p>The default implementation passes the String to {@code formatMessage},
     * resolving any argument placeholders found in them. Subclasses may override
     * this method to plug in custom processing of default messages.
     * @param defaultMessage the passed-in default message String
     * @param args arguments that will be filled in for params within
     * the message, or {@code null} if none.
     * @param locale the Locale used for formatting
     * @return the rendered default message (with resolved arguments)
     */
    protected String renderDefaultMessage(String defaultMessage, ICUMessageArguments args, Locale locale) {
        return formatMessage(defaultMessage, args, locale);
    }

    /**
     * Format the given message String, using cached MessageFormats.
     * By default invoked for passed-in default messages, to resolve
     * any argument placeholders found in them.
     * @param msg the message to format
     * @param args arguments that will be filled in for params within
     * the message, or {@code null} if none
     * @param locale the Locale used for formatting
     * @return the formatted message (with resolved arguments)
     */
    protected String formatMessage(String msg, ICUMessageArguments args, Locale locale) {
        if (msg == null || (!this.alwaysUseMessageFormat && args.isEmpty())) {
            return msg;
        }
        MessageFormat messageFormat = null;
        synchronized (this.messageFormatsPerMessage) {
            Map<Locale, MessageFormat> messageFormatsPerLocale = this.messageFormatsPerMessage.get(msg);
            if (messageFormatsPerLocale != null) {
                messageFormat = messageFormatsPerLocale.get(locale);
            }
            else {
                messageFormatsPerLocale = new HashMap<Locale, MessageFormat>();
                this.messageFormatsPerMessage.put(msg, messageFormatsPerLocale);
            }
            if (messageFormat == null) {
                try {
                    messageFormat = createMessageFormat(msg, locale);
                }
                catch (IllegalArgumentException ex) {
                    // invalid message format - probably not intended for formatting,
                    // rather using a message structure with no arguments involved
                    if (this.alwaysUseMessageFormat) {
                        throw ex;
                    }
                    // silently proceed with raw message if format not enforced
                    messageFormat = INVALID_MESSAGE_FORMAT;
                }
                messageFormatsPerLocale.put(locale, messageFormat);
            }
        }
        if (messageFormat == INVALID_MESSAGE_FORMAT) {
            return msg;
        }
        resolveArguments(args, locale);
        synchronized (messageFormat) {
            return args.formatWith(messageFormat);
        }
    }

    /**
     * Create a MessageFormat for the given message and Locale.
     * @param msg the message to create a MessageFormat for
     * @param locale the Locale to create a MessageFormat for
     * @return the MessageFormat instance
     */
    protected MessageFormat createMessageFormat(String msg, Locale locale) {
        return new MessageFormat((msg != null ? msg : ""), locale);
    }

    /**
     * Template method for resolving argument objects.
     * <p>The default implementation simply returns the given argument array as-is.
     * Can be overridden in subclasses in order to resolve special argument types.
     * @param args the original arguments
     * @param locale the Locale to resolve against
     * @return the resolved arguments
     */
    protected ICUMessageArguments resolveArguments(ICUMessageArguments args, Locale locale) {
        return args;
    }
}