package com.fsck.k9.endtoend.pages;

import de.fau.cs.mad.smile.android.R;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
public class AccountTypePage extends AbstractPage {

    public IncomingServerSettingsPage clickImap() {
        onView(withId(R.id.imap)).perform(click());
        return new IncomingServerSettingsPage();
    }
}
