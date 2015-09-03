package com.fsck.k9.ui.crypto;


import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.os.AsyncTask;
import android.util.Log;

import com.fsck.k9.Account;
import com.fsck.k9.K9;
import com.fsck.k9.crypto.MessageDecryptVerifier;
import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.BodyPart;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Multipart;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.internet.MessageExtractor;
import com.fsck.k9.mail.internet.MimeBodyPart;
import com.fsck.k9.mail.internet.TextBody;
import com.fsck.k9.mailstore.CryptoError;
import com.fsck.k9.mailstore.CryptoErrorType;
import com.fsck.k9.mailstore.DecryptStreamParser;
import com.fsck.k9.mailstore.LocalMessage;
import com.fsck.k9.mailstore.MessageHelper;
import com.fsck.k9.mailstore.CryptoResultAnnotation;
import com.fsck.k9.mailstore.SignatureResult;
import com.fsck.k9.mailstore.SignatureStatus;

import org.openintents.openpgp.IOpenPgpService;
import org.openintents.openpgp.OpenPgpDecryptionResult;
import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpApi.IOpenPgpCallback;
import org.openintents.openpgp.util.OpenPgpServiceConnection;
import org.openintents.openpgp.util.OpenPgpServiceConnection.OnBound;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import de.fau.cs.mad.smile.android.R;
import de.fau.cs.mad.smime_api.ISMimeService;
import de.fau.cs.mad.smime_api.SMimeApi;
import de.fau.cs.mad.smime_api.SMimeServiceConnection;


public final class MessageCryptoHelper {
    private static final int REQUEST_CODE_CRYPTO = 1000;
    private static final int INVALID_RESULT_CODE = -1;
    private static final MimeBodyPart NO_REPLACEMENT_PART = null;

    private final Context context;
    private final Activity activity;
    private final WeakReference<MessageCryptoCallback> callback;
    private LocalMessage message;

    private final Deque<CryptoPart> partsToDecryptOrVerify = new ArrayDeque<CryptoPart>();
    private volatile OpenPgpApi openPgpApi;
    private volatile SMimeApi sMimeApi;
    private CryptoPart currentCryptoPart;
    private Intent currentCryptoResult;

    private MessageCryptoAnnotations messageAnnotations;
    private OpenPgpServiceConnection openPgpServiceConnection;
    private SMimeServiceConnection sMimeServiceConnection;
    private final String sMimeProvider;
    private final String openPgpProvider;

    public MessageCryptoHelper(final Activity activity, final MessageCryptoCallback callback,
                               final String sMimeProvider, final String openPgpProvider) {
        this.context = activity.getApplicationContext();
        this.activity = activity;
        this.callback = new WeakReference<>(callback);

        this.messageAnnotations = new MessageCryptoAnnotations();
        this.sMimeProvider = sMimeProvider;
        this.openPgpProvider = openPgpProvider;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        if (this.openPgpServiceConnection != null) {
            this.openPgpServiceConnection.unbindFromService();
        }

        if (this.sMimeServiceConnection != null) {
            this.sMimeServiceConnection.unbindFromService();
        }
    }

