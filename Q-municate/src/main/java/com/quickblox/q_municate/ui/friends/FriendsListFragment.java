package com.quickblox.q_municate.ui.friends;

import android.content.DialogInterface;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.quickblox.q_municate.R;
import com.quickblox.q_municate.core.command.Command;
import com.quickblox.q_municate.db.DatabaseManager;
import com.quickblox.q_municate.db.tables.FriendTable;
import com.quickblox.q_municate.db.tables.UserTable;
import com.quickblox.q_municate.model.User;
import com.quickblox.q_municate.qb.commands.QBAcceptFriendCommand;
import com.quickblox.q_municate.qb.commands.QBAddFriendCommand;
import com.quickblox.q_municate.qb.commands.QBFindUsersCommand;
import com.quickblox.q_municate.qb.commands.QBRejectFriendCommand;
import com.quickblox.q_municate.qb.helpers.QBFriendListHelper;
import com.quickblox.q_municate.service.QBServiceConsts;
import com.quickblox.q_municate.ui.base.BaseFragment;
import com.quickblox.q_municate.ui.dialogs.AlertDialog;
import com.quickblox.q_municate.utils.Consts;
import com.quickblox.q_municate.utils.ErrorUtils;
import com.quickblox.q_municate.utils.FriendUtils;
import com.quickblox.q_municate.utils.KeyboardUtils;

