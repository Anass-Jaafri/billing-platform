package com.anass.billing.common.user;

import com.anass.billing.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class User extends BaseEntity {
    @Column(nullable = false, unique = true, length = 100)
    private String username;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    // STRING stores 'ADMIN'; ORDINAL would store 0 and silently change meaning if the enum is reordered
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

}