    public void decryptOrVerifyMessagePartsIfNecessary(final LocalMessage message) {
        this.message = message;

        if (openPgpProvider == null && sMimeProvider == null) {
            returnResultToFragment();
            return;
        }

        if(openPgpProvider != null) {
            List<Part> encryptedParts = MessageDecryptVerifier.findPgpEncryptedParts(message);
            processFoundParts(encryptedParts, CryptoPartType.ENCRYPTED_PGP, CryptoErrorType.ENCRYPTED_BUT_INCOMPLETE,
                    MessageHelper.createEmptyPart());

            List<Part> pgpSignedParts = MessageDecryptVerifier.findPgpSignedParts(message);
            processFoundParts(pgpSignedParts, CryptoPartType.SIGNED_PGP, CryptoErrorType.SIGNED_BUT_INCOMPLETE, NO_REPLACEMENT_PART);

            List<Part> inlineParts = MessageDecryptVerifier.findPgpInlineParts(message);
            addFoundInlinePgpParts(inlineParts);
        }

        if(sMimeProvider != null) {
            List<Part> smimeParts = MessageDecryptVerifier.findSmimeEncryptedParts(message);
            processFoundParts(smimeParts, CryptoPartType.ENCRYPTED_SMIME, CryptoErrorType.ENCRYPTED_BUT_INCOMPLETE,
                    MessageHelper.createEmptyPart());

            List<Part> smimeSignedParts = MessageDecryptVerifier.findSmimeSignedParts(message);
            processFoundParts(smimeSignedParts, CryptoPartType.SIGNED_SMIME, CryptoErrorType.SIGNED_BUT_INCOMPLETE, NO_REPLACEMENT_PART);
        }

        decryptOrVerifyNextPart();
    }

    private void processFoundParts(final List<Part> foundParts, final CryptoPartType cryptoPartType,
                                   final CryptoErrorType errorIfIncomplete, final MimeBodyPart replacementPart) {
        for (Part part : foundParts) {
            if (MessageHelper.isCompletePartAvailable(part)) {
                CryptoPart cryptoPart = new CryptoPart(cryptoPartType, part);
                partsToDecryptOrVerify.add(cryptoPart);
            } else {
                addErrorAnnotation(part, errorIfIncomplete, replacementPart);
            }
        }
    }

    private void addErrorAnnotation(final Part part, final CryptoErrorType error,
                                          final MimeBodyPart outputData) {
        CryptoResultAnnotation annotation = new CryptoResultAnnotation();
        annotation.setErrorType(error);
        annotation.setOutputData(outputData);
        messageAnnotations.put(part, annotation);
    }

    private void addFoundInlinePgpParts(final List<Part> foundParts) {
        for (Part part : foundParts) {
            final CryptoPart cryptoPart = new CryptoPart(CryptoPartType.INLINE_PGP, part);
            partsToDecryptOrVerify.add(cryptoPart);
        }
    }

    private void decryptOrVerifyNextPart() {
        if (partsToDecryptOrVerify.isEmpty()) {
            returnResultToFragment();
            return;
        }

        final CryptoPart cryptoPart = partsToDecryptOrVerify.peekFirst();
        startDecryptingOrVerifyingPart(cryptoPart);
    }

    private void startDecryptingOrVerifyingPart(final CryptoPart cryptoPart) {
        int latchCount = 0;

        if(openPgpProvider != null) {
            latchCount++;
        }

        if (sMimeProvider != null) {
            latchCount++;
        }

        final CountDownLatch latch = new CountDownLatch(latchCount);
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!isBoundToPgpProviderService()) {
                    connectToPgpProviderService(latch);
                }

                if(!isBoundToSMimeProviderService()) {
                    connectToSMimeProviderService(latch);
                }
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    latch.await();

