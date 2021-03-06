package com.fsck.k9.preferences;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentSender;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.preference.Preference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;

import com.fsck.k9.K9;

import org.openintents.openpgp.IOpenPgpService;
import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;

import de.fau.cs.mad.smile.android.R;

public class OpenPgpKeyPreferenceCompat extends Preference {
    private long mKeyId;
    private String mOpenPgpProvider;
    private OpenPgpServiceConnection mServiceConnection;
    private String mDefaultUserId;
    private Activity activity;

    public static final int REQUEST_CODE_KEY_PREFERENCE = 9999;

    private static final int NO_KEY = 0;

    public OpenPgpKeyPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public CharSequence getSummary() {
        if (mKeyId == NO_KEY) {
            return getContext().getString(R.string.openpgp_no_key_selected);
        }
        else {
            return getContext().getString(R.string.openpgp_key_selected);
        }
    }

    private void updateEnabled() {
        if (TextUtils.isEmpty(mOpenPgpProvider)) {
            setEnabled(false);
        } else {
            setEnabled(true);
        }
    }

    public void setOpenPgpProvider(String packageName) {
        mOpenPgpProvider = packageName;
        updateEnabled();
    }

    public void setDefaultUserId(String userId) {
        mDefaultUserId = userId;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    @Override
    protected void onClick() {
        // bind to service
        mServiceConnection = new OpenPgpServiceConnection(
                getContext().getApplicationContext(),
                mOpenPgpProvider,
                new OpenPgpServiceConnection.OnBound() {
                    @Override
                    public void onBound(IOpenPgpService service) {
                        getSignKeyId(new Intent());
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(OpenPgpApi.TAG, "exception on binding!", e);
                    }
                }
        );
        mServiceConnection.bindToService();
    }

    private void getSignKeyId(Intent data) {
        data.setAction(OpenPgpApi.ACTION_GET_SIGN_KEY_ID);
        data.putExtra(OpenPgpApi.EXTRA_USER_ID, mDefaultUserId);

        OpenPgpApi api = new OpenPgpApi(getContext(), mServiceConnection.getService());
        api.executeApiAsync(data, null, null, new MyCallback(activity, REQUEST_CODE_KEY_PREFERENCE));
    }

    public void save(long newValue) {
        // Give the client a chance to ignore this change if they deem it
        // invalid
        if (!callChangeListener(newValue)) {
            // They don't want the value to be set
            return;
        }

        setAndPersist(newValue);
    }

    /**
     * Public API
     */
    public void setValue(long keyId) {
        setAndPersist(keyId);
    }

    /**
     * Public API
     */
    public long getValue() {
        return mKeyId;
    }

    private void setAndPersist(long newValue) {
        mKeyId = newValue;

        // Save to persistent storage (this method will make sure this
        // preference should be persistent, along with other useful checks)
        persistLong(mKeyId);

        // Data has changed, notify so UI can be refreshed!
        notifyChanged();

        // also update summary
        setSummary(getSummary());
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        // This preference type's value type is Long, so we read the default
        // value from the attributes as an Integer.
        return (long) a.getInteger(index, NO_KEY);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        if (restoreValue) {
            // Restore state
            mKeyId = getPersistedLong(mKeyId);
        } else {
            // Set state
            long value = (Long) defaultValue;
            setAndPersist(value);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        /*
         * Suppose a client uses this preference type without persisting. We
         * must save the instance state so it is able to, for example, survive
         * orientation changes.
         */

        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        // Save the instance state
        final SavedState myState = new SavedState(superState);
        myState.keyId = mKeyId;
        myState.openPgpProvider = mOpenPgpProvider;
        myState.defaultUserId = mDefaultUserId;
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        // Restore the instance state
        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        mKeyId = myState.keyId;
        mOpenPgpProvider = myState.openPgpProvider;
        mDefaultUserId = myState.defaultUserId;
        notifyChanged();
    }

    /**
     * SavedState, a subclass of {@link BaseSavedState}, will store the state
     * of MyPreference, a subclass of Preference.
     * <p/>
     * It is important to always call through to super methods.
     */
    private static class SavedState extends BaseSavedState {

        long keyId;
        String openPgpProvider;
        String defaultUserId;
        public SavedState(Parcel source) {
            super(source);

            keyId = source.readInt();
            openPgpProvider = source.readString();
            defaultUserId = source.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);

            dest.writeLong(keyId);
            dest.writeString(openPgpProvider);
            dest.writeString(defaultUserId);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };

    }

    public boolean handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_KEY_PREFERENCE && resultCode == Activity.RESULT_OK) {
            long keyId = data.getLongExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, NO_KEY);
            save(keyId);
            return true;
        } else {
            return false;
        }
    }

    private static class MyCallback implements OpenPgpApi.IOpenPgpCallback {
        private final Activity activity;
        private final int requestCode;

        private MyCallback(Activity activity, int requestCode) {
            this.activity = activity;
            this.requestCode = requestCode;
        }

        @Override
        public void onReturn(Intent result) {
            switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                case OpenPgpApi.RESULT_CODE_SUCCESS: {
                    break;
                }
                case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED: {
                    PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                    try {
                        activity.startIntentSenderFromChild(
                                activity, pi.getIntentSender(),
                                requestCode, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        Log.e(OpenPgpApi.TAG, "SendIntentException", e);
                    }
                    break;
                }
                case OpenPgpApi.RESULT_CODE_ERROR: {
                    OpenPgpError error = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
                    Log.e(OpenPgpApi.TAG, "RESULT_CODE_ERROR: " + error.getMessage());

                    break;
                }
            }
        }
    }

}
