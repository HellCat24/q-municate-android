package com.quickblox.q_municate.qb.helpers;

import android.content.Context;

import com.quickblox.internal.core.exception.QBResponseException;
import com.quickblox.module.users.QBUsers;
import com.quickblox.module.users.model.QBUser;
import com.quickblox.q_municate.model.User;
import com.quickblox.q_municate.utils.FriendUtils;

import java.util.Collection;

public class QBRestHelper extends BaseHelper {

    public QBRestHelper(Context context) {
        super(context);
    }

    public User loadUser(int userId) throws QBResponseException {
        QBUser user = QBUsers.getUser(userId);
        return FriendUtils.createUser(user);
    }

    public Collection<User> loadUsers(Collection<Integer> usersIdsList) throws QBResponseException {
        Collection<QBUser> usersList = (Collection<QBUser>) QBUsers.getUsersByIDs(usersIdsList, null);
        return FriendUtils.createUsersList(usersList);
    }
}