                    if (isBoundToSMimeProviderService() && isBoundToPgpProviderService()) {
                        decryptOrVerifyPart(cryptoPart);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private boolean isBoundToPgpProviderService() {
        return openPgpApi != null || openPgpProvider == null;
    }

    private void connectToPgpProviderService(final CountDownLatch latch) {
        openPgpServiceConnection = new OpenPgpServiceConnection(context, openPgpProvider,
                new OnBound() {
                    @Override
                    public void onBound(IOpenPgpService service) {
                        openPgpApi = new OpenPgpApi(context, service);
                        latch.countDown();
                        //decryptOrVerifyNextPart();
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(K9.LOG_TAG, "Couldn't connect to OpenPgpService", e);
                        latch.countDown();
                    }
                });
        openPgpServiceConnection.bindToService();
    }

    private boolean isBoundToSMimeProviderService() {
        return sMimeApi != null || sMimeProvider == null;
    }

    private void connectToSMimeProviderService(final CountDownLatch latch) {
        sMimeServiceConnection = new SMimeServiceConnection(context, sMimeProvider,
                new SMimeServiceConnection.OnBound() {
                    @Override
                    public void onBound(ISMimeService service) {
                        sMimeApi = new SMimeApi(context, service);
                        latch.countDown();
                        //decryptOrVerifyNextPart();
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(K9.LOG_TAG, "Couldn't connect to SMimeService", e);
                        latch.countDown();
                    }
                });
        sMimeServiceConnection.bindToService();
    }

    private void decryptOrVerifyPart(final CryptoPart cryptoPart) {
        currentCryptoPart = cryptoPart;
        decryptVerify(new Intent());
    }

    private void decryptVerify(final Intent intent) {
        intent.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);

        try {
            CryptoPartType cryptoPartType = currentCryptoPart.type;
            switch (cryptoPartType) {
                case SIGNED_PGP: {
                    callAsyncDetachedVerify(intent);
                    return;
                }
                case SIGNED_SMIME: {
                    callAsyncVerifySmime();
                    return;
                }
                case ENCRYPTED_PGP: {
                    callAsyncDecrypt(intent);
                    return;
                }
                case ENCRYPTED_SMIME: {
                    // Note: the actual intent is set in callAsyncDecryptSmime
                    callAsyncDecryptSmime();
                    return;
                }
                case INLINE_PGP: {
                    callAsyncInlineOperation(intent);
                    return;
                }
            }

            throw new IllegalStateException("Unknown crypto part type: " + cryptoPartType);
        } catch (IOException e) {
            Log.e(K9.LOG_TAG, "IOException", e);
        } catch (MessagingException e) {
            Log.e(K9.LOG_TAG, "MessagingException", e);
        }
    }

    private void callAsyncInlineOperation(final Intent intent) throws IOException {
        PipedInputStream pipedInputStream = getPipedInputStream();
        final ByteArrayOutputStream decryptedOutputStream = new ByteArrayOutputStream();

        openPgpApi.executeApiAsync(intent, pipedInputStream, decryptedOutputStream, new IOpenPgpCallback() {
            @Override
            public void onReturn(Intent result) {
                currentCryptoResult = result;

                MimeBodyPart decryptedPart = null;
                try {
                    TextBody body = new TextBody(new String(decryptedOutputStream.toByteArray()));
                    decryptedPart = new MimeBodyPart(body, "text/plain");
                } catch (MessagingException e) {
                    Log.e(K9.LOG_TAG, "MessagingException", e);
                }

                onOpenPgpCryptoOperationReturned(decryptedPart);
            }
        });
    }

    private void callAsyncDecrypt(final Intent intent) throws IOException {
        final CountDownLatch latch = new CountDownLatch(1);

        PipedInputStream pipedInputStream = getPipedInputStream();
        final PipedOutputStream decryptedOutputStream = new PipedOutputStream();
        new DecryptedDataAsyncTask(decryptedOutputStream, latch).execute();

        openPgpApi.executeApiAsync(intent, pipedInputStream, decryptedOutputStream, new IOpenPgpCallback() {
            @Override
            public void onReturn(Intent result) {
                currentCryptoResult = result;
                latch.countDown();
            }
        });
    }

    private void callAsyncDecryptSmime() throws IOException {
        final CountDownLatch latch = new CountDownLatch(1);
        final String sender = this.message.getFrom()[0].getAddress();
        String recipient;

        try {
            recipient = this.message.getRecipients(Message.RecipientType.TO)[0].getAddress();
        } catch (MessagingException e) {
            recipient = null;
        }

        PipedInputStream pipedInputStream = getPipedInputStream();
        final PipedOutputStream decryptedOutputStream = new PipedOutputStream();
        new DecryptedSmimeDataAsyncTask(decryptedOutputStream, latch).execute();
        Intent data = SMimeApi.decryptAndVerifyMessage(sender, recipient);

        sMimeApi.executeApiAsync(data, pipedInputStream, decryptedOutputStream, new SMimeApi.ISMimeCallback() {
            @Override
            public void onReturn(Intent result) {
                currentCryptoResult = result;
                latch.countDown();
            }
        });
    }

