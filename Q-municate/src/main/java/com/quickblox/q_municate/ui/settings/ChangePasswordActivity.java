package com.quickblox.q_municate.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import com.quickblox.module.users.model.QBUser;
import com.quickblox.q_municate.R;
import com.quickblox.q_municate.core.command.Command;
import com.quickblox.q_municate.model.AppSession;
import com.quickblox.q_municate.qb.commands.QBChangePasswordCommand;
import com.quickblox.q_municate.qb.commands.QBReloginCommand;
import com.quickblox.q_municate.service.QBServiceConsts;
import com.quickblox.q_municate.ui.base.BaseLogeableActivity;
import com.quickblox.q_municate.utils.Consts;
import com.quickblox.q_municate.utils.DialogUtils;
import com.quickblox.q_municate.utils.ValidationUtils;

public class ChangePasswordActivity extends BaseLogeableActivity {

    private EditText oldPasswordEditText;
    private EditText newPasswordEditText;
    private ValidationUtils validationUtils;
    private Resources resources;
    private QBUser user;
    private String oldPasswordText;
    private String newPasswordText;

    public static void start(Context context) {
        Intent intent = new Intent(context, ChangePasswordActivity.class);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        resources = getResources();
        canPerformLogout.set(false);

        initUI();

        addActions();

        user = AppSession.getSession().getUser();
    }

    public void changePasswordOnClickListener(View view) {
        oldPasswordText = oldPasswordEditText.getText().toString();
        newPasswordText = newPasswordEditText.getText().toString();
        if (validationUtils.isValidChangePasswordData(oldPasswordText, newPasswordText)) {
            updatePasswords(oldPasswordText, newPasswordText);
            showProgress();
            QBChangePasswordCommand.start(this, user);
        }
    }

    private void updatePasswords(String oldPasswordText, String newPasswordText) {
        user.setOldPassword(oldPasswordText);
        user.setPassword(newPasswordText);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                navigateToParent();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void initUI() {
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        oldPasswordEditText = _findViewById(R.id.old_password_edittext);
        newPasswordEditText = _findViewById(R.id.new_password_edittext);
        validationUtils = new ValidationUtils(this, new EditText[]{oldPasswordEditText, newPasswordEditText},
                new String[]{resources.getString(R.string.cpw_not_old_password_field_entered), resources
                        .getString(R.string.cpw_not_new_password_field_entered)}
        );
    }

    private void addActions() {
        addAction(QBServiceConsts.CHANGE_PASSWORD_SUCCESS_ACTION, new ChangePasswordSuccessAction());
        addAction(QBServiceConsts.CHANGE_PASSWORD_FAIL_ACTION, new ChangePasswordFailAction());
        addAction(QBServiceConsts.RE_LOGIN_IN_CHAT_SUCCESS_ACTION, new ReloginChatSuccessAction());
        addAction(QBServiceConsts.RE_LOGIN_IN_CHAT_FAIL_ACTION, failAction);
        updateBroadcastActionList();
    }

    private void clearFields() {
        oldPasswordEditText.setText(Consts.EMPTY_STRING);
        newPasswordEditText.setText(Consts.EMPTY_STRING);
    }

    private void clearFieldNewPassword() {
        newPasswordEditText.setText(Consts.EMPTY_STRING);
    }

    private void saveUserCredentials(QBUser user) {
        user.setPassword(newPasswordEditText.getText().toString());
        AppSession.getSession().updateUser(user);
    }

    private void relogin(){
        showProgress();
        QBReloginCommand.start(ChangePasswordActivity.this);
    }

    @Override
    protected void onFailAction(String action) {
        super.onFailAction(action);
        if(QBServiceConsts.RE_LOGIN_IN_CHAT_FAIL_ACTION.equals(action)){
            hideProgress();
            finish();
        }
    }

    private class ReloginChatSuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            hideProgress();
            finish();
        }
    }

    private class ChangePasswordSuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            QBUser user = (QBUser) bundle.getSerializable(QBServiceConsts.EXTRA_USER);
            saveUserCredentials(user);
            hideProgress();
            DialogUtils.showLong(ChangePasswordActivity.this, getString(R.string.dlg_password_changed));
            relogin();
        }
    }

    private class ChangePasswordFailAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            Exception exception = (Exception) bundle.getSerializable(QBServiceConsts.EXTRA_ERROR);
            hideProgress();
            DialogUtils.showLong(ChangePasswordActivity.this, exception.getMessage());
            updatePasswords(oldPasswordText, oldPasswordText);
            clearFieldNewPassword();
        }
    }
}