
package com.fsck.k9.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.fsck.k9.Account;
import com.fsck.k9.Identity;
import com.fsck.k9.Preferences;

import java.util.List;

import de.fau.cs.mad.smile.android.R;

public class ChooseIdentity extends K9ListActivity {
    Account mAccount;
    ArrayAdapter<String> adapter;

    public static final String EXTRA_ACCOUNT = "com.fsck.k9.ChooseIdentity_account";
    public static final String EXTRA_IDENTITY = "com.fsck.k9.ChooseIdentity_identity";

    protected List<Identity> identities = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.list_content_simple);

        final Intent intent = getIntent();
        final String accountUuid = intent.getStringExtra(EXTRA_ACCOUNT);
        mAccount = Preferences.getPreferences(this).getAccount(accountUuid);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);

        configureListView();
    }

    private void configureListView() {
        ListView listView = getListView();
        listView.setTextFilterEnabled(true);
        listView.setItemsCanFocus(false);
        listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
        setListAdapter(adapter);
    }

    protected void setupClickListeners() {
        ListView listView = getListView();
        listView.setOnItemClickListener(new MyOnItemClickListener());
    }

    @Override
    public void onResume() {
            super.onResume();
        refreshView();
    }

    protected void refreshView() {
        adapter.setNotifyOnChange(false);
        adapter.clear();

        identities = mAccount.getIdentities();
        for (Identity identity : identities) {
            String description = identity.getDescription();
            if (description == null || description.trim().isEmpty()) {
                description = getString(R.string.message_view_from_format, identity.getName(), identity.getEmail());
            }

            adapter.add(description);
        }

        adapter.notifyDataSetChanged();
    }

    private class MyOnItemClickListener implements AdapterView.OnItemClickListener {
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Identity identity = mAccount.getIdentity(position);
            String email = identity.getEmail();

            if (email != null && !email.trim().equals("")) {
                Intent intent = new Intent();
                intent.putExtra(EXTRA_IDENTITY, mAccount.getIdentity(position));
                setResult(RESULT_OK, intent);
                finish();
            } else {
                Toast.makeText(ChooseIdentity.this, getString(R.string.identity_has_no_email),
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