    private void callAsyncVerifySmime() throws IOException, MessagingException {
        PipedInputStream pipedInputStream = getPipedInputStream();
        String[] fromHeader = currentCryptoPart.part.getHeader("From");
        String sender = new Address(fromHeader[0]).getAddress();
        Intent intent = SMimeApi.verifyMessage(sender);
        sMimeApi.executeApiAsync(intent, pipedInputStream, null, new SMimeApi.ISMimeCallback() {
            @Override
            public void onReturn(Intent result) {
                currentCryptoResult = result;
                onSmimeCryptoOperationReturned(null);
                Log.d(K9.LOG_TAG, "verify smime message");
            }
        });
    }

    private void callAsyncDetachedVerify(Intent intent) throws IOException, MessagingException {
        PipedInputStream pipedInputStream = getPipedInputStream();

        byte[] signatureData = MessageDecryptVerifier.getSignatureData(currentCryptoPart.part);
        intent.putExtra(OpenPgpApi.EXTRA_DETACHED_SIGNATURE, signatureData);

        openPgpApi.executeApiAsync(intent, pipedInputStream, null, new IOpenPgpCallback() {
            @Override
            public void onReturn(Intent result) {
                currentCryptoResult = result;
                onOpenPgpCryptoOperationReturned(null);
            }
        });
    }

