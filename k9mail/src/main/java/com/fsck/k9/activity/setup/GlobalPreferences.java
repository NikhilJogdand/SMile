package com.fsck.k9.activity.setup;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.fsck.k9.Account;
import com.fsck.k9.K9;
import com.fsck.k9.K9.NotificationHideSubject;
import com.fsck.k9.K9.NotificationQuickDelete;
import com.fsck.k9.K9.SplitViewMode;
import com.fsck.k9.Preferences;
import com.fsck.k9.activity.ColorPickerDialog;
import com.fsck.k9.fragment.SmilePreferenceFragment;
import com.fsck.k9.helper.FileBrowserHelper;
import com.fsck.k9.helper.FileBrowserHelper.FileBrowserFailOverCallback;
import com.fsck.k9.helper.NotificationHelper;
import com.fsck.k9.preferences.AccountPreference;
import com.fsck.k9.preferences.CheckBoxListPreference;
import com.fsck.k9.preferences.TimePickerPreference;
import com.fsck.k9.service.MailService;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.fau.cs.mad.smile.android.R;


public class GlobalPreferences extends SmilePreferenceFragment {

    public interface GlobalPreferencesCallback {
        void onAccountClick(Account account);
    }

    /**
     * Immutable empty {@link CharSequence} array
     */
    private static final CharSequence[] EMPTY_CHAR_SEQUENCE_ARRAY = new CharSequence[0];

    /*
     * Keys of the preferences defined in res/xml/global_preferences.xml
     */
    private static final String PREFERENCE_LANGUAGE = "language";
    private static final String PREFERENCE_THEME = "theme";
    private static final String PREFERENCE_FONT_SIZE = "font_size";
    private static final String PREFERENCE_VOLUME_NAVIGATION = "volumeNavigation";
    private static final String PREFERENCE_START_INTEGRATED_INBOX = "start_integrated_inbox";
    private static final String PREFERENCE_NOTIFICATION_HIDE_SUBJECT = "notification_hide_subject";
    private static final String PREFERENCE_MESSAGELIST_PREVIEW_LINES = "messagelist_preview_lines";
    private static final String PREFERENCE_MESSAGELIST_SHOW_CORRESPONDENT_NAMES = "messagelist_show_correspondent_names";
    private static final String PREFERENCE_MESSAGELIST_CONTACT_NAME_COLOR = "messagelist_contact_name_color";
    private static final String PREFERENCE_MESSAGEVIEW_FIXEDWIDTH = "messageview_fixedwidth_font";

    private static final String PREFERENCE_MESSAGEVIEW_RETURN_TO_LIST = "messageview_return_to_list";
    private static final String PREFERENCE_QUIET_TIME_ENABLED = "quiet_time_enabled";
    private static final String PREFERENCE_DISABLE_NOTIFICATION_DURING_QUIET_TIME =
            "disable_notifications_during_quiet_time";
    private static final String PREFERENCE_QUIET_TIME_STARTS = "quiet_time_starts";
    private static final String PREFERENCE_QUIET_TIME_ENDS = "quiet_time_ends";
    private static final String PREFERENCE_NOTIF_QUICK_DELETE = "notification_quick_delete";
    private static final String PREFERENCE_LOCK_SCREEN_NOTIFICATION_VISIBILITY = "lock_screen_notification_visibility";

    private static final String PREFERENCE_DEBUG_LOGGING = "debug_logging";

    private static final String PREFERENCE_ATTACHMENT_DEF_PATH = "attachment_default_path";
    private static final String PREFERENCE_THREADED_VIEW = "threaded_view";
    private static final String PREFERENCE_SPLITVIEW_MODE = "splitview_mode";

    private static final int ACTIVITY_CHOOSE_FOLDER = 1;

    private ListPreference mLanguage;
    private ListPreference mTheme;
    private CheckBoxListPreference mVolumeNavigation;
    private SwitchPreference mStartIntegratedInbox;
    private ListPreference mNotificationHideSubject;
    private ListPreference mPreviewLines;
    private SwitchPreference mShowCorrespondentNames;
    private SwitchPreference mChangeContactNameColor;
    private SwitchPreference mFixedWidth;
    private SwitchPreference mReturnToList;
    private SwitchPreference mDebugLogging;

    private SwitchPreference mQuietTimeEnabled;
    private SwitchPreference mDisableNotificationDuringQuietTime;
    private com.fsck.k9.preferences.TimePickerPreference mQuietTimeStarts;
    private com.fsck.k9.preferences.TimePickerPreference mQuietTimeEnds;
    private ListPreference mNotificationQuickDelete;
    private ListPreference mLockScreenNotificationVisibility;
    private Preference mAttachmentPathPreference;

