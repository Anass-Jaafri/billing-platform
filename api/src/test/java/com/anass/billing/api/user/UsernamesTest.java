package com.anass.billing.api.user;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class UsernamesTest {

    @Test
    void normalize_trimsAndLowerCases() {
        assertThat(Usernames.normalize("  Admin ")).isEqualTo("admin");
        assertThat(Usernames.normalize("Mixed.Case_Name-1")).isEqualTo("mixed.case_name-1");
    }

    @Test
    void normalize_doesNotDependOnTheServersLanguage() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));   // Turkish: "I" lower-cases to a dotless i
            assertThat(Usernames.normalize("ADMIN-ID")).isEqualTo("admin-id");
        } finally {
            Locale.setDefault(original);
        }
    }
}