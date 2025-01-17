package org.finos.waltz.test_common.helpers;

import org.finos.waltz.model.user.ImmutableUpdateRolesCommand;
import org.finos.waltz.model.user.ImmutableUserRegistrationRequest;
import org.finos.waltz.model.user.SystemRole;
import org.finos.waltz.service.role.RoleService;
import org.finos.waltz.service.user.UserRoleService;
import org.finos.waltz.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Stream;

import static org.finos.waltz.common.SetUtilities.map;

@Service
public class UserHelper {


    @Autowired
    private UserService userService;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private RoleService roleService;

    public Long createRole(String role) {
        return roleService
                .findAllRoles()
                .stream()
                .filter(r -> r.name().equals(role))
                .findFirst()
                .map(r -> r.id().get())
                .orElseGet(() -> roleService.create(
                        role,
                        role + " name",
                        role + " desc"));

    }

    public void createUser(String user) {
        userService.registerNewUser(
                ImmutableUserRegistrationRequest
                        .builder()
                        .userName(user)
                        .password(user + " password")
                        .build());
    }


    public void createUserWithRoles(String user, String... roles) {
        Stream.of(roles).forEach(this::createRole);
        createUser(user);
        userRoleService.updateRoles(
                user,
                user,
                ImmutableUpdateRolesCommand
                        .builder()
                        .addRoles(roles)
                        .build());
    }


    public void createUserWithSystemRoles(String user, Set<SystemRole> roles) {
        createUser(user);

        Set<String> roleKeys = map(roles, Enum::name);

        userRoleService.updateRoles(
                user,
                user,
                ImmutableUpdateRolesCommand
                        .builder()
                        .addAllRoles(roleKeys)
                        .build());
    }
}