    private SwitchPreference mThreadedView;
    private ListPreference mSplitViewMode;
    private GlobalPreferencesCallback callback;
    private Context mContext;

    public static GlobalPreferences newInstance(GlobalPreferencesCallback callback) {
        GlobalPreferences preferences = new GlobalPreferences();
        preferences.setCallback(callback);
        return preferences;
    }

    private static String themeIdToName(K9.Theme theme) {
        switch (theme) {
            case DARK:
                return "dark";
            case USE_GLOBAL:
                return "global";
            default:
                return "light";
        }
    }

    private static K9.Theme themeNameToId(String theme) {
        if (TextUtils.equals(theme, "dark")) {
            return K9.Theme.DARK;
        } else if (TextUtils.equals(theme, "global")) {
            return K9.Theme.USE_GLOBAL;
        } else {
            return K9.Theme.LIGHT;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getActivity();
        addPreferencesFromResource(R.xml.global_preferences);
        PreferenceCategory category = (PreferenceCategory) findPreference("accounts");
        List<Account> accounts = Preferences.getPreferences(mContext).getAccounts();
        for(Account account : accounts) {
            category.addPreference(new AccountPreference(mContext, account, new OnAccountPreferenceClickListener()));
        }

        mLanguage = (ListPreference) findPreference(PREFERENCE_LANGUAGE);
        List<CharSequence> entryVector = new ArrayList<>(Arrays.asList(mLanguage.getEntries()));
        List<CharSequence> entryValueVector = new ArrayList<>(Arrays.asList(mLanguage.getEntryValues()));
        String supportedLanguages[] = getResources().getStringArray(R.array.supported_languages);
        Set<String> supportedLanguageSet = new HashSet<>(Arrays.asList(supportedLanguages));

        for (int i = entryVector.size() - 1; i > -1; --i) {
            if (!supportedLanguageSet.contains(entryValueVector.get(i))) {
                entryVector.remove(i);
                entryValueVector.remove(i);
            }
        }

        initListPreference(mLanguage, K9.getK9Language(),
                entryVector.toArray(EMPTY_CHAR_SEQUENCE_ARRAY),
                entryValueVector.toArray(EMPTY_CHAR_SEQUENCE_ARRAY));

        mTheme = setupListPreference(PREFERENCE_THEME, themeIdToName(K9.getK9Theme()));
        findPreference(PREFERENCE_FONT_SIZE).setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    public boolean onPreferenceClick(Preference preference) {
                        onFontSizeSettings();
                        return true;
                    }
                });

        mVolumeNavigation = (CheckBoxListPreference) findPreference(PREFERENCE_VOLUME_NAVIGATION);
        mVolumeNavigation.setItems(new CharSequence[]{getString(R.string.volume_navigation_message), getString(R.string.volume_navigation_list)});
        mVolumeNavigation.setCheckedItems(new boolean[]{K9.useVolumeKeysForNavigationEnabled(), K9.useVolumeKeysForListNavigationEnabled()});

        mStartIntegratedInbox = (SwitchPreference) findPreference(PREFERENCE_START_INTEGRATED_INBOX);
        mStartIntegratedInbox.setChecked(K9.startIntegratedInbox());

        mNotificationHideSubject = setupListPreference(PREFERENCE_NOTIFICATION_HIDE_SUBJECT,
                K9.getNotificationHideSubject().toString());

        mPreviewLines = setupListPreference(PREFERENCE_MESSAGELIST_PREVIEW_LINES,
                Integer.toString(K9.messageListPreviewLines()));

        mShowCorrespondentNames = (SwitchPreference) findPreference(PREFERENCE_MESSAGELIST_SHOW_CORRESPONDENT_NAMES);
        mShowCorrespondentNames.setChecked(K9.showCorrespondentNames());

        mChangeContactNameColor = (SwitchPreference) findPreference(PREFERENCE_MESSAGELIST_CONTACT_NAME_COLOR);
        mChangeContactNameColor.setChecked(K9.changeContactNameColor());

        mThreadedView = (SwitchPreference) findPreference(PREFERENCE_THREADED_VIEW);
        mThreadedView.setChecked(K9.isThreadedViewEnabled());

        if (K9.changeContactNameColor()) {
            mChangeContactNameColor.setSummary(R.string.global_settings_registered_name_color_changed);
        } else {
            mChangeContactNameColor.setSummary(R.string.global_settings_registered_name_color_default);
        }

