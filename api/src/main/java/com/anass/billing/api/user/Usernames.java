package com.anass.billing.api.user;

import java.util.Locale;


public final class Usernames {

    private Usernames() {
    }

    public static String normalize(String username) {
        // Locale.ROOT: the result must not depend on the server's language setting
        return username.trim().toLowerCase(Locale.ROOT);
    }
}