    private PipedInputStream getPipedInputStream() throws IOException {
        PipedInputStream pipedInputStream = new PipedInputStream();
        PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream);
        new Thread(new CopyDataToPipeRunnable(pipedOutputStream, currentCryptoPart)).start();
        return pipedInputStream;
    }

    private final void onOpenPgpCryptoOperationReturned(final MimeBodyPart outputPart) {
        if (currentCryptoResult == null) {
            Log.e(K9.LOG_TAG, "Internal error: we should have a result here!");
            return;
        }

        try {
            handleOpenPgpCryptoOperationResult(outputPart);
        } finally {
            currentCryptoResult = null;
        }
    }

    private final void onSmimeCryptoOperationReturned(final MimeBodyPart outputPart) {
        if (currentCryptoResult == null) {
            Log.e(K9.LOG_TAG, "Internal error: we should have a result here!");
            return;
        }

        try {
            handleSmimeCryptoOperationResult(outputPart);
        } finally {
            currentCryptoResult = null;
        }
    }

    private void handleOpenPgpCryptoOperationResult(final MimeBodyPart outputPart) {
        int resultCode = currentCryptoResult.getIntExtra(OpenPgpApi.RESULT_CODE, INVALID_RESULT_CODE);
        if (K9.DEBUG) {
            Log.d(K9.LOG_TAG, "OpenPGP API decryptVerify result code: " + resultCode);
        }

        switch (resultCode) {
            case INVALID_RESULT_CODE: {
                Log.e(K9.LOG_TAG, "Internal error: no result code!");
                break;
            }
            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED: {
                handleUserInteractionRequest();
                break;
            }
            case OpenPgpApi.RESULT_CODE_ERROR: {
                handleOpenPgpCryptoOperationError();
                break;
            }
            case OpenPgpApi.RESULT_CODE_SUCCESS: {
                handleOpenPgpCryptoOperationSuccess(outputPart);
                break;
            }
        }
    }

    private void handleSmimeCryptoOperationResult(final MimeBodyPart outputPart) {
        int resultCode = currentCryptoResult.getIntExtra(SMimeApi.EXTRA_RESULT_CODE, INVALID_RESULT_CODE);
        switch (resultCode) {
            case INVALID_RESULT_CODE:
                Log.e(K9.LOG_TAG, "no result code!");
                break;
            case SMimeApi.RESULT_CODE_ERROR:
                break;
            case SMimeApi.RESULT_CODE_SUCCESS:
                handleSmimeCryptoOperationSuccess(outputPart);
                break;
        }
    }

    private void handleSmimeCryptoOperationSuccess(MimeBodyPart outputPart) {
        final CryptoResultAnnotation resultAnnotation = new CryptoResultAnnotation();
        resultAnnotation.setOutputData(outputPart);
        int resultType = currentCryptoResult.getIntExtra(SMimeApi.RESULT_TYPE, SMimeApi.RESULT_TYPE_UNENCRYPTED_UNSIGNED);

        if ((resultType & SMimeApi.RESULT_TYPE_ENCRYPTED) == SMimeApi.RESULT_TYPE_ENCRYPTED) {
            resultAnnotation.setEncrypted(true);
        } else {
            resultAnnotation.setEncrypted(false);
        }

        if ((resultType & SMimeApi.RESULT_TYPE_SIGNED) == SMimeApi.RESULT_TYPE_SIGNED) {
            int signatureStatus = currentCryptoResult.getIntExtra(SMimeApi.RESULT_SIGNATURE, SMimeApi.RESULT_SIGNATURE_UNSIGNED);
            SignatureResult signatureResult = new SignatureResult(SignatureStatus.SUCCESS, null, null);
            switch (signatureStatus) {
                case SMimeApi.RESULT_SIGNATURE_SIGNED:
                    signatureResult.setStatus(SignatureStatus.SUCCESS);
                    break;
                case SMimeApi.RESULT_SIGNATURE_SIGNED_UNCOFIRMED:
                    signatureResult.setStatus(SignatureStatus.SUCCESS_UNCERTIFIED);
                    break;
                default:
                    signatureResult.setStatus(SignatureStatus.INVALID_SIGNATURE);
                    break;
            }

            resultAnnotation.setSignatureResult(signatureResult);
        }

        onCryptoSuccess(resultAnnotation);
    }

    private void handleUserInteractionRequest() {
        PendingIntent pendingIntent = currentCryptoResult.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
        if (pendingIntent == null) {
            throw new AssertionError("Expecting PendingIntent on USER_INTERACTION_REQUIRED!");
        }

        try {
            activity.startIntentSenderForResult(pendingIntent.getIntentSender(), REQUEST_CODE_CRYPTO, null, 0, 0, 0);
        } catch (SendIntentException e) {
            Log.e(K9.LOG_TAG, "Internal error on starting pendingintent!", e);
        }
    }

    private void handleOpenPgpCryptoOperationError() {
        OpenPgpError error = currentCryptoResult.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
        if (K9.DEBUG) {
            Log.w(K9.LOG_TAG, "OpenPGP API error: " + error.getMessage());
        }

        CryptoError cryptoError = new CryptoError();
        cryptoError.setMessage(error.getMessage());

        switch (error.getErrorId()) {
            case OpenPgpError.CLIENT_SIDE_ERROR:
                cryptoError.setErrorType(CryptoErrorType.CLIENT_SIDE_ERROR);
                break;
            case OpenPgpError.GENERIC_ERROR:
                cryptoError.setErrorType(CryptoErrorType.GENERIC_ERROR);
                break;
            case OpenPgpError.INCOMPATIBLE_API_VERSIONS:
                cryptoError.setErrorType(CryptoErrorType.API_VERSION_MISMATCH);
                break;
            case OpenPgpError.NO_OR_WRONG_PASSPHRASE:
                cryptoError.setErrorType(CryptoErrorType.NO_OR_WRONG_PASSPHRASE);
                break;
            case OpenPgpError.NO_USER_IDS:
                cryptoError.setErrorType(CryptoErrorType.NO_USER_ID);
                break;
        }

        onCryptoFailed(cryptoError);
    }

    private void handleOpenPgpCryptoOperationSuccess(final MimeBodyPart outputPart) {
        final CryptoResultAnnotation resultAnnotation = new CryptoResultAnnotation();
        OpenPgpDecryptionResult decryptionResult =
                currentCryptoResult.getParcelableExtra(OpenPgpApi.RESULT_DECRYPTION);
        OpenPgpSignatureResult signatureResult =
                currentCryptoResult.getParcelableExtra(OpenPgpApi.RESULT_SIGNATURE);

        SignatureStatus signatureStatus = SignatureStatus.ERROR;

        switch (signatureResult.getResult()) {
            case OpenPgpSignatureResult.RESULT_INVALID_SIGNATURE:
                signatureStatus = SignatureStatus.INVALID_SIGNATURE;
                break;
            case OpenPgpSignatureResult.RESULT_VALID_CONFIRMED:
                signatureStatus = SignatureStatus.SUCCESS;
                break;
            case OpenPgpSignatureResult.RESULT_KEY_MISSING:
                signatureStatus = SignatureStatus.KEY_MISSING;
                break;
            case OpenPgpSignatureResult.RESULT_VALID_UNCONFIRMED:
                signatureStatus = SignatureStatus.SUCCESS_UNCERTIFIED;
                break;
            case OpenPgpSignatureResult.RESULT_INVALID_KEY_REVOKED:
                signatureStatus = SignatureStatus.KEY_REVOKED;
                break;
            case OpenPgpSignatureResult.RESULT_INVALID_KEY_EXPIRED:
                signatureStatus = SignatureStatus.KEY_EXPIRED;
                break;
            case OpenPgpSignatureResult.RESULT_NO_SIGNATURE:
                signatureStatus = SignatureStatus.UNSIGNED;
                break;
            case OpenPgpSignatureResult.RESULT_INVALID_INSECURE:
                signatureStatus = SignatureStatus.ERROR;
                break;
        }

        switch (decryptionResult.getResult()) {
            case OpenPgpDecryptionResult.RESULT_NOT_ENCRYPTED: {
                resultAnnotation.setEncrypted(false);
                break;
            }
            case OpenPgpDecryptionResult.RESULT_ENCRYPTED: {
                resultAnnotation.setEncrypted(true);
                break;
            }
            case OpenPgpDecryptionResult.RESULT_INSECURE: {
                resultAnnotation.setEncrypted(false);
                break;
            }
            default:
                throw new RuntimeException("OpenPgpDecryptionResult result not handled!");
        }

        SignatureResult signatureRes = new SignatureResult(signatureStatus, signatureResult.getPrimaryUserId(), signatureResult.getUserIds());
        PendingIntent pendingIntent = currentCryptoResult.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
        resultAnnotation.setPendingIntent(pendingIntent);
        resultAnnotation.setSignatureResult(signatureRes);
        resultAnnotation.setOutputData(outputPart);

        onCryptoSuccess(resultAnnotation);
    }

    public void handleCryptoResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE_CRYPTO) {
            return;
        }

        if (resultCode == Activity.RESULT_OK) {
            decryptOrVerifyNextPart();
        } else {
            onCryptoFailed(new CryptoError(CryptoErrorType.CLIENT_SIDE_ERROR, context.getString(R.string.openpgp_canceled_by_user)));
        }
    }

    private void onCryptoSuccess(CryptoResultAnnotation resultAnnotation) {
        addCryptoResultPartToMessage(resultAnnotation);
        onCryptoFinished();
    }

    private void addCryptoResultPartToMessage(CryptoResultAnnotation resultAnnotation) {
        Part part = currentCryptoPart.part;
        messageAnnotations.put(part, resultAnnotation);
    }

    private void onCryptoFailed(CryptoError error) {
        CryptoResultAnnotation errorPart = new CryptoResultAnnotation();
        errorPart.setError(error);
        addCryptoResultPartToMessage(errorPart);
        onCryptoFinished();
    }

    private void onCryptoFinished() {
        if (partsToDecryptOrVerify.size() > 0) {
            partsToDecryptOrVerify.removeFirst();
        }

        decryptOrVerifyNextPart();
    }

    private void returnResultToFragment() {
        MessageCryptoCallback realCallback = callback.get();
        if(realCallback != null) {
            realCallback.onCryptoOperationsFinished(messageAnnotations);
        }
    }

    private class DecryptedDataAsyncTask extends AsyncTask<Void, Void, MimeBodyPart> {
        private final PipedInputStream decryptedInputStream;
        private final CountDownLatch latch;

        public DecryptedDataAsyncTask(final PipedOutputStream decryptedOutputStream, final CountDownLatch latch) throws IOException {
            this.decryptedInputStream = new PipedInputStream(decryptedOutputStream);
            this.latch = latch;
        }

        @Override
        protected MimeBodyPart doInBackground(Void... params) {
            MimeBodyPart decryptedPart = null;
            try {
                // DecryptTempFileBody is created through this call
                decryptedPart = DecryptStreamParser.parse(context, decryptedInputStream);

                latch.await();
            } catch (InterruptedException e) {
                Log.w(K9.LOG_TAG, "we were interrupted while waiting for onReturn!", e);
            } catch (Exception e) {
                Log.e(K9.LOG_TAG, "Something went wrong while parsing the decrypted MIME part", e);
                //TODO: pass error to main thread and display error message to user
            }

            return decryptedPart;
        }

        @Override
        protected void onPostExecute(MimeBodyPart decryptedPart) {
            onOpenPgpCryptoOperationReturned(decryptedPart);
        }
    }

    private class DecryptedSmimeDataAsyncTask extends DecryptedDataAsyncTask {
        public DecryptedSmimeDataAsyncTask(final PipedOutputStream decryptedOutputStream, final CountDownLatch latch) throws IOException {
            super(decryptedOutputStream, latch);
        }

        @Override
        protected void onPostExecute(MimeBodyPart decryptedPart) {
            onSmimeCryptoOperationReturned(decryptedPart);
        }
    }

    private static class CopyDataToPipeRunnable implements Runnable {
        private final PipedOutputStream outputStream;
        private final CryptoPart cryptoPart;

        public CopyDataToPipeRunnable(final PipedOutputStream outputStream, final CryptoPart cryptoPart) {
            this.outputStream = outputStream;
            this.cryptoPart = cryptoPart;
        }

        @Override
        public void run() {
            try {
                Part part = cryptoPart.part;

                switch (cryptoPart.type) {
                    case INLINE_PGP:{
                        String text = MessageExtractor.getTextFromPart(part);
                        outputStream.write(text.getBytes());
                        break;
                    }
                    case ENCRYPTED_PGP: {
                        Multipart multipartEncryptedMultipart = (Multipart) part.getBody();
                        BodyPart encryptionPayloadPart = multipartEncryptedMultipart.getBodyPart(1);
                        Body encryptionPayloadBody = encryptionPayloadPart.getBody();
                        encryptionPayloadBody.writeTo(outputStream);
                        break;
                    }
                    case ENCRYPTED_SMIME: {
                        part.writeTo(outputStream);
                        break;
                    }
                    case SIGNED_PGP: {
                        Multipart multipartSignedMultipart = (Multipart) part.getBody();
                        BodyPart signatureBodyPart = multipartSignedMultipart.getBodyPart(0);
                        Log.d(K9.LOG_TAG, "signed data type: " + signatureBodyPart.getMimeType());
                        signatureBodyPart.writeTo(outputStream);
                        break;
                    }
                    case SIGNED_SMIME:
                        part.writeTo(outputStream);
                        break;
                    default:
                        Log.wtf(K9.LOG_TAG, "No suitable data to stream found!");
                        break;
                }
            } catch (IOException | MessagingException e) {
                Log.e(K9.LOG_TAG, "Exception while writing message to crypto provider", e);
            } finally {
                try {
                    if (outputStream != null) {
                        outputStream.close();
                    }
                } catch (IOException e) {
                    // don't care
                }
            }
        }
    }
}