        mChangeContactNameColor.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final Boolean checked = (Boolean) newValue;
                if (checked) {
                    onChooseContactNameColor();
                    mChangeContactNameColor.setSummary(R.string.global_settings_registered_name_color_changed);
                } else {
                    mChangeContactNameColor.setSummary(R.string.global_settings_registered_name_color_default);
                }
                mChangeContactNameColor.setChecked(checked);
                return false;
            }
        });

        mFixedWidth = (SwitchPreference) findPreference(PREFERENCE_MESSAGEVIEW_FIXEDWIDTH);
        mFixedWidth.setChecked(K9.messageViewFixedWidthFont());

        mReturnToList = (SwitchPreference) findPreference(PREFERENCE_MESSAGEVIEW_RETURN_TO_LIST);
        mReturnToList.setChecked(K9.messageViewReturnToList());

        mQuietTimeEnabled = (SwitchPreference) findPreference(PREFERENCE_QUIET_TIME_ENABLED);
        mQuietTimeEnabled.setChecked(K9.getQuietTimeEnabled());

        mDisableNotificationDuringQuietTime = (SwitchPreference) findPreference(
                PREFERENCE_DISABLE_NOTIFICATION_DURING_QUIET_TIME);
        mDisableNotificationDuringQuietTime.setChecked(!K9.isNotificationDuringQuietTimeEnabled());
        mQuietTimeStarts = (TimePickerPreference) findPreference(PREFERENCE_QUIET_TIME_STARTS);
        mQuietTimeStarts.setDefaultValue(K9.getQuietTimeStarts());
        mQuietTimeStarts.setSummary(K9.getQuietTimeStarts());
        mQuietTimeStarts.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final String time = (String) newValue;
                mQuietTimeStarts.setSummary(time);
                return false;
            }
        });

        mQuietTimeEnds = (TimePickerPreference) findPreference(PREFERENCE_QUIET_TIME_ENDS);
        mQuietTimeEnds.setSummary(K9.getQuietTimeEnds());
        mQuietTimeEnds.setDefaultValue(K9.getQuietTimeEnds());
        mQuietTimeEnds.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final String time = (String) newValue;
                mQuietTimeEnds.setSummary(time);
                return false;
            }
        });

        mNotificationQuickDelete = setupListPreference(PREFERENCE_NOTIF_QUICK_DELETE,
                K9.getNotificationQuickDeleteBehaviour().toString());
        if (!NotificationHelper.platformSupportsExtendedNotifications()) {
            PreferenceScreen prefs = (PreferenceScreen) findPreference("notification_preferences");
            prefs.removePreference(mNotificationQuickDelete);
            mNotificationQuickDelete = null;
        }

        mLockScreenNotificationVisibility = setupListPreference(PREFERENCE_LOCK_SCREEN_NOTIFICATION_VISIBILITY,
                K9.getLockScreenNotificationVisibility().toString());
        if (!NotificationHelper.platformSupportsLockScreenNotifications()) {
            ((PreferenceScreen) findPreference("notification_preferences"))
                    .removePreference(mLockScreenNotificationVisibility);
            mLockScreenNotificationVisibility = null;
        }

        mDebugLogging = (SwitchPreference) findPreference(PREFERENCE_DEBUG_LOGGING);
        mDebugLogging.setChecked(K9.DEBUG);

        mAttachmentPathPreference = findPreference(PREFERENCE_ATTACHMENT_DEF_PATH);
        mAttachmentPathPreference.setSummary(K9.getAttachmentDefaultPath());
        mAttachmentPathPreference
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    FileBrowserFailOverCallback callback = new FileBrowserFailOverCallback() {

                        @Override
                        public void onPathEntered(String path) {
                            mAttachmentPathPreference.setSummary(path);
                            K9.setAttachmentDefaultPath(path);
                        }

                        @Override
                        public void onCancel() {
                            // canceled, do nothing
                        }
                    };

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        FileBrowserHelper
                                .getInstance()
                                .showFileBrowserActivity(getActivity(),
                                        new File(K9.getAttachmentDefaultPath()),
                                        ACTIVITY_CHOOSE_FOLDER, callback);

                        return true;
                    }
                });

        mSplitViewMode = (ListPreference) findPreference(PREFERENCE_SPLITVIEW_MODE);
        initListPreference(mSplitViewMode, K9.getSplitViewMode().name(),
                mSplitViewMode.getEntries(), mSplitViewMode.getEntryValues());
    }

    @Override
    public void onPause() {
        saveSettings();
        super.onPause();
    }

    private void saveSettings() {
        SharedPreferences preferences = Preferences.getPreferences(mContext).getPreferences();

        K9.setK9Language(mLanguage.getValue());
        K9.setK9Theme(themeNameToId(mTheme.getValue()));
        K9.setUseFixedMessageViewTheme(true);

        K9.setAnimations(true);
        K9.setGesturesEnabled(true);
        K9.setUseVolumeKeysForNavigation(mVolumeNavigation.getCheckedItems()[0]);
        K9.setUseVolumeKeysForListNavigation(mVolumeNavigation.getCheckedItems()[1]);
        K9.setStartIntegratedInbox(mStartIntegratedInbox.isChecked());
        K9.setNotificationHideSubject(NotificationHideSubject.valueOf(mNotificationHideSubject.getValue()));

        K9.setConfirmDelete(false);
        K9.setConfirmDeleteStarred(true);
        if (NotificationHelper.platformSupportsExtendedNotifications()) {
            K9.setConfirmDeleteFromNotification(true);
        }
        K9.setConfirmSpam(false);
        K9.setConfirmDiscardMessage(true);

        K9.setMeasureAccounts(true);
        K9.setCountSearchMessages(true);
        K9.setHideSpecialAccounts(false);
        K9.setMessageListPreviewLines(Integer.parseInt(mPreviewLines.getValue()));
        K9.setMessageListCheckboxes(false);
        K9.setMessageListStars(true);
        K9.setShowCorrespondentNames(mShowCorrespondentNames.isChecked());
        K9.setMessageListSenderAboveSubject(false);
        K9.setShowContactName(true);
        K9.setShowContactPicture(true);
        K9.setColorizeMissingContactPictures(true);
        K9.setUseBackgroundAsUnreadIndicator(true);
        K9.setThreadedViewEnabled(mThreadedView.isChecked());
        K9.setChangeContactNameColor(mChangeContactNameColor.isChecked());
        K9.setMessageViewFixedWidthFont(mFixedWidth.isChecked());
        K9.setMessageViewReturnToList(mReturnToList.isChecked());
        K9.setMessageViewShowNext(true);
        K9.setAutofitWidth(true);
        K9.setQuietTimeEnabled(mQuietTimeEnabled.isChecked());

        K9.setNotificationDuringQuietTimeEnabled(!mDisableNotificationDuringQuietTime.isChecked());
        K9.setQuietTimeStarts(mQuietTimeStarts.getTime());
        K9.setQuietTimeEnds(mQuietTimeEnds.getTime());
        K9.setWrapFolderNames(true);

        if (mNotificationQuickDelete != null) {
            K9.setNotificationQuickDeleteBehaviour(
                    NotificationQuickDelete.valueOf(mNotificationQuickDelete.getValue()));
        }

        if (mLockScreenNotificationVisibility != null) {
            K9.setLockScreenNotificationVisibility(
                    K9.LockScreenNotificationVisibility.valueOf(mLockScreenNotificationVisibility.getValue()));
        }

        K9.setSplitViewMode(SplitViewMode.valueOf(mSplitViewMode.getValue()));
        K9.setAttachmentDefaultPath(mAttachmentPathPreference.getSummary().toString());
        boolean needsRefresh = K9.setBackgroundOps(K9.BACKGROUND_OPS.WHEN_CHECKED_AUTO_SYNC);

        if (!K9.DEBUG && mDebugLogging.isChecked()) {
            Toast.makeText(mContext, R.string.debug_logging_enabled, Toast.LENGTH_LONG).show();
        }

        K9.DEBUG = mDebugLogging.isChecked();
        K9.DEBUG_SENSITIVE = false;
        K9.setHideUserAgent(false);
        K9.setHideTimeZone(true);

        Editor editor = preferences.edit();
        K9.save(editor);
        editor.commit();

        if (needsRefresh) {
            MailService.actionReset(mContext, null);
        }
    }

    private void onFontSizeSettings() {
        FontSizeSettings.actionEditSettings(mContext);
    }

    private void onChooseContactNameColor() {
        ColorPickerDialog dialog = ColorPickerDialog.newInstance(
                new ColorPickerDialog.OnColorChangedListener() {
                    public void colorChanged(int color) {
                        K9.setContactNameColor(color);
                    }
                }, K9.getContactNameColor());

        dialog.show(getFragmentManager(), "colorPicker");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ACTIVITY_CHOOSE_FOLDER:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // obtain the filename
                    Uri fileUri = data.getData();
                    if (fileUri != null) {
                        String filePath = fileUri.getPath();
                        if (filePath != null) {
                            mAttachmentPathPreference.setSummary(filePath);
                            K9.setAttachmentDefaultPath(filePath);
                        }
                    }
                }
                break;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    public void setCallback(GlobalPreferencesCallback callback) {
        this.callback = callback;
    }

    private class OnAccountPreferenceClickListener implements OnPreferenceClickListener {
        @Override
        public boolean onPreferenceClick(Preference preference) {
            AccountPreference accountPreference = (AccountPreference)preference;
            Log.d(K9.LOG_TAG, "found acc pref: " + accountPreference.getAccount());
            callback.onAccountClick(accountPreference.getAccount());
            return false;
        }
    }
}
