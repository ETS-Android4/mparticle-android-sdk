package com.mparticle.internal;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;


import com.mparticle.internal.listeners.InternalListenerManager;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class BaseHandler extends Handler {
    private volatile boolean disabled;
    private volatile boolean handling;
    private ConcurrentHashMap<Message, Boolean> messageQueue = new ConcurrentHashMap();

    public Set<Message> getMessageQueue() {
        return messageQueue.keySet();
    }

    public BaseHandler() {}

    public BaseHandler(Looper looper) {
        super(looper);
    }

    public void disable(boolean disable) {
        this.disabled = disable;
        removeCallbacksAndMessages(null);
        while (handling) {}
    }



    public boolean isDisabled() {
        return disabled;
    }

    void await(CountDownLatch latch) {
        this.sendMessage(obtainMessage(-1, latch));
    }

    @Override
    public final void handleMessage(Message msg) {
        if (disabled) {
            Logger.error("Handler: " + getClass().getName() + " is destroyed! Message: \"" + msg.toString() + "\" will not be processed");
            return;
        }
        handling = true;
        try {
            if (msg != null) {
                messageQueue.remove(msg);
            }
            if (msg != null && msg.what == -1 && msg.obj instanceof CountDownLatch) {
                ((CountDownLatch)msg.obj).countDown();
            } else {
                if (InternalListenerManager.isEnabled()) {
                    InternalListenerManager.getListener().onThreadMessage(getClass().getName(), msg, true);
                }
                try {
                    handleMessageImpl(msg);
                } catch (OutOfMemoryError error) {
                    Logger.error("Out of memory");
                }
            }
        }
        finally {
            handling = false;
        }
    }

    @Override
    public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
        if (disabled) {
            return false;
        }
        if (InternalListenerManager.isEnabled()) {
            InternalListenerManager.getListener().onThreadMessage(getClass().getName(), msg, false);
        }
        if (msg != null) {
            messageQueue.put(msg, true);
        }
        return super.sendMessageAtTime(msg, uptimeMillis);
    }

    public void removeMessage(int what) {
        Set<Message> messages = messageQueue.keySet();
        for (Message message: messages) {
            if (message.what == what) {
                messageQueue.remove(message);
            }
        }
        super.removeMessages(what);
    }

    //Override this in order to handle messages
    public void handleMessageImpl(Message msg) {}
}
