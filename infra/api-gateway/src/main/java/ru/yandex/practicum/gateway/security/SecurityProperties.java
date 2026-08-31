package ru.yandex.practicum.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {
    private List<UserAccount> users = new ArrayList<>();

    public List<UserAccount> getUsers() {
        return users;
    }

    public void setUsers(List<UserAccount> users) {
        this.users = users;
    }

    public static class UserAccount {
        private String username;
        private String password;
        private List<String> roles = new ArrayList<>();

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }
    }
}
