package com.fsck.k9.ui.crypto;


import com.fsck.k9.mailstore.CryptoResultAnnotation;

public interface MessageCryptoCallback {
    void onCryptoOperationsFinished(MessageCryptoAnnotations<CryptoResultAnnotation> annotations);
}