import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class FriendsListFragment extends BaseFragment implements SearchView.OnQueryTextListener {

    private static final int SEARCH_DELAY = 1000;

    private State state;
    private String constraint;
    private ExpandableListView friendsListView;
    private TextView emptyListTextView;
    private FriendsListCursorAdapter friendsListAdapter;
    private SearchOnActionExpandListener searchOnActionExpandListener;
    private MenuItem searchItem;
    private SearchView searchView;
    private Toast errorToast;
    private ContentObserver userTableContentObserver;
    private ContentObserver friendTableContentObserver;
    private MatrixCursor headersCursor;
    private List<User> usersList;
    private MatrixCursor searchResultCursor;
    private FriendOperationAction friendOperationAction;
    private int relationStatusNoneId;
    private Resources resources;
    private Timer searchTimer;
    private int selectedPositionList;

    public static FriendsListFragment newInstance() {
        return new FriendsListFragment();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (State.GLOBAL_LIST.equals(state)) {
            initFriendsListForSearch();
        } else {
            initFriendsList();
        }
        checkVisibilityEmptyLabel();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        unregisterContentObservers();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.friend_list_menu, menu);
        searchOnActionExpandListener = new SearchOnActionExpandListener();
        searchItem = menu.findItem(R.id.action_search);
        searchItem.setOnActionExpandListener(searchOnActionExpandListener);
        searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(this);
    }

    private void registerContentObservers() {
        userTableContentObserver = new ContentObserver(new Handler()) {

            @Override
            public void onChange(boolean selfChange) {
                selectedPositionList = friendsListView.getFirstVisiblePosition();
                checkInitFriendsList();
            }
        };

        friendTableContentObserver = new ContentObserver(new Handler()) {

            @Override
            public void onChange(boolean selfChange) {
                checkInitFriendsList();
            }
        };

        baseActivity.getContentResolver().registerContentObserver(UserTable.CONTENT_URI, true,
                userTableContentObserver);
        baseActivity.getContentResolver().registerContentObserver(FriendTable.CONTENT_URI, true,
                friendTableContentObserver);
    }

    private void checkInitFriendsList() {
        if (State.GLOBAL_LIST.equals(state)) {
            initFriendsListForSearch();
            checkVisibilityEmptyLabel();
        } else {
            initFriendsList();
            checkVisibilityEmptyLabel();
        }
    }

    private void unregisterContentObservers() {
        baseActivity.getContentResolver().unregisterContentObserver(userTableContentObserver);
        baseActivity.getContentResolver().unregisterContentObserver(friendTableContentObserver);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        title = getString(R.string.nvd_title_contacts);
        state = State.FRIENDS_LIST;
    }

    @Override
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = layoutInflater.inflate(R.layout.fragment_friend_list, container, false);

        resources = getResources();
        friendOperationAction = new FriendOperationAction();
        relationStatusNoneId = DatabaseManager.getRelationStatusIdByName(baseActivity,
                QBFriendListHelper.RELATION_STATUS_NONE);
        usersList = Collections.emptyList();
        searchTimer = new Timer();

        initUI(rootView);
        initListeners();
        registerContentObservers();

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        addActions();
    }

    private void initListeners() {
        friendsListView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                KeyboardUtils.hideKeyboard(baseActivity);
                return false;
            }
        });

        friendsListView.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
            @Override
            public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
                // nothing do
                return true;
            }
        });

        friendsListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                    int childPosition, long id) {
                Cursor selectedItem = friendsListAdapter.getChild(groupPosition, childPosition);
                if (selectedItem.getCount() != Consts.ZERO_INT_VALUE && !selectedItem.isBeforeFirst()) {
                    User selectedUser = DatabaseManager.getUserFromCursor(selectedItem);
                    boolean isFriend = DatabaseManager.isFriendInBase(baseActivity, selectedUser.getUserId());
                    if (isFriend) {
                        startFriendDetailsActivity(selectedUser.getUserId());
                    }
                }
                return false;
            }
        });
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        KeyboardUtils.hideKeyboard(baseActivity);
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        constraint = newText;

        if (State.GLOBAL_LIST.equals(state)) {
            checkUsersListLoader();
        }

        return true;
    }

    private void addActions() {
        baseActivity.addAction(QBServiceConsts.ADD_FRIEND_SUCCESS_ACTION, new AddFriendSuccessAction());
        baseActivity.addAction(QBServiceConsts.ADD_FRIEND_FAIL_ACTION, failAction);

        baseActivity.addAction(QBServiceConsts.ACCEPT_FRIEND_SUCCESS_ACTION, new AcceptFriendSuccessAction());
        baseActivity.addAction(QBServiceConsts.ACCEPT_FRIEND_FAIL_ACTION, failAction);

        baseActivity.addAction(QBServiceConsts.REJECT_FRIEND_SUCCESS_ACTION, new RejectFriendSuccessAction());
        baseActivity.addAction(QBServiceConsts.REJECT_FRIEND_FAIL_ACTION, failAction);

        baseActivity.addAction(QBServiceConsts.LOAD_USERS_SUCCESS_ACTION, new UserSearchSuccessAction());
        baseActivity.addAction(QBServiceConsts.LOAD_USERS_FAIL_ACTION, new UserSearchFailAction());

        baseActivity.addAction(QBServiceConsts.LOAD_FRIENDS_SUCCESS_ACTION, new LoadFriendsSuccessAction());
        baseActivity.addAction(QBServiceConsts.LOAD_FRIENDS_FAIL_ACTION, failAction);

        baseActivity.updateBroadcastActionList();
    }

    private void initUI(View view) {
        friendsListView = (ExpandableListView) view.findViewById(R.id.friends_expandablelistview);
        emptyListTextView = (TextView) view.findViewById(R.id.empty_list_textview);
    }

    private void initFriendsList() {
        baseActivity.showActionBarProgress();
        createHeadersCursor();

        friendsListAdapter = new FriendsListCursorAdapter(baseActivity, headersCursor, null,
                friendOperationAction, false);

        updateFriendsList();
    }

    private void initFriendsListForSearch() {
        createHeadersForSearchCursor();

        friendsListAdapter = new FriendsListCursorAdapter(baseActivity, headersCursor, searchResultCursor,
                friendOperationAction, true);
        friendsListAdapter.setSearchCharacters(constraint);

        updateFriendsList();
    }

    private void updateFriendsList() {
        friendsListView.setAdapter(friendsListAdapter);
        friendsListView.setGroupIndicator(null);

        expandAllGroups(headersCursor);

        if (selectedPositionList != Consts.ZERO_INT_VALUE) {
            friendsListView.setSelection(selectedPositionList);
        }

        baseActivity.hideActionBarProgress();
    }

    private void startFriendDetailsActivity(int userId) {
        FriendDetailsActivity.start(baseActivity, userId);
    }

    private void expandAllGroups(Cursor headersCursor) {
        for (int i = 0; i < headersCursor.getCount(); i++) {
            friendsListView.expandGroup(i);
        }
    }

    private void createHeadersCursor() {
        headersCursor = new MatrixCursor(
                new String[]{FriendsListCursorAdapter.HEADER_COLUMN_ID, FriendsListCursorAdapter.HEADER_COLUMN_STATUS_NAME, FriendsListCursorAdapter.HEADER_COLUMN_HEADER_NAME});

        int countRequestsFriends = DatabaseManager.getAllFriendsCountByRelation(baseActivity,
                relationStatusNoneId);
        int countFriends = DatabaseManager.getAllFriendsCount(baseActivity);

        if (countRequestsFriends > Consts.ZERO_INT_VALUE) {
            headersCursor.addRow(new String[]{DatabaseManager.getRelationStatusIdByName(baseActivity,
                    QBFriendListHelper.RELATION_STATUS_NONE) + Consts.EMPTY_STRING, QBFriendListHelper.RELATION_STATUS_NONE, resources
                    .getString(R.string.frl_column_header_name_requests)});
        }

        if (countFriends > Consts.ZERO_INT_VALUE) {
            headersCursor.addRow(new String[]{DatabaseManager.getRelationStatusIdByName(baseActivity,
                    QBFriendListHelper.RELATION_STATUS_BOTH) + Consts.EMPTY_STRING, QBFriendListHelper.RELATION_STATUS_BOTH, resources
                    .getString(R.string.frl_column_header_name_contacts)});
        }

        if (state == State.GLOBAL_LIST) {
            headersCursor.addRow(
                    new String[]{QBFriendListHelper.VALUE_RELATION_STATUS_ALL_USERS + Consts.EMPTY_STRING, QBFriendListHelper.RELATION_STATUS_ALL_USERS, resources
                            .getString(R.string.frl_column_header_name_all_users)});
        }
    }

    private void createHeadersForSearchCursor() {
        headersCursor = new MatrixCursor(
                new String[]{FriendsListCursorAdapter.HEADER_COLUMN_ID, FriendsListCursorAdapter.HEADER_COLUMN_STATUS_NAME, FriendsListCursorAdapter.HEADER_COLUMN_HEADER_NAME});

        int countFriends = DatabaseManager.getFriendsByFullName(baseActivity, constraint).getCount();

        if (countFriends > Consts.ZERO_INT_VALUE) {
            headersCursor.addRow(new String[]{DatabaseManager.getRelationStatusIdByName(baseActivity,
                    QBFriendListHelper.RELATION_STATUS_BOTH) + Consts.EMPTY_STRING, QBFriendListHelper.RELATION_STATUS_BOTH, resources
                    .getString(R.string.frl_column_header_name_contacts)});
        }

        if (state == State.GLOBAL_LIST) {
            headersCursor.addRow(
                    new String[]{QBFriendListHelper.VALUE_RELATION_STATUS_ALL_USERS + Consts.EMPTY_STRING, QBFriendListHelper.RELATION_STATUS_ALL_USERS, resources
                            .getString(R.string.frl_column_header_name_all_users)});
        }
    }

    private void addToFriendList(final int userId) {
        baseActivity.showProgress();
        QBAddFriendCommand.start(baseActivity, userId);
        KeyboardUtils.hideKeyboard(baseActivity);
        searchView.clearFocus();
    }

    private void acceptUser(final int userId) {
        baseActivity.showProgress();
        QBAcceptFriendCommand.start(baseActivity, userId);
    }

    private void rejectUser(final int userId) {
        showRejectUserDialog(userId);
    }

    private void showRejectUserDialog(final int userId) {
        User user = DatabaseManager.getUserById(baseActivity, userId);
        AlertDialog alertDialog = AlertDialog.newInstance(getResources().getString(R.string.frl_dlg_reject_friend, user.getFullName()));
        alertDialog.setPositiveButton(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                baseActivity.showProgress();
                QBRejectFriendCommand.start(baseActivity, userId);
            }
        });
        alertDialog.setNegativeButton(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        alertDialog.show(getFragmentManager(), null);
    }

    private void checkUsersListLoader() {
        searchTimer.cancel();
        searchTimer = new Timer();
        searchTimer.schedule(new SearchTimerTask(), SEARCH_DELAY);
        baseActivity.showActionBarProgress();
    }

    private void startUsersListLoader() {
        QBFindUsersCommand.start(baseActivity, constraint);
    }

    private void showErrorToast(String error) {
        if (errorToast != null) {
            errorToast.cancel();
        }
        errorToast = ErrorUtils.getErrorToast(baseActivity, error);
        errorToast.show();
    }

    private void checkVisibilityEmptyLabel() {
        if (state == State.GLOBAL_LIST) {
            emptyListTextView.setVisibility(View.GONE);
        } else {
            int countRequestsFriends = DatabaseManager.getAllFriendsCountByRelation(baseActivity,
                    relationStatusNoneId);
            int countFriends = DatabaseManager.getAllFriendsCount(baseActivity);

            if ((countFriends + countRequestsFriends + usersList.size()) > Consts.ZERO_INT_VALUE) {
                emptyListTextView.setVisibility(View.GONE);
            } else {
                emptyListTextView.setVisibility(View.VISIBLE);
                friendsListView.setAdapter((ExpandableListAdapter) null);
            }
        }
    }

    private enum State {FRIENDS_LIST, GLOBAL_LIST}

    public interface FriendOperationListener {

        void onAddUserClicked(int userId);

        void onAcceptUserClicked(int userId);

        void onRejectUserClicked(int userId);
    }

    private class SearchTimerTask extends TimerTask {

        @Override
        public void run() {
            startUsersListLoader();
        }
    }

    private class SearchOnActionExpandListener implements MenuItem.OnActionExpandListener {

        @Override
        public boolean onMenuItemActionExpand(MenuItem item) {
            state = State.GLOBAL_LIST;
            return true;
        }

        @Override
        public boolean onMenuItemActionCollapse(MenuItem item) {
            state = State.FRIENDS_LIST;

            initFriendsList();

            friendsListAdapter.setSearchCharacters(null);
            usersList.clear();
            checkVisibilityEmptyLabel();

            return true;
        }
    }

    private class FriendOperationAction implements FriendOperationListener {

        @Override
        public void onAddUserClicked(int userId) {
            addToFriendList(userId);
        }

        @Override
        public void onAcceptUserClicked(int userId) {
            acceptUser(userId);
        }

        @Override
        public void onRejectUserClicked(int userId) {
            rejectUser(userId);
        }
    }

    private class AddFriendSuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            baseActivity.hideProgress();
            searchItem.collapseActionView();
        }
    }

    private class AcceptFriendSuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            baseActivity.hideProgress();
            initFriendsList();
        }
    }

    private class RejectFriendSuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            baseActivity.hideProgress();
            initFriendsList();
        }
    }

    private class UserSearchSuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            String constraint = bundle.getString(QBServiceConsts.EXTRA_CONSTRAINT);
            if (FriendsListFragment.this.constraint.equals(constraint)) {
                usersList = (List<User>) bundle.getSerializable(QBServiceConsts.EXTRA_USERS);
                searchResultCursor = FriendUtils.createSearchResultCursor(baseActivity, usersList);
                initFriendsListForSearch();
                checkVisibilityEmptyLabel();
                baseActivity.hideActionBarProgress();
            } else {
                onQueryTextChange(FriendsListFragment.this.constraint);
            }
        }
    }

    private class UserSearchFailAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            String notFoundError = getResources().getString(R.string.frl_not_found_users);
            showErrorToast(notFoundError);
            baseActivity.hideActionBarProgress();
        }
    }

    private class LoadFriendsSuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            initFriendsList();
            checkVisibilityEmptyLabel();
        }
    }
}